package se.magnus.util.tracing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import zipkin2.reporter.Sender;
import zipkin2.reporter.kafka.KafkaSender;

/**
 * Configures Zipkin reporting over Kafka when the 'kafka' Spring profile is active.
 * This makes services publish tracing spans to the Kafka topic used by Zipkin (default: 'zipkin').
 */
@AutoConfiguration
@Profile("kafka")
public class ZipkinKafkaConfig {

    @Bean(destroyMethod = "close")
    public Sender zipkinKafkaSender(
            @Value("${zipkin.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        // Default topic is 'zipkin' which matches Zipkin collector expectation
        return KafkaSender.newBuilder()
                .bootstrapServers(bootstrapServers)
                .build();
    }

}
