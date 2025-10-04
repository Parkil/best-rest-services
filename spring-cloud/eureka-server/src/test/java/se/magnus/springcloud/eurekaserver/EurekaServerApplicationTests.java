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

@SpringBootTest(webEnvironment = RANDOM_PORT)
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

