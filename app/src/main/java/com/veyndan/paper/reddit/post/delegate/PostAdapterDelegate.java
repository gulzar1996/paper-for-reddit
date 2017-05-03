package com.veyndan.paper.reddit.post.delegate;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import com.hannesdorfmann.adapterdelegates3.AdapterDelegate;
import com.jakewharton.rxbinding2.support.design.widget.RxSnackbar;
import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxbinding2.widget.RxCompoundButton;
import com.jakewharton.rxbinding2.widget.RxPopupMenu;
import com.veyndan.paper.reddit.MainActivity;
import com.veyndan.paper.reddit.R;
import com.veyndan.paper.reddit.api.reddit.Reddit;
import com.veyndan.paper.reddit.api.reddit.model.Listing;
import com.veyndan.paper.reddit.api.reddit.model.Thing;
import com.veyndan.paper.reddit.api.reddit.network.VoteDirection;
import com.veyndan.paper.reddit.databinding.PostItemBinding;
import com.veyndan.paper.reddit.post.Flair;
import com.veyndan.paper.reddit.post.PostAdapter;
import com.veyndan.paper.reddit.post.media.PostMediaAdapter;
import com.veyndan.paper.reddit.post.model.Post;
import com.veyndan.paper.reddit.ui.recyclerview.Swipeable;
import com.veyndan.paper.reddit.util.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;

public class PostAdapterDelegate extends AdapterDelegate<List<Node<Response<Thing<Listing>>>>> {

    private static final String CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome";

    private final PostAdapter adapter;
    private final Activity activity;
    private final Reddit reddit;

    private final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
    private final CustomTabsIntent customTabsIntent = builder.build();
    @Nullable private CustomTabsClient customTabsClient;

    public PostAdapterDelegate(final PostAdapter adapter, final Activity activity, final Reddit reddit) {
        this.adapter = adapter;
        this.activity = activity;
        this.reddit = reddit;

        CustomTabsClient.bindCustomTabsService(activity, CUSTOM_TAB_PACKAGE_NAME, new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(final ComponentName name, final CustomTabsClient client) {
                // customTabsClient is now valid.
                customTabsClient = client;
                customTabsClient.warmup(0);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // customTabsClient is no longer valid. This also invalidates sessions.
                customTabsClient = null;
            }
        });
    }

    @Override
    public boolean isForViewType(@NonNull final List<Node<Response<Thing<Listing>>>> nodes,
                                 final int position) {
        return nodes.get(position) instanceof Post;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final PostItemBinding binding = PostItemBinding.inflate(inflater, parent, false);
        return new PostViewHolder(binding, adapter, reddit);
    }

    @Override
    protected void onBindViewHolder(@NonNull final List<Node<Response<Thing<Listing>>>> nodes,
                                    final int position,
                                    @NonNull final RecyclerView.ViewHolder holder,
                                    @NonNull final List<Object> payloads) {
        final Context context = holder.itemView.getContext();
        final PostViewHolder postHolder = (PostViewHolder) holder;
        final Post post = (Post) nodes.get(position);

        if (payloads.isEmpty()) {
            bindTitle(post, postHolder);
            bindSubtitle(post, postHolder);
            bindFlairs(context, post, postHolder);
            bindMedia(post, postHolder, activity, customTabsClient, customTabsIntent);
            bindPoints(context, post, postHolder);
            bindActions(context, nodes, post, postHolder, reddit, adapter);
        } else if (payloads.get(0) == PostPayload.VOTE) {
            bindPoints(context, post, postHolder);
        }
    }

    private static void bindTitle(final Post post, final PostViewHolder holder) {
        holder.binding.postTitle.setTitle(post.linkTitle());
    }

    private static void bindSubtitle(final Post post, final PostViewHolder holder) {
        holder.binding.postSubtitle.setSubtitle(post.author(), post.getDisplayAge(), post.subreddit());
    }

    private static void bindFlairs(final Context context, final Post post, final PostViewHolder holder) {
        final Collection<Flair> flairs = new ArrayList<>();

        if (post.stickied()) {
            flairs.add(Flair.stickied(context));
        }

        if (post.locked()) {
            flairs.add(Flair.locked(context));
        }

        if (post.nsfw()) {
            flairs.add(Flair.nsfw(context));
        }

        if (post.hasLinkFlair()) {
            flairs.add(Flair.link(context, post.linkFlair()));
        }

        if (post.isGilded()) {
            flairs.add(Flair.gilded(context, post.gildedCount()));
        }

        holder.binding.postFlairs.setFlairs(flairs, post.subreddit());
    }

    private static void bindMedia(final Post post, final PostViewHolder holder,
                                  final Activity activity, final CustomTabsClient customTabsClient,
                                  final CustomTabsIntent customTabsIntent) {
        post.medias().value
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .toList()
                .subscribe(medias -> {
                    final PostMediaAdapter postMediaAdapter = new PostMediaAdapter(
                            activity, customTabsClient, customTabsIntent, post, medias);
                    holder.binding.postMediaView.setAdapter(postMediaAdapter);
                });
    }

    private static void bindPoints(final Context context, final Post post, final PostViewHolder holder) {
        final String points = post.getDisplayPoints(context);
        holder.binding.postScore.setText(points);
    }

    private static void bindActions(final Context context,
                                    final List<Node<Response<Thing<Listing>>>> nodes,
                                    final Post post, final PostViewHolder holder,
                                    final Reddit reddit, final PostAdapter adapter) {
        bindUpvoteAction(post, holder, reddit, adapter);
        bindDownvoteAction(post, holder, reddit, adapter);
        bindSaveAction(post, holder, reddit);
        bindCommentsAction(context, nodes, post, holder, adapter);
        bindPopupActions(context, post, holder);
    }

    private static void bindUpvoteAction(final Post post, final PostViewHolder holder,
                                         final Reddit reddit, final PostAdapter adapter) {
        final VoteDirection likes = post.likes().value;
        holder.binding.postUpvoteNew.setChecked(likes == VoteDirection.UPVOTE);
        RxCompoundButton.checkedChanges(holder.binding.postUpvoteNew)
                // checkedChanges emits the checked state on subscription. As the voted state of
                // the Reddit post is the same as the checked state of the button initially,
                // skipping the initial emission means no unnecessary network requests occur.
                .skip(1)
                .filter(isChecked -> !post.archived())
                .subscribe(isChecked -> {
                    post.likes().value = isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE;
                    reddit.vote(post.likes().value, post.fullname())
                            .subscribeOn(Schedulers.io())
                            .subscribe(response -> {});

                    post.points().value += isChecked ? 1 : -1;

                    adapter.notifyItemChanged(holder.getAdapterPosition(), PostPayload.VOTE);
                });
    }

    private static void bindDownvoteAction(final Post post, final PostViewHolder holder,
                                           final Reddit reddit, final PostAdapter adapter) {
        final VoteDirection likes = post.likes().value;
        holder.binding.postDownvoteNew.setChecked(likes == VoteDirection.DOWNVOTE);
        RxCompoundButton.checkedChanges(holder.binding.postDownvoteNew)
                // checkedChanges emits the checked state on subscription. As the voted state of
                // the Reddit post is the same as the checked state of the button initially,
                // skipping the initial emission means no unnecessary network requests occur.
                .skip(1)
                .filter(isChecked -> !post.archived())
                .subscribe(isChecked -> {
                    post.likes().value = isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE;
                    reddit.vote(post.likes().value, post.fullname())
                            .subscribeOn(Schedulers.io())
                            .subscribe(response -> {});

                    post.points().value += isChecked ? -1 : 1;

                    adapter.notifyItemChanged(holder.getAdapterPosition(), PostPayload.VOTE);
                });
    }

    private static void bindSaveAction(final Post post, final PostViewHolder holder,
                                       final Reddit reddit) {
        holder.binding.postSave.setChecked(post.saved().value);
        RxView.clicks(holder.binding.postSave)
                .subscribe(aVoid -> {
                    holder.binding.postSave.toggle();
                    final boolean isChecked = holder.binding.postSave.isChecked();

                    post.saved().value = isChecked;
                    if (isChecked) {
                        reddit.save("", post.fullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe(response -> {});
                    } else {
                        reddit.unsave(post.fullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe(response -> {});
                    }
                });
    }

    private static void bindCommentsAction(final Context context,
                                           final List<Node<Response<Thing<Listing>>>> nodes,
                                           final Post post, final PostViewHolder holder,
                                           final PostAdapter adapter) {
        RxView.clicks(holder.binding.postComments)
                .map(aVoid -> {
                    holder.binding.postComments.toggle();
                    return holder.binding.postComments.isChecked();
                })
                .subscribe(displayDescendants -> {
                    post.descendantsVisible().value = !displayDescendants;
                    if (post.comment()) {
                        if (displayDescendants) {
                            int i;
                            for (i = holder.getAdapterPosition() + 1; i < nodes.size() && nodes.get(i).depth() > post.depth(); i++)
                                ;

                            nodes.subList(holder.getAdapterPosition() + 1, i).clear();
                            adapter.notifyItemRangeRemoved(holder.getAdapterPosition() + 1, i - (holder.getAdapterPosition() + 1));

                            holder.binding.postCommentCount.setVisibility(View.VISIBLE);
                            holder.binding.postCommentCount.setText(String.valueOf(i - (holder.getAdapterPosition() + 1)));
                        } else {
                            post.preOrderTraverse(post.depth())
                                    .skip(1)
                                    .toList()
                                    .subscribe(children -> {
                                        nodes.addAll(holder.getAdapterPosition() + 1, children);
                                        adapter.notifyItemRangeInserted(holder.getAdapterPosition() + 1, children.size());
                                    });

                            holder.binding.postCommentCount.setVisibility(View.INVISIBLE);
                        }
                    } else {
                        final Intent commentsIntent = new Intent(context, MainActivity.class);
                        commentsIntent.putExtras(new Reddit.FilterBuilder()
                                .nodeDepth(0)
                                .commentsSubreddit(post.subreddit())
                                .commentsArticle(post.article())
                                .build());
                        context.startActivity(commentsIntent);
                    }
                });

        if (post.internalNode() && !post.descendantsVisible().value) {
            holder.binding.postCommentCount.setVisibility(View.VISIBLE);
            final String commentCount = post.getDisplayDescendants();
            holder.binding.postCommentCount.setText(commentCount);
        } else {
            holder.binding.postCommentCount.setVisibility(View.INVISIBLE);
        }
    }

    private static void bindPopupActions(final Context context, final Post post,
                                         final PostViewHolder holder) {
        final PopupMenu otherMenu = new PopupMenu(context, holder.binding.postOther);
        otherMenu.getMenuInflater().inflate(R.menu.menu_post_other, otherMenu.getMenu());

        RxView.clicks(holder.binding.postOther)
                .subscribe(aVoid -> otherMenu.show());

        RxPopupMenu.itemClicks(otherMenu)
                .subscribe(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.action_post_share:
                            bindShareAction(context, post);
                            break;
                        case R.id.action_post_browser:
                            bindBrowserAction(context, post);
                            break;
                        case R.id.action_post_report:
                            break;
                    }
                });
    }

    private static void bindShareAction(final Context context, final Post post) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, post.permalink());
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    private static void bindBrowserAction(final Context context, final Post post) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(post.linkUrl()));
        context.startActivity(intent);
    }

    private enum PostPayload {
        VOTE
    }

    static class PostViewHolder extends RecyclerView.ViewHolder implements Swipeable {

        private final PostItemBinding binding;
        private final PostAdapter adapter;
        private final Reddit reddit;

        PostViewHolder(final PostItemBinding binding, final PostAdapter adapter, final Reddit reddit) {
            super(binding.getRoot());

            this.binding = binding;
            this.adapter = adapter;
            this.reddit = reddit;
        }

        @Override
        public boolean swipeable() {
            final int position = getAdapterPosition();
            final Post post = (Post) adapter.getItems().get(position);
            return post.hideable();
        }

        @Override
        public void onSwipe() {
            final int position = getAdapterPosition();
            final Node<Response<Thing<Listing>>> node = adapter.getItems().get(position);
            final Post post = (Post) node;

            final View.OnClickListener undoClickListener = view -> {
                // If undo pressed, then don't follow through with request to hide
                // the post.
                adapter.getItems().add(position, node);
                adapter.notifyItemInserted(position);
            };

            final Snackbar snackbar = Snackbar.make(itemView, R.string.notify_post_hidden, Snackbar.LENGTH_LONG)
                    .setAction(R.string.notify_post_hidden_undo, undoClickListener);

            RxSnackbar.dismisses(snackbar)
                    // If undo pressed, don't hide post.
                    .filter(event -> event != Snackbar.Callback.DISMISS_EVENT_ACTION)
                    .firstElement()
                    .subscribe(event -> {
                        // Chance to undo post hiding has gone, so follow through with
                        // hiding network request.
                        reddit.hide(post.fullname())
                                .subscribeOn(Schedulers.io())
                                .subscribe(response -> {});
                    });

            snackbar.show();

            // Hide post from list, but make no network request yet. Outcome of the
            // user's interaction with the snackbar handling will determine this.
            adapter.getItems().remove(position);
            adapter.notifyItemRemoved(position);
        }
    }
}
