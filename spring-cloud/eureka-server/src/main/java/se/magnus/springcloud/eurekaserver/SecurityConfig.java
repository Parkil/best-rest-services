package se.magnus.springcloud.eurekaserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final String username;
  private final String password;

  public SecurityConfig(
          @Value("${app.eureka-username}") String username,
          @Value("${app.eureka-password}") String password
  ) {
    this.username = username;
    this.password = password;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            // Disable CSRF to allow services to register themselves with Eureka
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auths -> auths
                    .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {
            });

    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    UserDetails user = User.builder()
            .username(username)
            .password(password)
            .authorities("USER")
            .build();

    return new InMemoryUserDetailsManager(user);
  }

  // Chapter 11 까지는 password 가 평문 기반으로 작동하기 때문에 폐기된 NoOpPasswordEncoder 를 사용
  @Bean
  @SuppressWarnings("deprecation")
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }
}
