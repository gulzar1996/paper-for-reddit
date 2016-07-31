package com.veyndan.redditclient;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import butterknife.ButterKnife;
import rawjava.Reddit;
import rawjava.network.Credentials;
import rawjava.network.QueryBuilder;
import rawjava.network.Sort;
import rawjava.network.TimePeriod;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends BaseActivity {

    private PostsFragment postsFragment;

    private Reddit reddit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        Credentials credentials = Credentials.create(getResources().openRawResource(R.raw.credentials));
        reddit = new Reddit.Builder(credentials).build();

        postsFragment = (PostsFragment) getSupportFragmentManager().findFragmentById(R.id.main_fragment_posts);

        String subreddit;
        Intent intent = getIntent();
        if (intent.getExtras() != null) {
            subreddit = intent.getStringExtra("subreddit");
        } else {
            subreddit = "all";
        }

        reddit.subreddit(subreddit, Sort.HOT, postsFragment.getNextPageTrigger(), Schedulers.io(), AndroidSchedulers.mainThread())
                .subscribe(response -> {
                    postsFragment.addPosts(response.body().data.children);
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sort, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Sort sort;
        QueryBuilder query = new QueryBuilder();
        switch (item.getItemId()) {
            case R.id.action_sort_hot:
                sort = Sort.HOT;
                break;
            case R.id.action_sort_new:
                sort = Sort.NEW;
                break;
            case R.id.action_sort_rising:
                sort = Sort.RISING;
                break;
            case R.id.action_sort_controversial_hour:
                sort = Sort.CONTROVERSIAL;
                query.t(TimePeriod.HOUR);
                break;
            case R.id.action_sort_controversial_day:
                sort = Sort.CONTROVERSIAL;
                query.t(TimePeriod.DAY);
                break;
            case R.id.action_sort_controversial_week:
                sort = Sort.CONTROVERSIAL;
                query.t(TimePeriod.WEEK);
                break;
            case R.id.action_sort_controversial_month:
                sort = Sort.CONTROVERSIAL;
                query.t(TimePeriod.MONTH);
                break;
            case R.id.action_sort_controversial_year:
                sort = Sort.CONTROVERSIAL;
                query.t(TimePeriod.YEAR);
                break;
            case R.id.action_sort_controversial_all:
                sort = Sort.CONTROVERSIAL;
                query.t(TimePeriod.ALL);
                break;
            case R.id.action_sort_top_hour:
                sort = Sort.TOP;
                query.t(TimePeriod.HOUR);
                break;
            case R.id.action_sort_top_day:
                sort = Sort.TOP;
                query.t(TimePeriod.DAY);
                break;
            case R.id.action_sort_top_week:
                sort = Sort.TOP;
                query.t(TimePeriod.WEEK);
                break;
            case R.id.action_sort_top_month:
                sort = Sort.TOP;
                query.t(TimePeriod.MONTH);
                break;
            case R.id.action_sort_top_year:
                sort = Sort.TOP;
                query.t(TimePeriod.YEAR);
                break;
            case R.id.action_sort_top_all:
                sort = Sort.TOP;
                query.t(TimePeriod.ALL);
                break;
            default:
                return false;
        }

        postsFragment.clearPosts();

        reddit.subreddit("all", sort, query, postsFragment.getNextPageTrigger(), Schedulers.io(), AndroidSchedulers.mainThread())
                .subscribe(response -> {
                    postsFragment.addPosts(response.body().data.children);
                });

        return true;
    }
}
