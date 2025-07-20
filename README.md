### build 에 필요한 JDK/JRE 이미지 빌드 스크립트

```script
 docker buildx build -t alpine/jre21 -f .\Dockerfile_alpine_jre21 .
```

```script
 docker buildx build -t alpine/jdk21 -f .\Dockerfile_alpine_jdk21 .
```