package se.magnus.springcloud.configserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

  /*
    authenticated 는 설정 되어 있지만 별도의 사용자를 설정하지 않는 경우
    기본 사용자는
    user / [로그에 표시되는 일회용 비밀번호] Using generated security password: ~
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      // Disable CSRF to allow POST to /encrypt and /decrypt endpoints
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(auth -> auth
              .requestMatchers("/actuator/**").permitAll()
              .anyRequest().authenticated()
      )
      .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}


