package com.study.tobytv.chapter06;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class DelegateSub01 implements Subscriber<Integer> {
    Subscriber sub;

    public DelegateSub01(Subscriber sub) {
        this.sub = sub;
    }

    @Override
    public void onSubscribe(Subscription s) { sub.onSubscribe(s); }

    @Override
    public void onNext(Integer i) { sub.onNext(i); }

    @Override
    public void onError(Throwable t) { sub.onError(t); }

    @Override
    public void onComplete() { sub.onComplete(); }
}