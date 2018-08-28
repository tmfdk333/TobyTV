package com.study.tobytv.chapter06;

import reactor.core.publisher.Flux;

public class Reactor03 {
    public static void main(String[] args) {
        Flux.<Integer>create(e->{
            e.next(1);
            e.next(2);
            e.next(3);
            e.complete();
        })
        .map(s->s*10)
        .subscribe(System.out::println);
    }
}
