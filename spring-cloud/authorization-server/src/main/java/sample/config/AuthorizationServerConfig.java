//CHECKSTYLE:OFF
/*
 * Copyright 2020-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sample.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import sample.jose.Jwks;

import java.time.Duration;
import java.util.UUID;

/**
 * @author Joe Grandja
 * @since 0.0.1
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationServerConfig.class);


  /**
   * Authorization Server security filter chain for Spring Authorization Server endpoints.
   * <p></p>
   * What it does
   * - Scopes this filter chain only to the Authorization Server endpoints using the
   *   endpoints matcher from OAuth2AuthorizationServerConfigurer (e.g. /oauth2/authorize,
   *   /oauth2/token, OIDC endpoints like /.well-known/openid-configuration, JWKs, consent, etc.).
   * - Requires authentication for any request that hits those endpoints.
   * - Enables OpenID Connect (OIDC) support on top of OAuth2.
   * - Disables CSRF protection for these endpoints (CSRF is not applicable to the token endpoints),
   *   while leaving CSRF handling to other chains if needed.
   * - Sets an AuthenticationEntryPoint that redirects unauthenticated users to "/login" so users
   *   see the login page when starting an authorization code flow.
   * - Enables Resource Server JWT support on these endpoints so JWT-authenticated requests to
   *   protected AS endpoints work where applicable.
   * <p></p>
   * Chain ordering and interaction
   * - Marked with @Order(1) so it runs BEFORE the application’s default web SecurityFilterChain
   *   defined in DefaultSecurityConfig (@Order(2)).
   * - This separation ensures the AS endpoints are handled by SAS’s configuration while the rest
   *   of the application (e.g., form login pages, actuator) is handled by the default chain.
   * <p></p>
   * Behind a gateway / proxy
   * - Together with gateway settings that preserve Host and forwarded headers, the AS builds
   *   correct external redirect URLs (e.g., [<a href="https://localhost:8443">...</a>]).
   * <p></p>
   * 한국어 설명
   * - 이 체인은 Spring Authorization Server가 제공하는 엔드포인트(/oauth2/**, OIDC 관련 엔드포인트 등)에만
   *   적용되도록 범위를 한정하고, 해당 요청은 인증을 요구합니다.
   * - 인증되지 않은 접근은 /login 으로 리다이렉트되며, CSRF 는 해당 엔드포인트에서만 비활성화합니다.
   * - @Order(1) 로 기본 보안 체인(@Order(2))보다 먼저 적용되어 권한 부여 서버 엔드포인트가 올바르게 동작합니다.\
   * -> oAuth2 관련된 모든 url(/oauth2/authorize,/oauth2/token, /.well-known/openid-configuration.. ) 설정은 
   * 여기서 한다
   */
  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    var authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
    authorizationServerConfigurer.oidc(Customizer.withDefaults());

    RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

    http
        .securityMatcher(endpointsMatcher)
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest().authenticated()
        )
        .csrf(csrf -> csrf
            .ignoringRequestMatchers(endpointsMatcher)
        )
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
        )
        .with(authorizationServerConfigurer, (config) -> { /* defaults */ });

    return http.build();
  }

  // @formatter:off
  @Bean
  public RegisteredClientRepository registeredClientRepository() {

    /*
    OAuth - 권한 부여 서버에서 scope 를 설정하면 인증성공시 Access Token 에 지정한 scope 가 실려서
    반환이 되고 해당 access token 을 이용하여 url 호출시 scope 체크는 spring security 에서 알아서
    해주는 건가 아니면 코드로 따로 구현을 해야 하는 건가?

    readerClient, writerClient 가 동일한 평문 clientSecret 를 사용하는 경우 Registered client must be unique 오류가 발생한다
    encoding 된 secret 을 이용하면 문제가 없다고는 하는데 이부분 관련해서는 추가적인 확인이 필요

    여기에서 지정되는 scope 는 해당 clientId / clientSecret 으로 생성되는 token 이 가질수 있는 권한을 지정하는 것이지
    /oauth2/token 으로 생성되는 token에 scope 가 자동으로 들어간다는 의미는 아님
     */
    LOG.info("register OAuth client allowing all grant flows...");
    RegisteredClient writerClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("writer")
            .clientSecret("{noop}secret-writer")  // {noop} prefix for plain text password 여기 설정된 게 Basic cmVhZGVyOnNlY3JldA== 데이터와 일치 해야 함
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://my.redirect.uri")
            .redirectUri("https://localhost:8443/webjars/swagger-ui/oauth2-redirect.html")
            .scope(OidcScopes.OPENID)
            .scope("product:read")
            .scope("product:write")
            .clientSettings(ClientSettings.builder()
                    .requireAuthorizationConsent(true)
                    .build())
            .tokenSettings(TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofHours(1))
                    .build())
            .build();

    RegisteredClient readerClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("reader")
            .clientSecret("{noop}secret-reader")  // {noop} prefix for plain text password
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://my.redirect.uri")
            .redirectUri("https://localhost:8443/webjars/swagger-ui/oauth2-redirect.html")
            .scope(OidcScopes.OPENID)
            .scope("product:read")
            .clientSettings(ClientSettings.builder()
                    .requireAuthorizationConsent(true)
                    .build())
            .tokenSettings(TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofHours(1))
                    .build())
            .build();
    return new InMemoryRegisteredClientRepository(writerClient, readerClient);
  }
  // @formatter:on

  @Bean
  public JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = Jwks.generateRsa();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder()
            .issuer("http://auth-server:9999")
            .build();
  }
}
//CHECKSTYLE:ON

