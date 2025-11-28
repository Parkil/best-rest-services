### build 에 필요한 JDK/JRE 이미지 빌드 스크립트

```script
 docker buildx build -t alpine/jre21 -f Dockerfile_alpine_jre21 .
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

---

sleuth - 최신 버전은 3.1 이며 현재는 Micrometer 프로젝트 (https://micrometer.io/docs/tracing) 로 이관되어 더이상 유지보수가 되지 않는 상태

---

RabbitMQ 에서 queue 에 있는 message 조회시 http://localhost:15672 queue > get messages 에서
Ack Mode 를 Nack message requeue true 로 해야 데이터가 유실되지 않는다(queue 에서 message 를 가져온다음
다시 queue 에 넣는 설정)

RabbitMQ 에서는 queue 에 있는 메시지 조회만 하는 기능은 없다고 함

단 다시 queue 에 넣는 과정에서 입력 순서가 변동될 가능성이 존재 하기 때문에 별도의 queue 를 따로 만들어서
일종의 감사용으로 사용

본 프로젝트에서는 auditGroup 이 해당 역할 을 담당

http://localhost:9411/zipkin/dependency 에서 한번에 표시되는 dependency 만 trace 시 같이 표시된다

주의할점은 sampling 데이터가 없으면 dependency 도 표시되지 않음

zipkin + rabbitmq, kafka 모두 데이터는 정상적으로 표시가 되는데 product-composite -> product, review, recommendation span 이 연결이 안된다
지금까지 AI 로 찾아본 바로는 zipkin header 가 product-composite -> 하위 서비스로 전파가 안되어서 그렇다고는 하는데 설정할 수 있는 설정은 다 했음에도 
안되는 것으로 보아서는 다른 문제가 있는 듯


kafka-topics --bootstrap-server zookeeper --list

---

liveness probe : container 상태 체크
readiness probe : 요청 수락 준비가 되었는지 체크

calico flannel 같은 네트워크 플러그인을 CNI 라고 하는줄 알았는데 이제 보니까 CNI(Container network interface) 는 spec 의 일종
-> 네트워크 플러그인을 쓰지 않으면 기본적으로 모든 network 를 허용 

pod <-> deployment <-> service <-> ingress 

service 는 network endpoint 를 담당

실습을 위해 minikube + kubectl 을 이용

```bash
minikube start --profile=test --memory=10240 --cpus=4 --disk-size=20g --ports=8080:80 --ports=8443:443 --ports=30080:30080 --ports=30443:30443

minikube profile test

minikube addons enable ingress
minikube addons enable metrics-server
```

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deploy # deployment 명칭, 이 뒤에 랜덤 문자열이 붙는다
spec:
  replicas: 1 # replica 복제본 개수 설정
  selector: # deployment 에서 동일한 조건을 가지는 pod 를 검색
    matchLabels: 
      app: nginx-app
  template: # pod 생성 조건 지정
    metadata:
      labels:
        app: nginx-app
    spec: # pod 내부의 container (docker, podman ...) 설정
      containers:
      - name: nginx-container
        image: nginx:latest
        ports:
        - containerPort: 80
```

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-service
spec:
  type: NodePort # service 유형 (ClusterIP(내부 통신용), NodePort(외부통신 - 포트로 직점 전달), LoadBalancer(외부통신 - LoadBalancer 생성) ...)
  selector:
    app: nginx-app
  ports:
    - targetPort: 80
      port: 80
      nodePort: 30080
```
NodePort, LoadBalancer 차이점
- NodePort 는 외부 포트 -> service port 로 직접 트래픽이 전달
- LoadBalancer 는 외부 포트 -> LoadBalancer -> service 로 전달
- NodePort 의 경우에는 직접 전달되기 때문에 각 서비스별로 port가 전부 달라야 하고 로드 밸런싱이 안된다는 것이 단점

예전에 쿠버네티스 교과서로 helm 을 접했을때에는 단일 설정만 배웠는데 지금 설정을 보니 공통 (common/templates, common/values.yaml) 으로 설정한 다음
이를 상속받아서 사용하는 방법도 있다

template 만 상속받아서 채워야 하는 값만 해당 repo 의 values.yaml 에 설정

helm 에서 사용하는 template 구문은 go template 을 이용한다 (https://golang.org/pkg/text/template/)

```yaml
{{ (.Files.Glob "config-repo-txt/*").AsConfig | indent 2 }}
```
helm 에서 yml 파일을 읽어서 바로 helm 설정 파일로 변환

```yaml
{{- $common := dict "Values" .Values.common -}} # common/values.yaml 의 값 
{{- $noCommon := omit .Values "common" -}} # common/values.yaml 에 포함되지 않은 값
{{- $overrides := dict "Values" $noCommon -}} 
{{- $noValues := omit . "Values" -}} 
{{- with merge $noValues $overrides $common -}}
```

minikube docker
```bash
eval $(minikube docker-env)
./gradlew build && docker compose build
```

helm yaml 확인 (docker-compose config 와 유사)
```
helm template components/gateway -s templates/service.yaml
```

helm 의존성 업데이트
이 부분을 수행하면 environments/dev-env 에 tar파일이 생성된다
```
for f in kubernetes/helm/components/*; do helm dep up $f; done
for f in kubernetes/helm/environments/*; do helm dep up $f; done

helm dep ls kubernetes/helm/environments/dev-env/
```

helm 시작
```
helm install hands-on-dev-env kubernetes/helm/environments/dev-env -n hands-on --create-namespace

helm install --dry-run --debug hands-on-dev-env kubernetes/helm/environments/dev-env

kubectl config set-context $(kubectl config current-context) --namespace=hands-on

kubectl delete namespace hands-on
```



현재 구현되어 있는 시스템에서 단일 실패 지점은 config-server 다 config-server가 정상적으로 작동하지 않으면 microservice, spring-cloud 의 모든 서비스가 동작하지 않는다

현재 문제 liveness / readiness 에서는 80 으로 접근하는데 실제 서버에서는 8080 으로 포트가 설정됨 

```aiignore

apiVersion: v1
kind: ConfigMap
metadata:
  name: config-server
  labels:
    app.kubernetes.io/name: config-server
    helm.sh/chart: config-server-1.0.0
    app.kubernetes.io/managed-by: Helm
data:
  {}
```

현재 보면 설정 파일을 읽어들이지 못함

---

결론
질문하신 경로의 config-repo는 심볼릭 링크이며, 다음을 가리킵니다:
•
실제 대상: 프로젝트 루트의 config-repo 디렉터리
•
해석 경로: kubernetes/helm/components/config-server/config-repo → ../../../../config-repo → <프로젝트_루트>/config-repo
증거:
•
파일 내용이 한 줄로 ../../../../config-repo인 것은 Git에 저장된 심볼릭 링크(모드 120000)의 전형적인 형태입니다.
•
Helm 템플릿에서 .Files.Glob "config-repo/*"를 사용하고 있어, 차트 루트 기준의 config-repo(= 해당 심볼릭 링크) 아래 파일들을 읽습니다.
확인 방법들
다음 중 편한 방법으로 실제 가리키는 경로를 확인할 수 있습니다.
•
Linux/WSL에서
# 링크가 어디를 가리키는지(상대경로)
readlink kubernetes/helm/components/config-server/config-repo

# 절대경로로 확인
readlink -f kubernetes/helm/components/config-server/config-repo

# ls로도 링크 대상 확인
ls -l kubernetes/helm/components/config-server | grep config-repo
•
Windows PowerShell에서
# 링크 메타데이터 확인
Get-Item .\kubernetes\helm\components\config-server\config-repo | \
Select-Object FullName, LinkType, Target

# 혹은 재파스 포인트로 검색
Get-ChildItem .\kubernetes\helm\components\config-server -Attributes ReparsePoint
참고: \\wsl.localhost\Ubuntu-24.04\... 경로를 직접 지정해도 됩니다.
•
Git 메타데이터로 확인(운영체제 무관)
git ls-files -s kubernetes/helm/components/config-server/config-repo
# 모드가 120000이면 symlink, blob 내용이 상대경로(../../../../config-repo)입니다.

# 트리에서 보기
git ls-tree HEAD kubernetes/helm/components/config-server/config-repo
Helm 차트에서 실제로 읽히는지 검증
config-repo 아래 파일들이 ConfigMap에 포함되는지 템플릿 렌더링으로 확인합니다.
# 차트 루트는 components/config-server
helm template kubernetes/helm/components/config-server \
--show-only templates/configmap_from_file.yaml
렌더 결과의 data: 섹션에 <프로젝트_루트>/config-repo 아래의 *.yml/*.yaml/기타 파일 내용이 키-값으로 포함되어 나오면 정상입니다. (템플릿: kubernetes/helm/common/templates/_configmap_from_file.yaml의 (.Files.Glob "config-repo/*").AsConfig 로딩)


kubernetes/helm/components/config-server/config-repo 파일은 심볼릭 링크이며 별도로 생성해 주어야 한다
ln -s $(cat config-repo) config-repo

