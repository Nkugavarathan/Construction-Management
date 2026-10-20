package com.constructflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless API secured by bearer JWTs.
 *
 * <ul>
 *   <li><b>No server session</b>: every request carries its own access token.</li>
 *   <li><b>CSRF token disabled</b>: browsers never attach an {@code Authorization} header on their
 *       own, so bearer endpoints cannot be forged cross-site. The only cookie-authenticated
 *       endpoints (refresh, logout, switch organization) use a {@code SameSite=Strict} cookie plus
 *       an Origin check in {@link OriginValidator}. See ADR-0002.</li>
 *   <li><b>CORS</b>: production is same-origin (Next.js proxies /api), so CORS only matters when
 *       the frontend calls the API directly in development. Explicit origins, never "*".</li>
 *   <li><b>Method security</b>: {@code @PreAuthorize} on controllers checks permissions; services
 *       then check resource-level rules.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
                                    MembershipAuthenticationConverter authenticationConverter,
                                    ProblemResponseWriter problemWriter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint((request, response, ex) -> problemWriter.write(response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required or the session has expired.")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> problemWriter.write(response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required or the session has expired."))
                        .accessDeniedHandler((request, response, e) -> problemWriter.write(response,
                                HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.")))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    /**
     * Delegating encoder: hashes with bcrypt today ("{bcrypt}..." prefix) and can verify and upgrade
     * hashes made with a different algorithm in the future without a mass password reset.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Location", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
