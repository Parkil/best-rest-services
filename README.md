### build 에 필요한 JDK/JRE 이미지 빌드 스크립트

```script
 docker buildx build -t alpine/jre21 -f .\Dockerfile_alpine_jre21 .
```


### 주의사항
1.신규 모듈을 만들면 settings.gradle 에 추가 하고
2.Intellij 환경에서 절대로 F4 눌러서 module 수동 추가하지 말것. 설정이 꼬인다. 이경우에는 기존 코드 다 삭제하고 새로 설치하는 수밖에는 없다


### eureka 
eureka 설정을 안되어 있으면 docker 의 동일 network 로 묶인 service 는
http://[서비스명] 으로 접근이 가능하지만

eureka 설정이 되어 있으면 docker 의 동일 network 로 묶임 + eureka 설정이 같이 되어야 한다

eureka 관리 대상이 되는 app 은 반드시 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client' 의존성을 가져야 한다
그렇지 않으면 Spring actuator 결과가 실제 서버 결과와 상관없이 DOWN 으로 표시된다

### 환경 설정 관련
windows 에서 이 소스를 돌리려면 Docker Desktop or Rancher Desktop 을 설치해서 돌리는 방법외에는 없다
괜히 프로그램 하나더 설치하는게 싫어서 WSL 에 docker, docker-compose 를 설치하고 JetBrain Gateway 를 이용해서 WSL 환경에 
원격으로 붙는 방식으로 처리함

----------

### 실행 스크립트

해당 샘플코드에서는 java build, docker image build, run 이 다 분리되어 있으므로 별개의 명령어로 실행

##### java 프로젝트 build
```bash
./gradlew build
```

##### docker image 생성
```bash
docker-compose build
```

##### docker run
```bash
docker-compose up -d
```

##### 통합
```bash
docker-compose down && ./gradlew build && docker-compose build && docker-compose up -d
```
----------

### gateway actuator 실행시 주의점

gateway 에는 사설인증서를 이용한 TLS 가 설정되어 있가 때문에 curl test 시 https 로 접속해야 한다 http로 접속시
**curl: (52) Empty reply from server** 오류가 발생함
```bash
curl -k https://localhost:8443/actuator/health
```

----------

### Expose 와 docker port 설정 상관 관계

```dockerfile
FROM alpine/jre21 as builder
WORKDIR extracted
ADD ./build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM alpine/jre21
WORKDIR application
COPY --from=builder extracted/dependencies/ ./
COPY --from=builder extracted/spring-boot-loader/ ./
COPY --from=builder extracted/snapshot-dependencies/ ./
COPY --from=builder extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```
dockerfile에서의 EXPOSE 와 docker.yml 파일에서의 port 설정은 별개인듯 EXPOSE 는 내부 docker network 에서 이용하는 port고 
docker.yml 에서의 port 는 외부 접속용 port 인듯

----------

### config server 

```url
http://localhost:8888/config/default -> config-repo/application.yml
http://localhost:8888/config/docker -> config-repo/application.yml 의 docker profile 정보 반환 
http://localhost:8888/auth-server/default -> config-repo/auth-server.yml
```

##### 참고 사항
docker-compose.yml 에서 config server port 설정은 테스트를 위해 작성한 것으로 원래는 필요없는 기능

##### config 서버에서 지원하는 암/복호화
```암호화
curl -k https://dev-usr:dev-pwd@localhost:8443/config/encrypt --data-urlencode "test111"
```

```복호화
curl -k https://dev-usr:dev-pwd@localhost:8443/config/decrypt -d [암호화된 문자열]
```

설정에 {cipher} prefix 가 들어가면 config 서버에서 암/복호화 기능을 이용하여 자동으로 복호화해서 사용

----------

##### 서킷 브레이커
- 특정 서비스에 문제가 있다는 것이 감지되면 새로운 요청을 보내지 않도록 처리
- health check(=probe) 를 주기적으로 수행하여 서비스의 문제가 없다는 것이 판단되면 다시 요청을 보냄

##### 본 프로젝트에서는 Resilience4j 를 이용
    ###### 대안 
        - spring-cloud-circuitbreaker
        - reactive (Mono, Flux) - Mono.retryWhen, Mono.timeout

##### Resilience4j 상태
 - open
   - 오류가 발생하여 더이상 요청을 받지 않는 상태
   - 이 상태일때 수행할 비지니스 로직 / 반환값을 지정하여 빠르게 오류메시지를 반환한다
 - half open
   - open 상태에서 일정 시간이 지나면 half open 상태로 전환
   - 요청을 다시 받음
   - 오류가 발생하지 않으면 close, 오류가 발생하면 open 으로 전환
 - close
   - 정상 상태

##### Resilience4j 설정 에시

```yaml

resilience4j.retry: # circuit 재시도 설정 재시도 관련 정보는 /actuator/retryevents 에서 확인 
   instances:
      product:
         maxAttempts: 3
         waitDuration: 1000
         retryExceptions: # 재시도를 시도하는 오류 목록
            - org.springframework.web.reactive.function.client.WebClientResponseException$InternalServerError

resilience4j:
  circuitbreaker:
    instances:
      product:
        allowHealthIndicatorToFail: false # circuit 상태가 health check 에 영향을 주게 설정 true 인 경우 open, half-open 상태면 health check 가 fail 로 표시된다 false 면 서비스가 내려가지 않는 한 success 로 표시 
        registerHealthIndicator: true # health check 사용 (여기서는 spring actuator)
        slidingWindowType: COUNT_BASED # circuit open 기준 COUNT_BASED(회수), TIME_BASED(시간)
        slidingWindowSize: 5 # open 기준 여기서는 COUNT_BASED 로 설정했기 때문에 5번 호출의 결과를 가지고 open 여부를 판단
        minimumNumberOfCalls: 5 # 최소 호출 회수. 이 회수를 넘겨야 circuitbreaker 기준을 판단한다.
        failureRateThreshold: 50 # 실패 허용 비율 50% 이기 때문에 slidingWindowSize 의 50% 를 넘어가면 circuit이 open
        waitDurationInOpenState: 10000 # open -> half-open 으로 전환전 대기하는 시간
        permittedNumberOfCallsInHalfOpenState: 3 # half-open 상태에서 허용된 호출 수. 이 회수가 넘어가면 open 또는 close 상태로 전환되어야 함
        automaticTransitionFromOpenToHalfOpenEnabled: true # 대기 시간이 종료되면 half-open 상태로 전환할지 여부 false면 대기시간 종료 + 호출이 있을때 half-open 으로 전환
        ignoreExceptions: # circuit open, close 판단 기준에서 제외되는 예외
          - se.magnus.api.exceptions.InvalidInputException
          - se.magnus.api.exceptions.NotFoundException
  timelimiter:
    instances:
      product:
        timeoutDuration: 4s
        cancelRunningFuture: true
```

##### Resilience4j Reactive 환경
 - reactive(Mono,Flux) 환경에서는 annotation 은 작동하지 않는다
 - 아래 코드처럼 직접 transform 으로 호출해 주어야 함

##### Resilience4j timelimter failCount 설정
- 기본적으로 timelimter 와 circuitbreaker는 별개로 작동하며 metric 도 별개의 metric 을 사용한다
  - /actuator/metrics/resilience4j.timelimiter.calls (timelimter)
  - /actuator/metrics/resilience4j.circuitbreaker.calls (circuitbreaker)
- timelimter 오류를 circuitbreaker failCount 에 포함시키려면 timelimter 설정을 circuitbreaker로 감싸야 한다

###### timelimter 를 circuitbreaker로 감싸는 코드
timelimter 가 circuitbreaker 보다 먼저 호출되어야 한다

```java
public Mono<Product> getProduct(int productId, int delay, int faultPercent) {
   URI url = UriComponentsBuilder.fromUriString(PRODUCT_SERVICE_URL
           + "/product/{productId}?delay={delay}&faultPercent={faultPercent}").build(productId, delay, faultPercent);
   LOG.debug("Will call the getProduct API on URL: {}", url);

   return webClient.get()
           .uri(url)
           .retrieve()
           .bodyToMono(Product.class)
           .transformDeferred(TimeLimiterOperator.of(timeLimiterRegistry.timeLimiter("product")))
           .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker("product")))
           .transformDeferred(RetryOperator.of(retryRegistry.retry("product")))
           .doOnError(error -> LOG.warn("Error calling product service: {}", error.toString()))
           .onErrorResume(CallNotPermittedException.class, ex -> getProductFallbackValue(productId, delay, faultPercent, ex))
           .onErrorMap(WebClientResponseException.class, this::handleException)
           .log(LOG.getName(), FINE);
}
```

---

docker-compose exec -T product-composite curl -s http://product-composite:8080/actuator/health | jq

docker-compose exec -T product-composite curl -s http://product-composite:8080/actuator/metrics/resilience4j.circuitbreaker.calls | jq

docker-compose exec -T product-composite curl -s http://product-composite:8080/actuator/metrics/resilience4j.circuitbreaker.calls?tag=kind:failed | jq

docker-compose exec -T product-composite curl -s http://product-composite:8080/actuator/metrics/resilience4j.timelimiter.calls | jq

secret-writer