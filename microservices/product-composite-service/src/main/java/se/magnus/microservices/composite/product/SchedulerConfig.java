package se.magnus.microservices.composite.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/*
  ProductCompositeIntegration - ProductCompositeServiceApplication 순환참조 오류 때문에
  Scheduler 설정을 별도 Config 로 분리
 */
@Configuration
public class SchedulerConfig {
  private static final Logger LOG = LoggerFactory.getLogger(SchedulerConfig.class);

  private final Integer threadPoolSize;
  private final Integer taskQueueSize;

  public SchedulerConfig(
          @Value("${app.threadPoolSize:10}") Integer threadPoolSize,
          @Value("${app.taskQueueSize:100}") Integer taskQueueSize
  ) {
    this.threadPoolSize = threadPoolSize;
    this.taskQueueSize = taskQueueSize;
  }

  @Bean
  public Scheduler publishEventScheduler() {
    LOG.info("Creates a messagingScheduler with connectionPoolSize = {}", threadPoolSize);
    return Schedulers.newBoundedElastic(threadPoolSize, taskQueueSize, "publish-pool");
  }
}
