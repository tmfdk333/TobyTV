package com.study.tobytv.chapter06;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class DelegateSub02<T> implements Subscriber<T> {
    Subscriber sub;

    public DelegateSub02(Subscriber<? super T> sub) {
        this.sub = sub;
    }

    @Override
    public void onSubscribe(Subscription s) { sub.onSubscribe(s); }

    @Override
    public void onNext(T i) { sub.onNext(i); }

    @Override
    public void onError(Throwable t) { sub.onError(t); }

    @Override
    public void onComplete() { sub.onComplete(); }
}