package com.study.tobytv.chapter10_1;

import io.netty.channel.nio.NioEventLoopGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.function.Consumer;
import java.util.function.Function;

@SpringBootApplication
@EnableAsync
public class Tobytv10Application {
    @RestController
    public static class MyController {
        AsyncRestTemplate rt = new AsyncRestTemplate(new Netty4ClientHttpRequestFactory(new NioEventLoopGroup(1)));

        @Autowired MyService myService;

        static final String URL1 = "http://localhost:8081/service1?req={req}";
        static final String URL2 = "http://localhost:8081/service2?req={req}";

        @GetMapping("/rest")
        public DeferredResult<String> rest(int idx) {
            DeferredResult<String> dr = new DeferredResult<>();

            Completion
                    .from(rt.getForEntity(URL1, String.class, "h" + idx))
                    .andApply(s->rt.getForEntity(URL2, String.class, s.getBody()))
                    .andAccept(s->dr.setResult(s.getBody()));

//            ListenableFuture<ResponseEntity<String>> f1 = rt.getForEntity(URL1, String.class, "h" + idx));
//            f1.addCallback(s->{
//                ListenableFuture<ResponseEntity<String>> f2 = rt.getForEntity(URL2, String.class, s.getBody());
//                f2.addCallback(s2->{
//                    ListenableFuture<String> f3 = myService.work(s2.getBody());
//                    f3.addCallback(s3->{
//                        dr.setResult(s3);
//                    }, e->{
//                        dr.setErrorResult(e.getMessage());
//                    });
//                }, e->{
//                    dr.setErrorResult(e.getMessage());
//                });
//            }, e->{
//                dr.setErrorResult(e.getMessage());
//            });

            return dr;
        }
    }

    public static class Completion {
        Completion next;

        public Consumer<ResponseEntity<String>> con;
        public Completion(Consumer<ResponseEntity<String>> con) {
            this.con = con;
        }

        public Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn;
        public Completion(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {
            this.fn = fn;
        }

        public Completion() {}

        public void andAccept(Consumer<ResponseEntity<String>> con) {
            Completion c = new Completion(con);
            this.next = c;
        }

        public Completion andApply(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {
            Completion c = new Completion(fn);
            this.next = c;
            return c;
        }

        public static Completion from(ListenableFuture<ResponseEntity<String>> lf) {
            Completion c = new Completion();
            lf.addCallback(s->{
                c.complete(s);
            }, e->{
                c.error(e);
            });
            return c;
        }

        void error(Throwable e) {

        }

        void complete(ResponseEntity<String> s) {
            if (next != null) next.run(s);
        }

        void run(ResponseEntity<String> value) {
            if (con != null) con.accept(value);
            else if (fn != null) {
                ListenableFuture<ResponseEntity<String>> lf = fn.apply(value);
                lf.addCallback(s->complete(s), e->error(e));
            }
        }
    }

    @Service
    public static class MyService {
        @Async
        public ListenableFuture<String> work(String req) {
            return new AsyncResult<>(req + "/asyncwork");
        }
    }

    @Bean
    public ThreadPoolTaskExecutor myThreadPool() {
        ThreadPoolTaskExecutor te = new ThreadPoolTaskExecutor();
        te.setCorePoolSize(1);
        te.setMaxPoolSize(1);
        te.initialize();
        return te;
    }

    public static void main(String[] args) {
        SpringApplication.run(Tobytv10Application.class, args);
    }
}
