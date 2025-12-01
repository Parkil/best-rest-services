package se.magnus.microservices.composite.product;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@ComponentScan("se.magnus")
public class ProductCompositeServiceApplication {

  @Value("${api.common.version}")
  String apiVersion;
  @Value("${api.common.title}")
  String apiTitle;
  @Value("${api.common.description}")
  String apiDescription;
  @Value("${api.common.termsOfService}")
  String apiTermsOfService;
  @Value("${api.common.license}")
  String apiLicense;
  @Value("${api.common.licenseUrl}")
  String apiLicenseUrl;
  @Value("${api.common.externalDocDesc}")
  String apiExternalDocDesc;
  @Value("${api.common.externalDocUrl}")
  String apiExternalDocUrl;
  @Value("${api.common.contact.name}")
  String apiContactName;
  @Value("${api.common.contact.url}")
  String apiContactUrl;
  @Value("${api.common.contact.email}")
  String apiContactEmail;

  @Bean
  public OpenAPI getOpenApiDocumentation() {
    return new OpenAPI()
            .info(new Info().title(apiTitle)
                    .description(apiDescription)
                    .version(apiVersion)
                    .contact(new Contact()
                            .name(apiContactName)
                            .url(apiContactUrl)
                            .email(apiContactEmail))
                    .termsOfService(apiTermsOfService)
                    .license(new License()
                            .name(apiLicense)
                            .url(apiLicenseUrl)))
            .externalDocs(new ExternalDocumentation()
                    .description(apiExternalDocDesc)
                    .url(apiExternalDocUrl));
  }

  /*
    아직 명확하지는 않지만, @LoadBalanced 어노테이션이 지정되면, Spring cloud 검색 서비스
    에서 서비스를 검색해서 http url, port 를 매핑해주는듯
    docker 상에서 product, recommendation, review 포트는 8080인데 ProductCompositeIntegration 에서는
    http://product 로 호출하고 이게 작동하는게 이상했는데 spring cloud 검색 서비스 (현재는 eureka)
    가 port 까지 같이 mapping 을 해주는 듯
   */
  @Bean
  public WebClient.Builder loadBalancedWebClientBuilder() {
    return WebClient.builder();
  }

  public static void main(String[] args) {
    SpringApplication.run(ProductCompositeServiceApplication.class, args);
  }
}
