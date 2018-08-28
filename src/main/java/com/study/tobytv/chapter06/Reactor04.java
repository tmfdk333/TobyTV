package com.study.tobytv.chapter06;

import reactor.core.publisher.Flux;

public class Reactor04 {
    public static void main(String[] args) {
        Flux.<Integer>create(e->{
            e.next(1);
            e.next(2);
            e.next(3);
            e.complete();
        })
        .log()
        .map(s->s*10)
        .log()
        .subscribe(System.out::println);
    }
}
