package se.magnus.springcloud.eurekaserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/*
  테스트 케이스 run 시에는 src/main/resources/application.yml + src/test/resources/application.yml 이 같이 설정되어 실행되는듯
  src/main/resources/application.yml 은 default profile 에 있는게 실행되는 듯
  spring.config.import: "configserver:" 오류가 계속 났던 이유가 SpringBootTest는 config server 를 사용하지 않기 때문에 pring.cloud.config.enabled=false 를 설정 했지만
  default profile 에 spring.config.import: "configserver:" 가 있어서 오류가 난 듯
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"spring.cloud.config.enabled=false"})
class EurekaServerApplicationTests {
  //@Value 의 주입시점은 생성자/필드/setter 메서드 호출 시점
  @Value("${app.eureka-username}")
  private String username;

  @Value("${app.eureka-password}")
  private String password;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  void contextLoads() {
  }

  @Test
  void catalogLoads() {
    String expectedResponseBody = "{\"applications\":{\"versions__delta\":\"1\",\"apps__hashcode\":\"\",\"application\":[]}}";

    // Basic Authentication을 적용한 TestRestTemplate 사용
    TestRestTemplate authenticatedTemplate = testRestTemplate.withBasicAuth(username, password);
    ResponseEntity<String> entity = authenticatedTemplate.getForEntity("/eureka/apps", String.class);

    assertEquals(HttpStatus.OK, entity.getStatusCode());
    assertEquals(expectedResponseBody, entity.getBody());
  }

  @Test
  void healthy() {
    String expectedResponseBody = "{\"status\":\"UP\"}";
    TestRestTemplate authenticatedTemplate = testRestTemplate.withBasicAuth(username, password);
    ResponseEntity<String> entity = authenticatedTemplate.getForEntity("/actuator/health", String.class);
    assertEquals(HttpStatus.OK, entity.getStatusCode());
    assertEquals(expectedResponseBody, entity.getBody());
  }
}

