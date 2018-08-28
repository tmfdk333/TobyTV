# 4회 : Reactive Streams2 - Operators

> * 예제코드 보는 순서
>   * 복습 : PubSub01 - PubSub02
>   * 진도1 : MapPub01 - MapPub02(DelegateSub01) - SumPub01 - ReducePub01 - MapPub03(DelegateSub02) - MapPub04(DelegateSub03) -
>   ReducePub02 - ReducePub03
>   * 진도2 :


## 복습 : Publisher, Subscriber
[`PubSub01`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/PubSub01.java)

> [reactive-streams](https://github.com/reactive-streams/reactive-streams-jvm#api-components)
>   * Publisher : Data stream을 계속 만들어내는 provider
>   * Subscriber : Publisher가 보낸 것을 실제로 받아서 사용
>     * Publisher.subscribe(Subscriber) : Publisher로부터 데이터를 받겠다고 신청
>       * Publisher의 subscribe 메소드를 호출하면서 Subscriber을 던짐.
>       * 내가 퍼블리셔가 제공하는 데이터를 받겠다.


### 1. main
```java
pub.subscribe(sub);
```
* Publisher에 Subscribe : 구독 신청

### 2. Publisher
```java
Publisher<Integer> pub = new Publisher<Integer>() {

	Iterable<Integer> iter = Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList());
	// Stream.iterate(seed, function) : 무한대로 데이터 스트림을 만들어 내는 메소드
	// limit을 걸지 않으면 무한대로 돌아감.
	// stream을 list로 가져오기 위해 Collector 사용
	@Override
	public void subscribe(Subscriber<? super Integer> sub) {
		sub.onSubscribe(new Subscription() {
			@Override
			public void request(long n) {
				try {
					iter.forEach(s -> sub.onNext(s));
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
```
* subscribe가 호출되면 Subscriber의 onSubscribe 메소드를 호출하면서 Subscription 오브젝트를 만들어 Subscriber에게 전달.
	* Publisher : 데이터 발생
	* Subscriber : 데이터를 받는 것
	* Subscription : 둘 사이에 구독이 일어나는 액션
* forEach문을 돌면서 Subscriber의 onNext에 다음 데이터를 전달.
* Publisher가 가진 데이터를 다 전달했다면 반드시 onComplete를 호출.
* 에러 발생 시 try-catch문을 사용해서  Subscriber의 onError에 전달해야 함
* cancel은 완료된 것이 아니지만 Publisher에게 더 이상 데이터를 전달하지 말라고 할 때 사용
	* 나중에 멀티쓰레드 할 때 볼 것

### 3. Subscriber
```java
Subscriber<Integer> sub = new Subscriber<Integer>() {
	@Override
	public void onSubscribe(Subscription s) {
		log.debug("onSubscribe:");
		s.request(Long.MAX_VALUE);
	}

	@Override
	public void onNext(Integer i) {
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
```

* 	메소드
	* 	onSubscribe : 구독이 시작됐음을 알려주는 단계
	* 	onNext : 데이터를 넘겨줌.
	* 	onError | onComplete :  둘 중 하나만 발생, 호출되는 순간 Subscription은 끝.
* onSubscribe가 호출되면 Subscription에 request를 호출해 데이터 요청
	* 가지고 있는 값을 모두 받으려면 Long.MAX_VALUE 사용

> **Output**  
> 18:45:02.699 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onSubscribe:  
> 18:45:02.713 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:1  
> 18:45:02.715 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:2  
> 18:45:02.715 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:3  
> 18:45:02.715 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:4  
> 18:45:02.715 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:5  
> 18:45:02.715 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:6  
> 18:45:02.715 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:7  
> 18:45:02.716 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:8  
> 18:45:02.716 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:9  
> 18:45:02.716 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onNext:10  
> 18:45:02.716 [main] DEBUG com.study.tobytv.chapter06.PubSub01 - onComplete()  

### 4. 리팩토링
[`PubSub02`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/PubSub02.java)

```java
public class PubSub {
    public static void main(String[] args) {
        Publisher<Integer> pub = iterPub(Stream.iterate(1, a -> a + 1).limit(10).collect(Collectors.toList()));
        pub.subscribe(logSub());
    }
	// 메소드로 추출
    private static Subscriber<Integer> logSub() {
        return new Subscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription s) {
                log.debug("onSubscribe:");
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Integer i) {
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
	// 메소드로 추출
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
```
* Subscriber : LogSub()

## 진도1 : Operator

### 0. Operator

* **기본 구조** : Publisher > Data > Subscriber
	* Publisher가 Subscription을 가지고 Subscriber에게 데이터를 전달
* **Operator 구조** : Publisher >  [Data1] > Op1 > [Data2] > Op2 > [Data3]  > Subscriber
	* Publisher가 넘겨주는 데이터를 Operator을 사용해서 데이터를 변경하는 구조
	* Subscriber는 Publisher에게 바로 연결되어있는게 아니기 때문에 Op2에 Subscribe를 요청

### 1. map
[`MapPub01`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/MapPub01.java)

> * map (d1 > f > d2)
	* 데이터가 하나 오면 function을 적용해서 가공한 뒤 d2를 만들어 내는 구조
#### 1-1. pub > [Data1] > mapPub > [Data2] > logSub

```java
public static void main(String[] args) {
	Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
	Publisher<Integer> mapPub = mapPub(pub, s->s*10);
	map2Pub.subscribe(logSub());
}
```
* 두번째 Publisher mapPub 정의. 첫 번째 Publisher인 pub를 전달. map이므로 function과 함께 전달.

```java
private static Publisher<Integer> mapPub(Publisher<Integer> pub, Function<Integer, Integer> f) {
	return new Publisher<Integer>() {
		@Override
		public void subscribe(Subscriber<? super Integer> sub) {
			pub.subscribe(new Subscriber<Integer>() {
				@Override
				public void onSubscribe(Subscription s) {
					sub.onSubscribe(s);
				}

				@Override
				public void onNext(Integer i) {
					sub.onNext(f.apply(i));
				}

				@Override
				public void onError(Throwable t) {
					sub.onError(t);
				}

				@Override
				public void onComplete() {
					sub.onComplete();
				}
			});
		}
	};
}
```
* mapPub는 새로운 Publisher를 만들어서 Return. 처음 Publisher과 똑같이 Subscribe를 작성.
	* Subscribe에는 Subscriber를 추가해서 전달.
	* pub에서 onNext, onError할 때 mapPub의 Subscriber가 호출된다.
* logSub는 pub이 아닌 mapPub에게 subscribe 요청
	* logSub가 보기에는 mapPub도 Publisher
	* mapPub이 logSub에게 받은 데이터를 가지고 다시 pub에게 subscribe 요청
* onNext에서 필요한 데이터를 조작하는 코드를 작성.
	* Function 타입으로 넘겨진 f를 데이터에 적용하여 데이터를 변환
	* 받은 데이터를 logSub에게 넘기기 전에 function에 apply 후 전달

```java
public static void main(String[] args) {
	Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
	Publisher<Integer> mapPub = mapPub(pub, s->s*10);
	Publisher<Integer> map2Pub = mapPub(pub, s->-s);
	map2Pub.subscribe(logSub());
}
```

* map을 하나 더 추가할 수도 있다.
* pub > [Data1] > mapPub > [Data2] > map2Pub > [Data3] > logSub 형태로 전달.
* Function을 넘겨서 어떻게 mapping할 것인가는 매번 다르게 지정할 수 있으므로 mapPub메소드는 다시 정의할 필요가 없다.

> **Output**  
> 21:22:16.231 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onSubscribe:  
> 21:22:16.235 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-1  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-2  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-3  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-4  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-5  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-6  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-7  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-8  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-9  
> 21:22:16.236 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onNext:-10  
> 21:22:16.237 [main] DEBUG com.study.tobytv.chapter06.MapPub01 - onComplete()  

### 1-2. 리팩토링
[`MapPub02`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/MapPub02.java) [`DelegateSub01`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/DelegateSub01.java)
* 이후에 Publisher와 Subscriber를 가지고 있는 Operator를 만들기 위해서 리팩토링.

```java
private static Publisher<Integer> mapPub(Publisher<Integer> pub, Function<Integer, Integer> f) {
	return new Publisher<Integer>() {
		@Override
		public void subscribe(Subscriber<? super Integer> sub) {
			pub.subscribe(new DelegateSub(sub) {
				@Override
				public void onNext(Integer i) {
					sub.onNext(f.apply(i));
				}
			});
		}
	};
}
```

* mapPub의 메소드들은 특별이 작업하는 게 없이 전달만 하는 것. Subscriber 생성 부분을 별도의 클래스로 추출.
* 위임하는 코드 이상으로 적용하고 싶은 메소드가 있으면 Override

```java
public class DelegateSub implements Subscriber<Integer> {
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
```
* 생성자 추가(Subscriber를 사용할 수 있도록)

### 2. sumPub : 동작 방식이 mapPub과는 다른 것
[`SumPub01`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/SumPub01.java)

```java
public static void main(String[] args) {
	Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
	Publisher<Integer> sumPub = sumPub(pub);
	sumPub.subscribe(logSub());
}
```

* 퍼블리셔를 넣어주면 합계를 계산해서 리턴
*  map과는 성격이 다름.
	*  map은 데이터가 10개가 날라오면 전달되는 만큼 subscriber에게 10개의 데이터를 넘김.
	*  sum은 데이터가 날라와도 logSub에게 다 전달하지 않고 최종 결과만 전달.
* 전달할 Function이 없음

```java
private static Publisher<Integer> sumPub(Publisher<Integer> pub) {
    return new Publisher<Integer>() {
        @Override
        public void subscribe(Subscriber<? super Integer> sub) {
            pub.subscribe(new DelegateSub(sub) {
                int sum = 0;

                @Override
                public void onNext(Integer i) {
                    sum += i;
                }

                @Override
                public void onComplete() {
                    sub.onNext(sum);
                    sub.onComplete();
                }
            });
        }
    };
}
```

* mapPub에서는 onNext사용하여 function에 데이터를 하나씩 전달하고 받은만큼 Subscriber에게 넘겼는데 sum 계산을 할 때는 onNext에서 전달하면 안됨
* sum 변수를 설정하고 onNext에서는 데이터가 Publisher로부터 넘어오면 더한다.
* onNext에서는 마지막 데이터라고 알 수 있는 방법이 없음.
* Publisher에서 데이터가 다 전달되었다고 알 수 있는 방법은 onComplete가 호출되는 시점.
* onComplete를 Override하여 데이터를 전달
	* onComplete에서 onNext를 호출해도 문제 없음

> **Output**  
> 21:22:55.500 [main] DEBUG com.study.tobytv.chapter06.SumPub01 - onSubscribe:  
> 21:22:55.503 [main] DEBUG com.study.tobytv.chapter06.SumPub01 - onNext:55  
> 21:22:55.504 [main] DEBUG com.study.tobytv.chapter06.SumPub01 - onComplete()  

### 3. reducePub : sumPub을 General하게
[`ReducePub01`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/ReducePub01.java)

* 중간 데이터 축적 방식(multiple, average, sum ...)을 내가 정하기
	*  초기 데이터와 함수가 주어짐

```java
public static void main(String[] args) {
    Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
    Publisher<Integer> reducePub = reducePub(pub, 0, (BiFunction<Integer, Integer, Integer>)(a,b)->a+b);
    reducePub.subscribe(logSub());
}
```

* BiFunction은 파라미터 두 개를 받아서 계산한 뒤 하나만 리턴
* a의 타입은 첫 번째 인자 타입과 일치해야 한다.

```java
private static Publisher<Integer> reducePub(Publisher<Integer> pub, int init, BiFunction<Integer, Integer, Integer> bf) {
    return new Publisher<Integer>() {
        @Override
        public void subscribe(Subscriber<? super Integer> sub) {
            pub.subscribe(new DelegateSub(sub) {
                int result = init;

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
```

* sumPub을 만들었을 때랑 동일한 결과(식이 덧셈이랑 똑같아서)
* 내부 구조는 sumPub과 거의 비슷하다고 봐도 된다.
* 계산할 때 최종 결과값.
* 초기값은 subscriber를 만드는 순간에 셋팅할 수 있도록 해야 한다.
* onNext에는 bifunction으로 apply한 값을 계속 result에 적용시켜 준다.
* sumPub과 마찬가지로 onComplete가 호출될 때 최종 결과값을 하위 Subscriber에게 전달.

### 4. Generic Class로 변경 : MapPub
[`MapPub03`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/MapPub03.java) [`DelegateSub02`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/DelegateSub02.java)

```java
public static void main(String[] args) {
    Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
    Publisher<Integer> mapPub = mapPub(pub, s->s*10);
    mapPub.subscribe(logSub());
}
```

*  현재는 Integer, Integer로만 넘기는 function이 허용
*  다양한 종류의 타입 변환이 function을 통해서 일어날 수 있어야 함

```java
private static <T> Publisher<T> mapPub(Publisher<T> pub, Function<T, T> f) {
    return new Publisher<T>() {
        @Override
        public void subscribe(Subscriber<? super T> sub) {
	        // DelegateSub(sub)하면 row type이 적용되니까 T 타입을 적용.
            pub.subscribe(new DelegateSub02<T>(sub) {
                @Override
                public void onNext(T i) {
                    sub.onNext(f.apply(i));
                }
            });
        }
    };
}
```

*  Big Decimal, String 등을 받고 싶음.
*  T 타입에 어떤게 들어가든 상관 없이 그 타입에 맞게
*  function은 T라는 타입을 받아서 T로 리턴하는 function은 다 적용 되므로 sub.onNext(f.apply(i));는 동일하게 적용 가능

```java
public class DelegateSub<T> implements Subscriber<T> {
    Subscriber sub;

    public DelegateSub(Subscriber<? super T> sub) {
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
```

* Deligate도 Generic 타입으로 변환

```java
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
```

* Integer 타입만 리턴할 필요가 없기 때문에 logSub도 Generic Type로 변경
* 타입 파라미터는 Generic Method에서는 return type 바로 앞에 준다.

### 5. Generic Type을 두 개로 : mapPub
[`MapPub04`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/MapPub04.java) [`DelegateSub03`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/DelegateSub03.java)

* 타입을 두개를 주고 싶음.
* 소스가 되는 Publisher의 타입이 있고 결과를 받아볼 Subscriber가 받는 데이터의 타입을 다르게 가져가고 싶을 때.
* 소스에 해당하는 건 T, Publisher를 감싸서 어떤 작업을 수행하는 새로 만드는 Publisher의 타입은 R
	* 두 개의 파라미터

```java
private static <T, R> Publisher<R> mapPub(Publisher<T> pub, Function<T, R> f) {
	// 각각이 어느 쪽에 해당되는 것인지 코드를 보면서 차근차근 하나씩 찾아나가면 됨.
    return new Publisher<R>() {
        @Override
        public void subscribe(Subscriber<? super R> sub) {
            pub.subscribe(new DelegateSub03<T, R>(sub) {
                @Override
                public void onNext(T i) {
                    sub.onNext(f.apply(i));
                }
            });
        }
    };
}
```

* Function은 T를 넣으면 R이 리턴되는 형태
	* mapPub이 만드는 새로운 Publisher Object도 R 타입
	* T 타입으로 들어와 R 타입으로 변형되는 Function이 적용되는 Publisher
* Subscriber는 받는 쪽, 받는쪽에서 원하는 타입은 R

```java
public class DelegateSub<T, R> implements Subscriber<T> {
    Subscriber sub;

    public DelegateSub03(Subscriber<? super R> sub) {
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
```

* Delegate도 데이터를 Subscriber가 받는 것과 넘어오는 데이터의 타입이 달라지게 된다.
	* DelegateSub를 만드는 쪽도 T, R 타입 두 가지를 가짐
* subscriber의 타입은 subscriber가 생성하는, 넘겨주는 쪽이니까 R 타입.
* onNext에서 넘어오는 타입은 T 타입

```java
public static void main(String[] args) {
    Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
    Publisher<String> mapPub = mapPub(pub, s->"["+s+"]");
//  Publisher<List> mapPub = mapPub(pub, s->Collections..singleTonList(s));
    mapPub.subscribe(logSub());
}
```

* Integer 값이 넘어오면 String으로 변환하여 리턴하는 코드
* Publisher<List> mapPub = mapPub(pub, s -> Collections.singleTonList(s));
	* 리스트 형식으로도 타입 변환이 가능

> **Output**  
> 21:23:42.344 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onSubscribe:  
> 21:23:42.366 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[1]  
> 21:23:42.367 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[2]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[3]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[4]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[5]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[6]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[7]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[8]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[9]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onNext:[10]  
> 21:23:42.368 [main] DEBUG com.study.tobytv.chapter06.MapPub04 - onComplete()  

### 6. Generic Class로 변경 : reducePub
[`ReducePub02`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/ReducePub02.java)

* 바로 타입을 주지 말고 타입이 바뀌면 어떻게 그룹핑되는가 생각.

```java
public static void main(String[] args) {
    Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
    Publisher<String> reducePub = reducePub(pub, "", (a, b) -> a + "-" + b);
    reducePub.subscribe(logSub());
}
```

* 문자열에 숫자를 더하면 새로운 문자가 됨. 결과값은 String, 입력은 Integer.
* 초기값을 문자열로.

```java
private static Publisher<String> reducePub(Publisher<Integer> pub, String init, BiFunction<String, Integer, String> bf) {
	// 결과도 String
    return new Publisher<String>() {
        @Override
        // Subscriber 쪽이니까 String
        public void subscribe(Subscriber<? super String> sub) {
	        // DelegateSub
            pub.subscribe(new DelegateSub<Integer, String>(sub) {
	            //result Type
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
```
* 초기값  String, Function은 String, Integer, String

> **Output**  
> 21:24:21.613 [main] DEBUG com.study.tobytv.chapter06.ReducePub02 - onSubscribe:  
> 21:24:21.620 [main] DEBUG com.study.tobytv.chapter06.ReducePub02 - onNext:-1-2-3-4-5-6-7-8-9-10  
> 21:24:21.622 [main] DEBUG com.study.tobytv.chapter06.ReducePub02 - onComplete()  

### 7. Generic Class로 변경2 : reducePub
[`ReducePub03`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/ReducePub03.java)

* 타입을 두가지로 나눠놨으니 제네릭으로 만드는 건 쉽다.

```java
private static <T, R> Publisher<R> reducePub(Publisher<T> pub, R init, BiFunction<R, T, R> bf) {
	// R
    return new Publisher<R>() {
        @Override
        // R
        public void subscribe(Subscriber<? super R> sub) {
	        // T, R
            pub.subscribe(new DelegateSub03<T, R>(sub) {
	            // R
                R result = init;

                @Override
                // T
                public void onNext(T i) {
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
```

* 내가 주입받는 타입은 T, 계산한 결과 타입은 R. T는 Integer, R은 String
* 한번에 변경하려면 헷갈리니까 구체적인 타입을 바꿔서 여러가지 타입을 정의해 보고 찾아서 바꾼다.

```java
public static void main(String[] args) {
    Publisher<Integer> pub = iterPub(Stream.iterate(1, a->a+1).limit(10).collect(Collectors.toList()));
    Publisher<StringBuilder> reducePub = reducePub(pub, new StringBuilder() , (a, b) -> a.append(b+","));
    reducePub.subscribe(logSub());
}
```

* StringBuilder로 변경, 초기값 new StringBuilder, StringBuilder가 결과값
* a는 StringBuilder, b는 Integer, a.append(b)
* append의 장점 : 다시 StringBuilder를 리턴. 간결하게 작성하기 편리함.  

> **Output**  
> 21:24:53.437 [main] DEBUG com.study.tobytv.chapter06.ReducePub03 - onSubscribe:  
> 21:24:53.452 [main] DEBUG com.study.tobytv.chapter06.ReducePub03 - onNext:1,2,3,4,5,6,7,8,9,10,  
> 21:24:53.453 [main] DEBUG com.study.tobytv.chapter06.ReducePub03 - onComplete()  

## 진도2 : Reactor
* Reactor는 Publisher 인터페이스를 구현한 유틸리티성 클래스.
* Flux를 제공. 일종의 Publisher라고 보면 된다.

### 1. Flux
[`Reactor01`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/Reactor01.java)

```java
public class Reactor {
    public static void main(String[] args) {
        Flux.create(e->{
            e.next(1);
            e.next(2);
            e.next(3);
            e.complete();
        })
        .subscribe(System.out::println);
        // .subscribe(e->System.out.println(s));
    }
}
```

* Subscriber의 여러 메소드들을 람다식으로 간단하게 넘기면 Subscriber 오브젝트들을 만들어주는 기능이 있음.
* Consumer는 파라미터는 들어오지만 return은 없는 functional 인터페이스 타입

> **Output**  
> 1  
> 2  
> 3  

### 2. log()
[`Reactor02`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/Reactor02.java)

```java
public class Reactor {
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
```
* 어떤 변환이 일어나는가. log가 걸린 곳의 위쪽과 아래쪽에서 데이터가 어떤식으로 흘러가는지 볼 수 있음.

> **Output**  
> 21:31:34.136 [main] INFO reactor.Flux.Create.1 - onSubscribe(FluxCreate.BufferAsyncSink) //호출  
> 21:31:34.139 [main] INFO reactor.Flux.Create.1 - request(unbounded) //long값 전달  
> 21:31:34.145 [main] INFO reactor.Flux.Create.1 - onNext(1)  
> 1  
> 21:31:34.145 [main] INFO reactor.Flux.Create.1 - onNext(2)  
> 2  
> 21:31:34.145 [main] INFO reactor.Flux.Create.1 - onNext(3)  
> 3  
> 21:31:34.145 [main] INFO reactor.Flux.Create.1 - onComplete()  

### 3. map()
[`Reactor03`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/Reactor03.java)

```java
public class Reactor {
    public static void main(String[] args) {
	    // Integer이라고 지정, 지정이 없으면 다양한 Object가 날라올수 있기 때문. 컴파일러가 체크해달라 함
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
```
> **Output**  
> 10  
> 20  
> 30  

### 4. map & log
[`Reactor04`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/Reactor04.java)

* Integer지정, 지정이 없으면 다양한 Object가 올 수 있기 때문.

```java
public class Reactor {
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
```

> **Output**  
> 21:33:21.422 [main] INFO reactor.Flux.Create.1 - onSubscribe(FluxCreate.BufferAsyncSink)  
> 21:33:21.424 [main] INFO reactor.Flux.Map.2 - onSubscribe(FluxMap.MapSubscriber)  
> 21:33:21.424 [main] INFO reactor.Flux.Map.2 - request(unbounded)  
> 21:33:21.424 [main] INFO reactor.Flux.Create.1 - request(unbounded)  
> 21:33:21.428 [main] INFO reactor.Flux.Create.1 - onNext(1) // Create1 - onNext(1)  
> 21:33:21.428 [main] INFO reactor.Flux.Map.2 - onNext(10) // Map2 - onNext(10)  
> 10  
> 21:33:21.428 [main] INFO reactor.Flux.Create.1 - onNext(2) // Create1 - onNext(2)  
> 21:33:21.429 [main] INFO reactor.Flux.Map.2 - onNext(20) // Map2 - onNext(20)  
> 20  
> 21:33:21.429 [main] INFO reactor.Flux.Create.1 - onNext(3) // Create1 - onNext(3)  
> 21:33:21.429 [main] INFO reactor.Flux.Map.2 - onNext(30) // Map2 - onNext(30)  
> 30  
> 21:33:21.430 [main] INFO reactor.Flux.Create.1 - onComplete()  
> 21:33:21.430 [main] INFO reactor.Flux.Map.2 - onComplete()  

### 5. reduce
[`Reactor05`](https://github.com/tmfdk333/TobyTV/blob/master/src/main/java/com/study/tobytv/chapter6/Reactor05.java)

```java
public class Reactor {
    public static void main(String[] args) {
        Flux.<Integer>create(e->{
            e.next(1);
            e.next(2);
            e.next(3);
            e.complete();
        })
        .log()
        .map(s->s*10)
        .reduce(0, (a, b)->a+b)
        .log()
        .subscribe(System.out::println);
    }
}
```

> **Output**  
> 21:34:38.241 [main] INFO reactor.Flux.Create.1 - onSubscribe(FluxCreate.BufferAsyncSink)  
> 21:34:38.243 [main] INFO reactor.Mono.ReduceSeed.2 - | onSubscribe([Fuseable] MonoReduceSeed.ReduceSeedSubscriber)  
> 21:34:38.243 [main] INFO reactor.Mono.ReduceSeed.2 - | request(unbounded)  
> 21:34:38.244 [main] INFO reactor.Flux.Create.1 - request(unbounded)  
> 21:34:38.247 [main] INFO reactor.Flux.Create.1 - onNext(1) // Create1 - onNext(1)  
> 21:34:38.247 [main] INFO reactor.Flux.Create.1 - onNext(2) // Create1 - onNext(2)  
> 21:34:38.247 [main] INFO reactor.Flux.Create.1 - onNext(3) // Create1 - onNext(3)  
> 21:34:38.248 [main] INFO reactor.Flux.Create.1 - onComplete() // Complete가 호출되고 나서 Reduce가 던져짐  
> 21:34:38.248 [main] INFO reactor.Mono.ReduceSeed.2 - | onNext(60) // ReduceSeed2 - onNext(60)  
> 60  
> 21:34:38.248 [main] INFO reactor.Mono.ReduceSeed.2 - | onComplete() // ReduceSeed2와 Create1에서 cancel가 던져지던데 나는 안그러네,,,,  
