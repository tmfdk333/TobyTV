package com.study.tobytv.chapter06;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class ReducePub02 {
    public static Logger log = LoggerFactory.getLogger(ReducePub02.class);

    public static void main(String[] args) {
        Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
        Publisher<String> reducePub = reducePub(pub, "", (a, b) -> a + "-" + b);
        reducePub.subscribe(logSub());
    }

    private static Publisher<String> reducePub(Publisher<Integer> pub, String init, BiFunction<String, Integer, String> bf) {
        return new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> sub) {
                pub.subscribe(new DelegateSub03<Integer, String>(sub) {
                    String result = init;

                    @Override
                    public void onNext(Integer i) {
                        result = bf.apply(result, i);
                    }

                    @Override
                    public void onComplete() {
                        sub.onNext(result);
                        sub.onComplete();
                    }
                });
            }
        };
    }

    private static <T> Subscriber<T> logSub() {
        return new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                log.debug("onSubscribe:");
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T i) {
                log.debug("onNext:{}", i);
            }

            @Override
            public void onError(Throwable t) {
                log.debug("onError:{}", t);
            }

            @Override
            public void onComplete() {
                log.debug("onComplete()");
            }
        };
    }

    private static Publisher<Integer> iterPub(List<Integer> iter) {
        return new Publisher<Integer>() {
            @Override
            public void subscribe(Subscriber<? super Integer> sub) {
                sub.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        try {
                            iter.forEach(s->sub.onNext(s));
                            sub.onComplete();
                        }
                        catch(Throwable t) {
                            sub.onError(t);
                        }
                    }

                    @Override
                    public void cancel() {

                    }
                });
            }
        };
    }
}