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
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
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

  // @formatter:off
  @Bean
  public RegisteredClientRepository registeredClientRepository() {

    /*
    OAuth - 권한 부여 서버에서 scope 를 설정하면 인증성공시 Access Token 에 지정한 scope 가 실려서
    반환이 되고 해당 access token 을 이용하여 url 호출시 scope 체크는 spring security 에서 알아서
    해주는 건가 아니면 코드로 따로 구현을 해야 하는 건가?

    readerClient, writerClient 가 동일한 평문 clientSecret 를 사용하는 경우 Registered client must be unique 오류가 발생한다
    encoding 된 secret 을 이용하면 문제가 없다고는 하는데 이부분 관련해서는 추가적인 확인이 필요
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

