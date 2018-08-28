package com.study.tobytv.chapter06;

import reactor.core.publisher.Flux;

public class Reactor02 {
    public static void main(String[] args) {
        Flux.create(e->{
            e.next(1);
            e.next(2);
            e.next(3);
            e.complete();
        })
        .log()
        .subscribe(System.out::println);
    }
}
