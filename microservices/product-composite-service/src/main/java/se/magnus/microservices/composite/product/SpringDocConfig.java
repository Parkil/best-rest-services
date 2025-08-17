package se.magnus.microservices.composite.product;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;

/*
  springdoc -swagger 의 서버 정보 설정
  springdoc -swagger 가 바로 외부에 노출이 될때는 servers 가 외부 domain 으로 바로 mapping 이 되는데
  ex) http://localhost:8080/openapi/swagger-ui/index.html -> Servers http://localhost:8080

  내부로 숨기는 경우 (지금 프로젝트 처럼 spring gateway 만 직접 통신을 담당 나어지는 중계) 서버정보가 정상적으로
  표시되지 않는 경우가 있어 명시적으로 서버 정보를 설정한다

 */
@OpenAPIDefinition(
        servers = {
                @Server(url = "http://localhost:8080", description = "Local-Server")
        }
)
public class SpringDocConfig {}
