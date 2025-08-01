package se.magnus.microservices.composite.product;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.CompositeReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import se.magnus.microservices.composite.product.services.ProductCompositeIntegration;

import java.util.LinkedHashMap;
import java.util.Map;

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

  /**
   * Will exposed on $HOST:$PORT/swagger-ui.html
   *
   * @return the common OpenAPI documentation
   */
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

  private final ProductCompositeIntegration integration;

  @Autowired
  public ProductCompositeServiceApplication(
          ProductCompositeIntegration integration
  ) {
    this.integration = integration;
  }

  @Bean
  ReactiveHealthContributor coreServices() {

    final Map<String, ReactiveHealthIndicator> registry = new LinkedHashMap<>();

    registry.put("product", integration::getProductHealth);
    registry.put("recommendation", integration::getRecommendationHealth);
    registry.put("review", integration::getReviewHealth);

    return CompositeReactiveHealthContributor.fromMap(registry);
  }

  public static void main(String[] args) {
    SpringApplication.run(ProductCompositeServiceApplication.class, args);
  }

}
