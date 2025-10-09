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