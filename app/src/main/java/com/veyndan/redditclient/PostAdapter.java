package com.veyndan.redditclient;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import rawjava.Reddit;
import rawjava.model.Link;
import rawjava.model.Source;
import rawjava.model.Thing;
import rawjava.network.VoteDirection;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private static final String TAG = "veyndan_PostAdapter";

    private final List<Thing<Link>> posts;
    private final Reddit reddit;
    private final int width;

    public PostAdapter(List<Thing<Link>> posts, Reddit reddit, int width) {
        this.posts = posts;
        this.reddit = reddit;
        this.width = width;
    }

    @Override
    public PostAdapter.PostViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final PostAdapter.PostViewHolder holder, int position) {
        Thing<Link> post = posts.get(position);
        final Context context = holder.itemView.getContext();

        holder.title.setText(post.data.title);

        CharSequence age = DateUtils.getRelativeTimeSpanString(
                TimeUnit.SECONDS.toMillis(post.data.createdUtc), System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_NO_MONTH_DAY);

        List<CharSequence> subtitleTokens = new ArrayList<>();
        subtitleTokens.add(post.data.author);
        subtitleTokens.add(age);
        subtitleTokens.add(post.data.subreddit);
        if (!post.data.isSelf && (post.data.preview == null || post.data.preview.images.isEmpty())) {
            String urlHost;
            try {
                urlHost = new URL(post.data.url).getHost();
            } catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage(), e);
                urlHost = post.data.url;
            }

            subtitleTokens.add(urlHost);
        }
        holder.subtitle.setText(TextUtils.join(" · ", subtitleTokens));

        if (!post.data.isSelf && post.data.preview != null && !post.data.preview.images.isEmpty()) {
            holder.urlContainer.setVisibility(View.VISIBLE);
            Source source = post.data.preview.images.get(0).source;
            Glide.with(context)
                    .load(source.url)
                    .listener(new RequestListener<String, GlideDrawable>() {
                        @Override
                        public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                            holder.imageProgress.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                            holder.imageProgress.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.image);
            holder.image.getLayoutParams().height = (int) ((float) width / source.width * source.height);
            holder.urlContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Thing<Link> post = posts.get(holder.getAdapterPosition());
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(post.data.url));
                    context.startActivity(intent);
                }
            });

            String urlHost;
            try {
                urlHost = new URL(post.data.url).getHost();
            } catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage(), e);
                urlHost = post.data.url;
            }

            holder.url.setText(urlHost);
        } else {
            holder.urlContainer.setVisibility(View.GONE);
        }

        final String points = context.getResources().getQuantityString(R.plurals.points, post.data.score, post.data.score);
        final String comments = context.getResources().getQuantityString(R.plurals.comments, post.data.numComments, post.data.numComments);
        holder.score.setText(context.getString(R.string.score, points, comments));

        Boolean likes = post.data.likes;

        holder.upvote.setChecked(likes != null && likes);
        holder.upvote.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Ensure that downvote and upvote aren't checked at the same time.
                if (isChecked) {
                    holder.downvote.setChecked(false);
                }

                Thing<Link> post = posts.get(holder.getAdapterPosition());
                post.data.likes = isChecked ? true : null;
                reddit.vote(isChecked ? VoteDirection.UPVOTE : VoteDirection.UNVOTE, post.kind + "_" + post.data.id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe();

                post.data.score += isChecked ? 1 : -1;

                final String points = context.getResources().getQuantityString(R.plurals.points, post.data.score, post.data.score);
                final String comments = context.getResources().getQuantityString(R.plurals.comments, post.data.numComments, post.data.numComments);
                holder.score.setText(context.getString(R.string.score, points, comments));
            }
        });

        holder.downvote.setChecked(likes != null && !likes);
        holder.downvote.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Ensure that downvote and upvote aren't checked at the same time.
                if (isChecked) {
                    holder.upvote.setChecked(false);
                }

                Thing<Link> post = posts.get(holder.getAdapterPosition());
                post.data.likes = isChecked ? false : null;
                reddit.vote(isChecked ? VoteDirection.DOWNVOTE : VoteDirection.UNVOTE, post.kind + "_" + post.data.id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe();

                post.data.score += isChecked ? -1 : 1;

                final String points = context.getResources().getQuantityString(R.plurals.points, post.data.score, post.data.score);
                final String comments = context.getResources().getQuantityString(R.plurals.comments, post.data.numComments, post.data.numComments);
                holder.score.setText(context.getString(R.string.score, points, comments));
            }
        });

        holder.save.setChecked(post.data.saved);
        holder.save.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Thing<Link> post = posts.get(holder.getAdapterPosition());
                post.data.saved = isChecked;
                if (isChecked) {
                    reddit.save("", post.kind + "_" + post.data.id)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe();
                } else {
                    reddit.unsave(post.kind + "_" + post.data.id)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.post_title) TextView title;
        @BindView(R.id.post_subtitle) TextView subtitle;
        @BindView(R.id.post_image) ImageView image;
        @BindView(R.id.post_image_progress) ProgressBar imageProgress;
        @BindView(R.id.post_url) TextView url;
        @BindView(R.id.post_url_container) LinearLayout urlContainer;
        @BindView(R.id.post_score) TextView score;
        @BindView(R.id.post_upvote) ToggleButton upvote;
        @BindView(R.id.post_downvote) ToggleButton downvote;
        @BindView(R.id.post_save) ToggleButton save;

        public PostViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
