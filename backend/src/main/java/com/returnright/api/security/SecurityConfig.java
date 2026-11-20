package com.returnright.api.security;

import com.returnright.core.exception.GlobalExceptionHandler;
import com.returnright.core.config.RrProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Backend route guards = the authority (Part 2 §11). One prefix per persona; ADMIN may use every staff area.
 * The state machine re-checks the role per transition, so a router mistake cannot let the wrong role act.
 */
@Configuration(proxyBeanMethods = false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public class SecurityConfig {

    private static final String W = "WAREHOUSE_AGENT";
    private static final String F = "FINANCE_REVIEWER";
    private static final String M = "OPS_MANAGER";
    private static final String A = "ADMIN";

    @Bean
    public SecurityFilterChain api(HttpSecurity http, JsonMapper mapper) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        http.csrf(AbstractHttpConfigurer::disable) // stateless Bearer tokens, no cookies
                .cors(org.springframework.security.config.Customizer.withDefaults()) // uses bean 'corsConfigurationSource'
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/healthz", "/readyz", "/actuator/health/**", "/actuator/prometheus",
                                "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/customer/lookup", "/api/v1/auth/staff/login").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/warehouse/**").hasAnyRole(W, A)
                        .requestMatchers("/api/v1/finance/**").hasAnyRole(F, A)
                        .requestMatchers("/api/v1/analytics/flagged-customers").hasAnyRole(M, F, A)
                        .requestMatchers("/api/v1/analytics/**").hasAnyRole(M, A)
                        .requestMatchers("/api/v1/ops/audit", "/api/v1/ops/reconciliation").hasAnyRole(M, F, A)
                        .requestMatchers("/api/v1/ops/**").hasAnyRole(M, A)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> {
                            String msg = String.valueOf(ex.getMessage()).toLowerCase();
                            boolean expired = msg.contains("expired");
                            write(res, mapper, 401, expired ? "TOKEN_EXPIRED" : "UNAUTHENTICATED",
                                    expired ? "Your session has expired. Please sign in again." : "Please sign in.");
                        })
                        .accessDeniedHandler((req, res, ex) -> write(res, mapper, 403, "FORBIDDEN_ROLE",
                                "You don't have permission to do that.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, mapper, 401, "UNAUTHENTICATED",
                                "Please sign in."))
                        .accessDeniedHandler((req, res, ex) -> write(res, mapper, 403, "FORBIDDEN_ROLE",
                                "You don't have permission to do that.")));
        return http.build();
    }

    private static void write(HttpServletResponse res, JsonMapper mapper, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(mapper.writeValueAsString(GlobalExceptionHandler.envelope(code, message, Map.of())));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(RrProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.security().corsOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "If-Match", "X-Correlation-ID"));
        c.setExposedHeaders(List.of("X-Correlation-ID", "ETag", "Idempotent-Replayed"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", c);
        return src;
    }
}
