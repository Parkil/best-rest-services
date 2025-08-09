### build 에 필요한 JDK/JRE 이미지 빌드 스크립트

```script
 docker buildx build -t alpine/jre21 -f .\Dockerfile_alpine_jre21 .
```

```script
 docker buildx build -t alpine/jdk21 -f .\Dockerfile_alpine_jdk21 .
```

### 주의사항
1.신규 모듈을 만들면 settings.gradle 에 추가 하고
2.Intellij 환경에서 절대로 F4 눌러서 module 수동 추가하지 말것. 설정이 꼬인다. 이경우에는 기존 코드 다 삭제하고 새로 설치하는 수밖에는 없다