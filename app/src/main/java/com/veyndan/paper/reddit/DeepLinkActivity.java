package com.veyndan.paper.reddit;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.airbnb.deeplinkdispatch.DeepLinkHandler;

import timber.log.Timber;

@DeepLinkHandler(AppDeepLinkModule.class)
public class DeepLinkActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Timber.d("DeepLinkActivity ~ ");

        if (!getIntent().getScheme().equals(Constants.REDDIT_REDIRECT_URI_SCHEME)) {
            final Uri standardizedUri = getIntent().getData().buildUpon()
                    .scheme("http")
                    .authority("reddit.com")
                    .build();

            getIntent().setData(standardizedUri);
        }

        final DeepLinkDelegate deepLinkDelegate = new DeepLinkDelegate(new AppDeepLinkModuleLoader());
        deepLinkDelegate.dispatchFrom(this);
        finish();
    }
}
