package com.veyndan.paper.reddit.util;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.reactivex.Observable;

public abstract class Node<T> {

    @IntRange(from = 0) private int depth;
    @Nullable @IntRange(from = 0) private Integer descendantCount;

    @IntRange(from = 0)
    public int depth() {
        return depth;
    }

    /**
     * Returns the degree of this node, or else {@code null} if the degree is unknown.
     */
    @Nullable
    @IntRange(from = 0)
    public abstract Integer degree();

    public boolean internalNode() {
        return descendantCount() > 0;
    }

    @NonNull
    public abstract Observable<Node<T>> children();

    /**
     * Returns the descendant count of this node, or else {@code null} if the descendant count is
     * unknown.
     */
    @Nullable
    @IntRange(from = 0)
    public Integer descendantCount() {
        return descendantCount;
    }

    public void descendantCount(@Nullable @IntRange(from = 0) final Integer descendantCount) {
        this.descendantCount = descendantCount;
    }

    @NonNull
    public Observable<Node<T>> preOrderTraverse(@IntRange(from = 0) final int depth) {
        return Observable.just(this)
                // Node specific calculations are done here. This is the soonest that these
                // calculations can be performed, as before this point the node data came from
                // some unknown place, e.g. a network request, disk etc.
                .doOnNext(node -> node.depth = depth)
                .doOnNext(node -> {
                    if (node.descendantCount == null) {
                        node.generateDescendantCount().subscribe(integer -> node.descendantCount = integer);
                    }
                })
                .concatMap(node -> Observable.just(node)
                        .concatWith(node.children()
                                .concatMap(childNode -> childNode.preOrderTraverse(depth + 1))));
    }

    private Observable<Integer> generateDescendantCount() {
        return children()
                .toList()
                .flatMapObservable(nodes -> Observable.fromIterable(nodes)
                        .flatMap(Node::generateDescendantCount)
                        .concatWith(Observable.just(nodes.size()))
                        .scan((sum, item) -> sum + item))
                .lastElement()
                .toObservable();
    }
}
