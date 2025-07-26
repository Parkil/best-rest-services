package se.magnus.microservices.core.product;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DummyTest {

  @Test
  void mapperTests() {
    /*
    List<Integer> list = Flux.just(1,2,3,4)
            .filter(n -> n % 2 == 0)
            .map(n -> n * 2)
            .log()
            .collectList()
            .block();
    */

    Mono<List<Integer>> list = Flux.just(1,2,3,4)
            .filter(n -> n % 2 == 0)
            .map(n -> n * 2)
            .log()
            .collectList();
    list.subscribe(val -> assertThat(val).containsExactly(4,8));
  }
}
