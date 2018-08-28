# 10회 : AsyncRestTemplate의 콜백헬과 중복 작업 문제

```java
ListenableFuture<ResponseEntity<String>> f1 = rt.getForEntity(URL1, String.class, "h" + idx));
f1.addCallback(s->{
	ListenableFuture<ResponseEntity<String>> f2 = rt.getForEntity(URL2, String.class, s.getBody());
	f2.addCallback(s2->{
		ListenableFuture<String> f3 = myService.work(s2.getBody());
		f3.addCallback(s3->{
			dr.setResult(s3);
		}, e->{
			dr.setErrorResult(e.getMessage());
		});
	}, e->{
		dr.setErrorResult(e.getMessage());
	});
}, e->{
	dr.setErrorResult(e.getMessage());
});
```

* 콜백헬 구조를 클래스를 정의하여 해결할 것
* 앞에 어떤 작업에 의존하지 않고 바로 시작하는 첫 번째 단계와 비동기 작업이 완료되면 결과를 받아서 실행되는 작업

---

## 0.

```java
Completion
    .from(rt.getForEntity(URL1, String.class, "h" + idx))
```

```java
public static class Completion {
    public Completion() {}

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

    }
}
```

* ListenableFuture과 같은 비동기 작업의 결과를 가져와서 완료/에러 상황에서의 처리를 정의하기 위한 클래스를 작성
* Completion을 return하는 from 메소드를 정의
  * from에는 `rt.getForEntity(URL1, String.class, "h" + idx)`를 던져주면 그걸 받아서 Completion 오브젝트 형태로 만들어주는 코드를 작성할 것
  * getForEntity의 결과가 String인 최종 결과를 받고 싶은데, asyncRestTemplate는 getForEntity를 했을 때 String을 감싸서 ResponseEntity로 반환
  * Completion의 기본 생성자 작성
* callback이 실행되는 순간 처리할 수 있는 메소드를 추가
  * complete는 callback의 결과를 넘김
  * error는 Exception을 던짐
  * 이렇게 생성된 Completion 오브젝트를 가지고 있으면 Error, Complete 메소드에 콜백을 처리한 결과가 전달됨

---

## 1.

```java
Completion
        .from(rt.getForEntity(URL1, String.class, "h" + idx))
        .andAccept(s->dr.setResult(s.getBody()));
```

* 실제로 원하는건 Callback을 실행한 결과를 받아서 다른 비동기 작업으로 추가하는 것.
  * 한번에 다 넣으면 복잡하니까 첫번째와 최종 단계(Client한테 String으로 결과를 넘겨주는 코드)만 우선 추가

```java
public static class Completion {
    Completion next;

    public Completion() {}

    public static Completion from(ListenableFuture<ResponseEntity<String>> lf) {
        Completion c = new Completion();
        lf.addCallback(s->{
            c.complete(s);
        }, e->{
            c.error(e);
        });
        return c;
    }

    Consumer<ResponseEntity<String>> con;
    public Completion(Consumer<ResponseEntity<String>> con) {
        this.con = con;
    }

    public void andAccept(Consumer<ResponseEntity<String>> con) {
        Completion c = new Completion(con);
        this.next = c;
    }

    void error(Throwable e) {

    }

    void complete(ResponseEntity<String> s) {
         if (next != null) next.run(s);
    }

    void run(ResponseEntity<String> value) {
        if (con != null) con.accept(value);
    }
}
```

* andAccept는 from이 return하는 Completion 오브젝트를 받아 사용할 것이므로 andAccept가 받는 타입은 Consumer가 됨.
  * Consumer의 타입은 `.from(rt.getForEntity(URL1, String.class, "h" + idx))`의 결과로 return되는 타입과 같음(ResponseEntity<String>)
* andAccept는 뒤에 추가할 작업이 없으므로 return type는 void
* andAccept 또한 새로운 Completion을 생성
  * 각 단계마다 Completion을 생성하여 각 단계의 작업들을 계속 포장할 것임
  * 생성자를 작성해서 con(Consumer)를 새로 만들어지는 Completion안에 저장해야 함
* from에 의해서 만들어지는 Completion과 andAccept에서 만들어지는 Completion을 연결시켜주어야 함
  * Completion next라는 이름으로 레퍼런스 변수를 만들고 새로운 오브젝트가 만들어졌으면 `this.next`를 사용해서 새로운 Completion 오브젝트를 설정
  * from의 addCallback에서 `c.complete`가 실행되면 next가 설정되어있는지 확인하고 run 메소드를 실행
  * next는 두 번째 Completion이 되고 첫 번째 비동기 작업의 결과값이 전달됨
* run 메소드는 andAccept에 의해서 만들어진 Completion의 run이 실행되는 것
  * run이 실행됐을 때 자신에게 등록된 Consumer가 있으면 앞에서 넘어온 작업의 결과를 받아서 넘길 수 있도록 작성


* Output : service1 만 붙어있음. h 뒤에는 호출한 요청의 인덱스 번호. tobytv10Application의 Completion.from에서 h를 붙여서 api를 호출. 그때 호출된 api가 remoteService라는 api이고 그 값이 service1 이었으니 클라이언트 쪽으로 넘어온게 정상적으로 값이 처리됨
* 지금까지 하나하나를 Completion이라는 이름으로 오브젝트로 변환을 해서 체이닝 코드 작성.

---

## 2.
```java
Completion
	.from(rt.getForEntity(URL1, String.class, "h" + idx))
	.andApply(s->rt.getForEntity(URL2, String.class, s.getBody()))
	.andAccept(s->dr.setResult(s.getBody()));
```

* accept와 apply의 차이점
  * accept는 s를 넘겨서 수행만 하고 끝. 메소드가 void형의 return타입
  * apply는 받기도 받아야 하지만 return도 해야 함

```java
public static class Completion {
    Completion next;

    public Completion() {}

    public static Completion from(ListenableFuture<ResponseEntity<String>> lf) {
        Completion c = new Completion();
        lf.addCallback(s->{
            c.complete(s);
        }, e->{
            c.error(e);
        });
        return c;
    }

    public Consumer<ResponseEntity<String>> con;
    public Completion(Consumer<ResponseEntity<String>> con) {
        this.con = con;
    }

    public void andAccept(Consumer<ResponseEntity<String>> con) {
        Completion c = new Completion(con);
        this.next = c;
    }

    public Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn;
    public Completion(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {
        this.fn = fn;
    }

    public Completion andApply(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {
        Completion c = new Completion(fn);
        this.next = c;
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
```

* `s->rt.getForEntity(URL2, String.class, s.getBody())` 이 람다식은 Function타입. 입력값과 출력값도 있음.
  * Function 형태의 파라미터를 받는 andApply를 작성해야 함(Consumer와는 달리 두가지 타입을 주어야 한다.)
    * Input : 첫 번째 api 호출한 결과값 - ResponseEntity<String> (ListenableFuture의 결과이기 때문)
    * Output : asyncRestTemplate의 getForEntity를 실행하는 것이므로 결과값은 ListenableFuture<ResponseEntity<String>> (from의 ListenableFuture<ResponseEntity<String>>과 동일)
* 얘도 마찬가지로 한 단계의 비동기 작업을 할 때마다 Completion을 만들어 줄 것
* andApply에서 받은 Function을 이용해서 만든 Completion을 next로 지정
  * 생성자의 타입이 달라졌기 때문에 생성자 하나 더 추가

> **중간 복습**
> * 첫번째 `.from(rt.getForEntity(URL1, String.class, "h" + idx))` 비동기 작업이 완료되면 from의 complete가 실행되고 complete에 들어가면 next가 있나 체크한 뒤 있으면 run을 실행.

* run이 실행되면 andApply에서 적용한 Completion이 들어왔을 때는 Consumer가 아닌 Function이 존재하게 됨
* andApply는 중간단계이기 때문에 앞에서 넘겨준 결과를 이용해 비동기 작업을 수행하고 자기 자신의 비동기 작업에 대한 결과를 다음 단계에 넘겨주어야 함
* fn.apply()에서 넘겨주는 value는 전 단계에서 실행한 비동기 작업의 결과값으로 넘어온 것을 ListenableFuture 타입으로 받음.
* 이후 from 메소드와 똑같이 결과가 성공이면 complete, 실패면 error를 호출하도록 작성
* andApply는 자신의 비동기 작업의 결과값을 다른 Completion이 작업을 수행할 수 있도록 해야 하기 때문에 Completion 리턴
* 마지막으로 두번째 api 호출한 결과값을 최종적으로 andAccept에게 넘겨줌

* Output : 첫번째 api 호출해서 service1을 받고, 두 번째 api를 호출해서 service2를 받아서 최종적으로 문자 완성

---

## 3.

```java
public static class Completion {
    Completion next;

    public void andAccept(Consumer<ResponseEntity<String>> con) {
        Completion c = new AcceptCompletion(con);
        this.next = c;
    }

    public Completion andApply(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {
        Completion c = new ApplyCompletion(fn);
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
//        if (con != null) con.accept(value);
//        else if (fn != null) {
//            ListenableFuture<ResponseEntity<String>> lf = fn.apply(value);
//            lf.addCallback(s->complete(s), e->error(e));
//        }
    }
}
```

* next, andAccept, andApply는 어떤 종류의 Completion에서도 다 필요
* from은 static 메소드로 기본으로 가져야 하므로
* error/complete도 동일하므로
* run에서 각각의 어떤 용도로 Completion 오브젝트가 만들어 질 때 Accept인지, Apply인지 if문으로 구분해서 체크하는 방식 리팩토링
  * 결과를 받아서 사용하고 끝나는 Accept용과 Apply용으로 Completion을 두개로 분리하여 다형성 적용

```java
public static class AcceptCompletion extends Completion {
    public Consumer<ResponseEntity<String>> con;
    public AcceptCompletion(Consumer<ResponseEntity<String>> con) {
        this.con = con;
    }

    @Override
    void run(ResponseEntity<String> value) {
        con.accept(value);
    }
}

public static class ApplyCompletion extends Completion {
    public Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn;
    public ApplyCompletion(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {
        this.fn = fn;
    }

    @Override
    void run(ResponseEntity<String> value) {
        ListenableFuture<ResponseEntity<String>> lf = fn.apply(value);
        lf.addCallback(s->complete(s), e->error(e));
    }
}
```

* Consumer를 받아서 저장하고 생성하는 일은 Accept에서만 필요
* Function을 받아서 처리하고 생성하는 일은 Apply에서만 필
* Completion에 기본으로 만들어져있는 run에 각 클래스의 특성에 맞는 작업만 수행하도록 작성
  * AcceptCompletion이 만들어 진 것은 Consumer가 존재한다는 얘기이므로 if문으로 체크할 필요 없음.
  * ApplyCompletion도 마찬가지.

---

## 4.
* 에러 처리의 중복 해결 : 비동기 작업을 하나 수행할 때마다 매번 발생 > 에러처리를 딱 한번만 작업하고 싶음
  * 에러가 넘어왔을때는 스프링 MVC의 ErrResult에 넘겨주도록

```java
Completion
        .from(rt.getForEntity(URL1, String.class, "h" + idx))
        .andApply(s->rt.getForEntity(URL2, String.class, s.getBody()))
        .andError(e->dr.setErrorResult(e));
        .andAccept(s->dr.setResult(s.getBody()));
```

* andError가 호출되기 이전에 첫번째 비동기 작업, 두번째 비동기 작업 어디에서라도 에러가 발생하면 andError에 정의해 놓은 람다식이 실행되도록 작성할 것. 뒤에는 무시하고 종료
* 첫번째, 두번째에서 에러가 발생하지 않았으면 andError를 패스하고 마지막 작업으로 넘어가도록

```java
public static class Completion {
    Completion next;
    public void andAccept(Consumer<ResponseEntity<String>> con) { ... }
    public Completion andApply(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) { ... }
    public static Completion from(ListenableFuture<ResponseEntity<String>> lf) { ... }

    public Completion andError(Consumer<Throwable> econ) {
        Completion c = new ErrorCompletion(econ);
        this.next = c;
        return c;
    }

    void complete(ResponseEntity<String> s) { ... }
    void run(ResponseEntity<String> value) { }
}
```

* Consumer로 받을 것. 비동기 작업의 에러는 Throwable 타입.
* 받아서 처리만 하고 넘어가는 것으로 함. 복잡하게 하면 에러가 났을 때 정상 상태로 다시 전환해서 그 다음 작업으로 넘기는 것도 가능.
* 에러를 처리하는 ErrorCompletion 클래스 작성할 것
* 만약 `Consumer<Throwable> econ`이 실행된다면 andError한 다음 작업을 종료해야 함.
	* 하지만 에러가 없으면 다음 코드로 넘어가야 한다.
	* 그러려면 다음 단계의 Completion 연결이 필요하기 때문에 andError도 Completion을 return해야 함.

```java
public static class ErrorCompletion extends Completion {
    public Consumer<Throwable> econ;
    public ErrorCompletion(Consumer<Throwable> econ) {
        this.econ = econ;
    }

    @Override
    void run(ResponseEntity<String> value) {
        if (next != null) next.run(value);
    }
}
```

* 여기에서도 Consumer<Throwable> 타입이 들어온다.
* errorCompletion은 에러가 났을 때 ListenableFuture의 addCallback 중에서 실패했을 때 Throwable을 던져주면 받아서 어떤 처리를 할 것인지 정의해주는 부분
	* DeferredResult의 SetErrorResult를 호출하도록 작성
* 지금까지의 Consumer, Function 들은 이전단계에서 정상적으로 나왔을 때 처리해주는 코드
	* 위의 econ은 전 단계에서 정상적인 결과가 넘어왔을때 실행되면 안된다.
	* 정상적인 경우에는  andError를 패스해서 andApply의 결과를 andAccept에게 넘겨주어야 한다.
* 정상적으로 이전단계에서 넘어오면 run이 호출되므로 ErrorCompletion에서는 아무 일도 하지 않고 다음 단계가 있으면 넘기도록 한다.

```java
public static class Completion {
    Completion next;
    public void andAccept(Consumer<ResponseEntity<String>> con) { ... }
    public Completion andApply(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) { ... }
    public static Completion from(ListenableFuture<ResponseEntity<String>> lf) { ... }

    public Completion andError(Consumer<Throwable> econ) {
        Completion c = new ErrorCompletion(econ);
        this.next = c;
        return c;
    }

    void error(Throwable e) {
        if (next != null) next.error(e);
    }

    void complete(ResponseEntity<String> s) { if (next != null) next.run(s); }
    void run(ResponseEntity<String> value) { }
}
```

* callback의 Error를 처리할 때 불리는 코드를 작성
	* 첫번째에서 에러가 발생해서 호출되면 이 자체에서 무언가를 하는 게 아니라 다음 호출할 게 있으면 next의 error를 호출하면서 Throwable 오브젝트를 넘겨준다.
* callback 중에서 성공하는 complete 메소드가 호출되면 next의 run이 호출됐지만 실패하면 next의 error를 호출해야 한다.
* 에러가 발생하면 중간단계 비동기 작업을 실행하지 않고 계속 next.error를 실행하다가 ErrorCompletion이 넘어오면 다른 작업을 해야 한다.

```java
public static class ErrorCompletion extends Completion {
    public Consumer<Throwable> econ;
    public ErrorCompletion(Consumer<Throwable> econ) {
        this.econ = econ;
    }

    @Override
    void run(ResponseEntity<String> value) {
        if (next != null) next.run(value);
    }

	@Override
	void error(Throwable e) {
		econ.accept(e);
	}
}
```

* 어느 단계인지 모르겠지만 어디선가 에러가 던져져서 계속 error를 타고 넘어온 것을 처리해주고 끝.
* 더이상 뒤로 넘길 필요는 없다.
* Spring MVC는 DeferredResult에 결과를 써줬으니 정상적으로 처리해서 클라이언트에게 전달한다.
* 에러가 없으면 아무 일도 하지 않고 정상 처리하는 코드인 next의 run을 실행한다.
	* accept의 경우에는 run에서 aceept 람다식을 호출 `con.accept(value);`
	* apply의 경우에는 apply `fn.apply(value);`를 적용한 이후  만들어지는 비동기 작업에 또 callback을 셋팅.
	* error의 경우에는  에러가 발생하지 않으면 다음으로 계속 패스한다.


* Output 확인 : andError가 있음에도 불구하고 정상적인 경우에는 잘 작동하는지 확인, from이나 andApply에서 에러가 났을 때 에러 처리가 되는지 확인
	* setErrorResult할 때 exception을 던지면 client한테도 실패 처리를 한다.
	* 어떤 메세지를 넘기면 클라이언트한테 정상적인 api 호출의 결과로 string이 넘어가는 형태가 된다.
	* httpServerErrorException 500 : form에서 발생. 그 뒤의 andApply가 나오면 실행을 해야되는지 고민할 필요가 없음. error가 발생하면 패스해서 그 다음 작업으로 넘어감. 그러다 언젠가 andError를 만나면 처리한 후 종료

---

## 5.
```java
Completion
        .from(rt.getForEntity(URL1, String.class, "h" + idx))
        .andApply(s->rt.getForEntity(URL2, String.class, s.getBody()))
        .andApply(s->myService.work(s.getBody()))
        .andError(e->dr.setErrorResult(e.toString()))
        .andAccept(s->dr.setResult(s));
```
* asyncRestTemplate 말고 myService에 대한 비동기 작업(지금까지는 api 호출 비동기 작업)
* myService의 work를 걸고 앞의 결과를 넘겨줘도 파라미터 타입이 맞지 않음.
* `s` : 파라미터, `myService.work(s.getBody())` : 파라미터가 리턴하는 값.
	* asyncRestTemplate는 무조건 ListenableFuture를 Return하기 때문에 `ListenableFuture<ResponseEntity<String>>`으로 고정
* `myService.work(s.getBody())`가 return하는 비동기 작업의 최종 결과값은 ListenableFuture로 같음
	* 그 안의 타입이 바로 String을 리턴하고 api호출인 경우 ResponseEntitiy로 감싸진 String을 리턴함
	* 타입 파라미터를 적용할 수 있도록 제네릭 코드를 작성한다.
	* ListenableFuture를 리턴하는 모든 종류의 비동기 작업을 호출할 때 그 안의 결과 타입이 뭐든지 적용할 수 있는 형태로 작성
* 타입 파라미터가 어떻게 필요한지, 무엇이어야 하는지, 몇 개여야 하는지 생각
	* Completion은 이전 비동기 작업의 결과를 이어받아서 비동기 작업을 수행하고 다음의 비동기 작업에게 내 결과를 넘겨줌. 타입이 2개 있을 수 있다.
* 추가적으로 생각할 것
	* 타입 파라미터를 적용하려면 클래스 레벨에 정의된 타입 파라미터를 적용할 것인가?
	* 메소드 레벨에서 추가된 새로운 타입 파라미터인것인가?

```java
public static class Completion<S, T> { //Completion은 복잡한 타입 구조를 가지는 andApply를 적용하는 경우이기 때문에 타입 파라미터는 두개
    Completion next;
//  내가 작업을 하고 그 작업의 결과를 다음 Completion에 넘겨주는 것
//  public void andAccept(Consumer<ResponseEntity<String>> con) {
    public void andAccept(Consumer<T> con) { // 받는것이기 때문에 T
//      더이상 넘겨줄 게 없으므로 Void로 정의    
        Completion<T, Void> c = new AcceptCompletion<>(con);
        this.next = c;
    }

//  andApply는 내가 새롭개 생성하는 Completion에 적용하는 것. 이전의 값 전달.
//  그다음 Completion어떤 비동기 방식을 수행할지 모르기 때문에 새로운 V타입을 정의하고 메소드 레벨에 타입 파라미터를 추가
//  public Completion andApply(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {
    public <V> Completion<T, V> andApply(Function<T, ListenableFuture<V>> fn) {
        Completion<T, V> c = new ApplyCompletion<>(fn);
        this.next = c;
        return c;
    }

//  static에서 정의한 것이기 때문에 Class의 타입 파라미터와는 상관없으므로 메소드 타입 파라미터인 <S, T> 정의
//  public static Completion from(ListenableFuture<ResponseEntity<String> lf) {
    public static <S, T> Completion<S, T> from(ListenableFuture<T> lf) {  // 내가 수행할 것이기 때문에 T로 정의	    
        Completion<S, T> c = new Completion<>();
        lf.addCallback(s->{
            c.complete(s);
        }, e->{
            c.error(e);
        });
        return c;
    }

//  andError는 내가 넘겨준 걸 받는 Completion.
//  S, T는 정상적인 상태에서 처리하는 것. and가 붙은 것은 T타입으로 적용. Return할 때는 정상이면 값을 그대로 넘기기 때문에 똑같은 타입 사용
    public Completion<T, T> andError(Consumer<Throwable> econ) {
        Completion<T, T> c = new ErrorCompletion<>(econ);
        this.next = c;
        return c;
    }

//  Throwable은 타입 파라미터를 적용할 필요가 없음.
    void error(Throwable e) {
        if (next != null) next.error(e);
    }

//  Completion 오브젝트가 스스로 진행한 결과를 받아오기 때문에 T타입.
//  void complete(ResponseEntity<String> s) {
    void complete(T s) {
        if (next != null) next.run(s);
    }

//  어떤 Completion의 run은 앞의 Completion에서 받아서 수행하는 것이기 때문에 받는쪽의 타입인 S타입
//  void run(ResponseEntity<String> value) {
    void run(S value) {
    }
}
```

```java
//<S, T>로 정의해도 되는데 T타입이 별로 의미가 없음. 하나로만 정의하고 Completion에는 더이상 넘겨줄 게 없으므로 Void로 정의
public static class AcceptCompletion<S> extends Completion<S, Void> {
//  앞단계에서 넘어온 파라미터를 받아서 처리하기 때문에 S타입
//  public Consumer<ResponseEntity<String>> con;
    public Consumer<S> con;
//  파라미터로 넘겨받는 것 또한 S타입    
//  public AcceptCompletion(Consumer<ResponseEntity<String> con) {    
    public AcceptCompletion(Consumer<S> con) {
        this.con = con;
    }

    @Override
//  여기도 이전걸 넘겨 받았으므로 S타입    
//  void run(ResponseEntity<String> value) {    
    void run(S value) {
        con.accept(value);
    }
}

// 자체만 보면 두개를 받아야 함.
public static class ApplyCompletion<S, T> extends Completion<S, T> {
//  public Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>> fn;
    public Function<S, ListenableFuture<T>> fn;
//  public ApplyCompletion(Function<ResponseEntity<String>, ListenableFuture<ResponseEntity<String>>> fn) {    
    public ApplyCompletion(Function<S, ListenableFuture<T>> fn) {
        this.fn = fn;
    }

    @Override
//  앞에서 넘어온 것을 받기 때문에 S 타입    
//  void run(ResponseEntity<String> value) {
    void run(S value) {

//      그걸 적용한 거니까 T타입
//      ListenableFuture<ResponseEntity<String>> lf = fn.apply(value);
        ListenableFuture<T> lf = fn.apply(value);
        lf.addCallback(s->complete(s), e->error(e));
    }
}


//  위랑 똑같(and가 붙은 것은 T타입으로 적용, Return할 때는 정상이면 값을 그대로 넘기기 때문에 똑같은 T타입)
public static class ErrorCompletion<T> extends Completion<T, T> {
    public Consumer<Throwable> econ;
    public ErrorCompletion(Consumer<Throwable> econ) {
        this.econ = econ;
    }

    @Override
//  void run(ResponseEntity<String> value) {    
    void run(T value) {
        if (next != null) next.run(value);
    }

    @Override
    void error(Throwable e) {
        econ.accept(e);
    }
}
```
