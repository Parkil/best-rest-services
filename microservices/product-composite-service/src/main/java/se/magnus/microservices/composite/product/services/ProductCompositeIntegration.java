package se.magnus.microservices.composite.product.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.product.ProductService;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.recommendation.RecommendationService;
import se.magnus.api.core.review.Review;
import se.magnus.api.core.review.ReviewService;
import se.magnus.api.event.Event;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.api.exceptions.NotFoundException;
import se.magnus.util.http.HttpErrorInfo;
import se.magnus.util.http.ServiceUtil;

import java.io.IOException;
import java.net.URI;

import static java.util.logging.Level.FINE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static reactor.core.publisher.Flux.empty;
import static se.magnus.api.event.Event.Type.CREATE;
import static se.magnus.api.event.Event.Type.DELETE;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

  private final WebClient webClient;
  private final ObjectMapper mapper;
  private final StreamBridge streamBridge;
  private final Scheduler publishEventScheduler;
  private final ServiceUtil serviceUtil;

  // Resilience4j 레지스트리 추가
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final RetryRegistry retryRegistry;
  private final TimeLimiterRegistry timeLimiterRegistry;

  private static final String PRODUCT_SERVICE_URL = "http://product";
  private static final String RECOMMENDATION_SERVICE_URL = "http://recommendation";
  private static final String REVIEW_SERVICE_URL = "http://review";

  // Cannot find bean with qualifier 'publishEventScheduler' 오류 메시지는 신경 안써도 될듯
  // 실제 테스트를 돌려보면 정상적으로 주입되어 표시된다
  public ProductCompositeIntegration(
      @Qualifier("publishEventScheduler") Scheduler publishEventScheduler,

      WebClient.Builder webClientBuilder,
      ObjectMapper mapper,
      StreamBridge streamBridge,
      ServiceUtil serviceUtil,
      CircuitBreakerRegistry circuitBreakerRegistry,
      RetryRegistry retryRegistry,
      TimeLimiterRegistry timeLimiterRegistry
  ) {

    this.webClient = webClientBuilder.build();
    this.publishEventScheduler = publishEventScheduler;
    this.mapper = mapper;
    this.streamBridge = streamBridge;
    this.serviceUtil = serviceUtil;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.retryRegistry = retryRegistry;
    this.timeLimiterRegistry = timeLimiterRegistry;
  }

  @Override
  public Mono<Product> createProduct(Product body) {

    return Mono.fromCallable(() -> {
      sendMessage("products-out-0", new Event<>(CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Mono<Product> getProduct(int productId, int delay, int faultPercent) {
    URI url = UriComponentsBuilder.fromUriString(PRODUCT_SERVICE_URL
        + "/product/{productId}?delay={delay}&faultPercent={faultPercent}").build(productId, delay, faultPercent);
    LOG.debug("Will call the getProduct API on URL: {}", url);

    /*
      annotation 방식은 reactive 방식(Mono, Flux) 에서는 작동하지 않음(오류를 failCount 로 집계 하지 못함)
      timelimiter 설정
      원래 timelimiter 는 CircuitBreaker 와 별개로 집계되는 데이터이며 timelimiter 는 health indicator 가 정의되어 있지 않기 때문에
      별도의 health indicator 를 만들지 않는 이상 spring actuator 에 time limit 은 표시되지 않는다

      timelimiter 의 timeout 을 fail 로 집계해서 circuit 상태를 변경하고자 한다면 timelimiter 를 CircuitBreaker 로 감싸야 하는데
      주의점은 transformDeferred 순서를 timelimiter -> CircuitBreaker 순으로 해야 한다
      만약 CircuitBreaker -> timelimiter 순으로 하면 timelimit 오류는 발생하지만 이를 CircuitBreaker 에서 인식을 못해서 failCount 로 집계 되지 않음에 주의
     */
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

  // CircuitBreaker open 상태시 처리할 로직 정의
  private Mono<Product> getProductFallbackValue(int productId, int delay, int faultPercent, CallNotPermittedException ex) {

    LOG.warn("Creating a fail-fast fallback product for productId = {}, delay = {}, faultPercent = {} and exception = {} ",
        productId, delay, faultPercent, ex.toString());

    if (productId == 13) {
      String errMsg = "Product Id: " + productId + " not found in fallback cache!";
      LOG.warn(errMsg);
      throw new NotFoundException(errMsg);
    }

    return Mono.just(new Product(productId, "Fallback product" + productId, productId, serviceUtil.getServiceAddress()));
  }


  @Override
  public Mono<Void> deleteProduct(int productId) {

    return Mono.fromRunnable(() -> sendMessage("products-out-0", new Event<>(DELETE, productId, null)))
            .subscribeOn(publishEventScheduler).then();
  }

  @Override
  public Mono<Recommendation> createRecommendation(Recommendation body) {

    return Mono.fromCallable(() -> {
      sendMessage("recommendations-out-0", new Event<>(CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Flux<Recommendation> getRecommendations(int productId) {

    String url = RECOMMENDATION_SERVICE_URL + "/recommendation?productId=" + productId;

    LOG.debug("Will call the getRecommendations API on URL: {}", url);

    // Return an empty result if something goes wrong to make it possible for the composite service to return partial responses
    return webClient.get().uri(url).retrieve().bodyToFlux(Recommendation.class).log(LOG.getName(), FINE).onErrorResume(error -> empty());
  }

  @Override
  public Mono<Void> deleteRecommendations(int productId) {

    return Mono.fromRunnable(() -> sendMessage("recommendations-out-0", new Event<>(DELETE, productId, null)))
            .subscribeOn(publishEventScheduler).then();
  }

  @Override
  public Mono<Review> createReview(Review body) {

    return Mono.fromCallable(() -> {
      sendMessage("reviews-out-0", new Event<>(CREATE, body.getProductId(), body));
      return body;
    }).subscribeOn(publishEventScheduler);
  }

  @Override
  public Flux<Review> getReviews(int productId) {

    String url = REVIEW_SERVICE_URL + "/review?productId=" + productId;

    LOG.debug("Will call the getReviews API on URL: {}", url);

    // Return an empty result if something goes wrong to make it possible for the composite service to return partial responses
    return webClient.get().uri(url).retrieve().bodyToFlux(Review.class).log(LOG.getName(), FINE).onErrorResume(error -> empty());
  }


  @Override
  public Mono<Void> deleteReviews(int productId) {

    return Mono.fromRunnable(() -> sendMessage("reviews-out-0", new Event<>(DELETE, productId, null)))
            .subscribeOn(publishEventScheduler).then();
  }

  public Mono<Health> getProductHealth() {
    return getHealth(PRODUCT_SERVICE_URL);
  }

  public Mono<Health> getRecommendationHealth() {
    return getHealth(RECOMMENDATION_SERVICE_URL);
  }

  public Mono<Health> getReviewHealth() {
    return getHealth(REVIEW_SERVICE_URL);
  }

  private Mono<Health> getHealth(String url) {
    url += "/actuator/health";
    LOG.debug("Will call the Health API on URL: {}", url);
    return webClient.get().uri(url).retrieve().bodyToMono(String.class)
            .map(s -> new Health.Builder().up().build())
            .onErrorResume(ex -> Mono.just(new Health.Builder().down(ex).build()))
            .log(LOG.getName(), FINE);
  }

  private void sendMessage(String bindingName, Event<Integer, ?> event) {
    LOG.debug("Sending a {} message to {}. eventKey : {}", event.getEventType(), bindingName, event.getKey());
    Message<? extends Event<Integer, ?>> message = MessageBuilder.withPayload(event)
            .setHeader("partitionKey", event.getKey())
            .build();
    streamBridge.send(bindingName, message);
  }

  private Throwable handleException(Throwable ex) {

    if (!(ex instanceof WebClientResponseException wcre)) {
      LOG.warn("Got a unexpected error: {}, will rethrow it", ex.toString());
      return ex;
    }

    switch (wcre.getStatusCode()) {

      case NOT_FOUND:
        return new NotFoundException(getErrorMessage(wcre));

      case UNPROCESSABLE_ENTITY :
        return new InvalidInputException(getErrorMessage(wcre));

      default:
        LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", wcre.getStatusCode());
        LOG.warn("Error body: {}", wcre.getResponseBodyAsString());
        return ex;
    }
  }

  private String getErrorMessage(WebClientResponseException ex) {
    try {
      return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
    } catch (IOException ioex) {
      return ex.getMessage();
    }
  }
}