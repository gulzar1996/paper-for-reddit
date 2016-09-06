package com.veyndan.redditclient.post;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jakewharton.rxbinding.support.v7.widget.RxRecyclerView;
import com.veyndan.redditclient.Config;
import com.veyndan.redditclient.MarginItemDecoration;
import com.veyndan.redditclient.R;
import com.veyndan.redditclient.UserFilter;
import com.veyndan.redditclient.api.reddit.Reddit;
import com.veyndan.redditclient.api.reddit.network.Credentials;
import com.veyndan.redditclient.post.model.Post;
import com.veyndan.redditclient.ui.recyclerview.SwipeItemTouchHelperCallback;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindDimen;
import butterknife.ButterKnife;
import rx.Observable;

public class PostsFragment extends Fragment implements PostMvpView {

    private static final String ARG_USER_FILTER = "user_filter";

    private final PostPresenter postPresenter = new PostPresenter();

    @BindDimen(R.dimen.card_view_margin) int cardViewMargin;

    private RecyclerView recyclerView;

    private final List<Post> posts = new ArrayList<>();

    private PostAdapter postAdapter;

    private LinearLayoutManager layoutManager;

    private boolean loadingPosts;
    private int pastVisiblesItems, visibleItemCount, totalItemCount;

    private Reddit reddit;

    public PostsFragment() {
        // Required empty public constructor
    }

    public static PostsFragment newInstance(final UserFilter userFilter) {
        final PostsFragment fragment = new PostsFragment();

        final Bundle args = new Bundle();
        args.putParcelable(ARG_USER_FILTER, userFilter);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        postPresenter.attachView(this);

        final Credentials credentials = new Credentials(Config.REDDIT_CLIENT_ID_RAWJAVA, Config.REDDIT_CLIENT_SECRET, Config.REDDIT_USER_AGENT, Config.REDDIT_USERNAME, Config.REDDIT_PASSWORD);
        reddit = new Reddit.Builder(credentials).build();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void setFilter(final PostsFilter filter) {
        clearPosts();
        posts.add(null);
        postPresenter.loadPosts(filter);
    }

    public void clearPosts() {
        final int postsSize = this.posts.size();
        this.posts.clear();
        if (postAdapter != null) {
            postAdapter.notifyItemRangeRemoved(0, postsSize);
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        recyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_posts, container, false);
        ButterKnife.bind(this, recyclerView);

        layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new MarginItemDecoration(cardViewMargin));
        postAdapter = new PostAdapter(getActivity(), posts, reddit);
        recyclerView.setAdapter(postAdapter);

        final ItemTouchHelper.Callback swipeCallback = new SwipeItemTouchHelperCallback();
        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        final Bundle args = getArguments();
        if (args != null) {
            setFilter(args.getParcelable(ARG_USER_FILTER));
        }

        return recyclerView;
    }

    @Override
    public void onDestroy() {
        postPresenter.detachView();
        super.onDestroy();
    }

    @Override
    public void showPosts(final List<Post> posts) {
        final int positionStart = this.posts.size();
        this.posts.addAll(this.posts.size() - 1, posts);
        postAdapter.notifyItemRangeInserted(positionStart, posts.size());
        loadingPosts = false;
    }

    @Override
    public void removeProgressBar() {
        final int progressBarIndex = posts.size() - 1;
        posts.remove(progressBarIndex);
        postAdapter.notifyItemRemoved(progressBarIndex);
    }

    @Override
    public Observable<Boolean> getNextPageTrigger() {
        return RxRecyclerView.scrollEvents(recyclerView)
                .filter(scrollEvent -> scrollEvent.dy() > 0) //check for scroll down
                .map(scrollEvent -> {
                    visibleItemCount = layoutManager.getChildCount();
                    totalItemCount = layoutManager.getItemCount();
                    pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();
                    return scrollEvent;
                })
                .filter(scrollEvent -> !loadingPosts && visibleItemCount + pastVisiblesItems >= totalItemCount)
                .map(scrollEvent -> {
                    loadingPosts = true;
                    return true;
                });
    }
}
