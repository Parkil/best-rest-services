package se.magnus.api.core.product;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

public interface ProductService {

  /**
   * Sample usage, see below.
   * <p>
   * curl -X POST $HOST:$PORT/product \
   *   -H "Content-Type: application/json" --data \
   *   '{"productId":123,"name":"product 123","weight":123}'
   *
   * @param body A JSON representation of the new product
   * @return A JSON representation of the newly created product
   */
  @PostMapping(
    value    = "/product",
    consumes = "application/json",
    produces = "application/json")
  Mono<Product> createProduct(@RequestBody Product body);

  /**
   * Sample usage: "curl $HOST:$PORT/product/1".
   *
   * @param productId Id of the product
   * @return the product, if found, else null
   */
  @GetMapping(
    value = "/product/{productId}",
    produces = "application/json")
  Mono<Product> getProduct(@PathVariable int productId);

  /**
   * Sample usage: "curl -X DELETE $HOST:$PORT/product/1".
   *
   * @param productId Id of the product
   */
  @DeleteMapping(value = "/product/{productId}")
  Mono<Void> deleteProduct(@PathVariable int productId);
}
