package Ngaritech.com.payment_service.config;

import Ngaritech.com.payment_service.common.ApiCode;
import Ngaritech.com.payment_service.common.ApiResponse;
import Ngaritech.com.payment_service.security.SandboxApiKeyAuthenticationFilter;
import Ngaritech.com.payment_service.security.SandboxApiKeyProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SandboxApiKeyProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain paymentSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SandboxApiKeyAuthenticationFilter sandboxApiKeyAuthenticationFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/paystack").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/paystack/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/mpesa/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/mpesa/stk-transaction-result").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/mpesa/b2c-queue-timeout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/mpesa/b2c-transaction-result").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(sandboxApiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Authentication is required",
                                ApiCode.UNAUTHORIZED
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_FORBIDDEN,
                                "Access is denied",
                                ApiCode.FORBIDDEN
                        ))
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${payment.security.expected-audience}") String expectedAudience
    ) {
        JwtDecoder decoder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : JwtDecoders.fromIssuerLocation(issuerUri);

        if (decoder instanceof NimbusJwtDecoder nimbusJwtDecoder) {
            nimbusJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuerUri),
                    audienceValidator(expectedAudience)
            ));
        }

        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(this::authorities);
        return converter;
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        claimAsList(jwt.getClaim("roles")).forEach(role ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        );

        claimAsList(jwt.getClaim("scope")).forEach(scope ->
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope))
        );

        claimAsList(jwt.getClaim("scp")).forEach(scope ->
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope))
        );

        claimAsList(jwt.getClaim("permissions")).forEach(permission ->
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + permission))
        );

        return authorities;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "Missing required audience",
                null
        ));
    }

    private List<String> claimAsList(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }

        if (claim instanceof String value && !value.isBlank()) {
            return List.of(value.trim().split("\\s+"));
        }

        return List.of();
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String message,
            String code
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(message, code, null));
    }
}
