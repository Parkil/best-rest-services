
package se.magnus.springcloud.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/*
  원래 샘플 코드에서는 @Configuration 을 안해도 SecurityConfig 가 실행이 되는 거 같았는데
  jdk 21 로 올린 다음에는 @Configuration 을 해야 SecurityConfig 가 실행 된다
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    LOG.info("EnableWebFluxSecurity Bean springSecurityFilterChain created");
    return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                    .pathMatchers("/headerrouting/**").permitAll()
                    .pathMatchers("/actuator/**").permitAll()
                    .pathMatchers("/eureka/**").permitAll()
                    .pathMatchers("/oauth2/**").permitAll()
                    .pathMatchers("/login/**").permitAll()
                    .pathMatchers("/error/**").permitAll()
                    .pathMatchers("/openapi/**").permitAll()
                    .pathMatchers("/webjars/**").permitAll()
                    .pathMatchers("/config/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
  }
}