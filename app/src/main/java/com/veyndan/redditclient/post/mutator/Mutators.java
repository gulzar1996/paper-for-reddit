package com.veyndan.redditclient.post.mutator;

import com.google.common.collect.ImmutableList;
import com.veyndan.redditclient.post.model.Post;

import java.util.List;

import rx.Observable;
import rx.functions.Func1;

public final class Mutators {

    /**
     * All available mutator factories.
     * <p>
     * Note that the order of the mutator factories is the order in which the post will be mutated.
     */
    private static final List<MutatorFactory> MUTATOR_FACTORIES = ImmutableList.of(
            ImgurMutatorFactory.create(),
            XkcdMutatorFactory.create()
    );

    /**
     * Mutate a list of posts by all the available mutator factories.
     */
    public Func1<List<Post>, Observable<List<Post>>> mutate() {
        return posts -> Observable.from(posts)
                .doOnNext(post -> {
                    for (final MutatorFactory mutatorFactory : MUTATOR_FACTORIES) {
                        if (mutatorFactory.applicable(post)) mutatorFactory.mutate(post);
                    }
                })
                .toList();
    }
}