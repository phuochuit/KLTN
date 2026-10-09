package vn.edu.parking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import vn.edu.parking.service.DesktopJwtAuthenticationConverter;

@Configuration
public class DesktopApiSecurityConfig {
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "true")
    SecurityFilterChain desktopApiSecurityFilterChain(HttpSecurity http, JwtDecoder decoder,
            DesktopJwtAuthenticationConverter authenticationConverter) throws Exception {
        http
            .securityMatcher(desktopApiMatcher())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/parking/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/parking/slots/**").hasAuthority("ROLE_MANAGEMENT")
                .requestMatchers("/api/parking/vehicles-unassigned").hasAuthority("ROLE_MANAGEMENT")
                .requestMatchers(HttpMethod.POST, "/api/parking/entry", "/api/parking/exit-preview",
                    "/api/parking/exit-confirm").hasAnyAuthority("ROLE_MANAGEMENT", "ROLE_GATE_STAFF")
                .requestMatchers(HttpMethod.POST, "/api/parking/anpr/recognize/image")
                    .hasAnyAuthority("ROLE_MANAGEMENT", "ROLE_GATE_STAFF")
                .requestMatchers(HttpMethod.POST, "/api/parking/anpr/recognize/video")
                    .hasAnyAuthority("ROLE_MANAGEMENT", "ROLE_GATE_STAFF")
                .requestMatchers(HttpMethod.POST, "/api/parking/anpr/face/capture", "/api/parking/anpr/face/verify")
                    .hasAnyAuthority("ROLE_MANAGEMENT", "ROLE_GATE_STAFF")
                .requestMatchers(HttpMethod.GET,
                    "/api/parking/sessions/*/operations/*/evidence/*/*/image")
                    .hasAnyAuthority("ROLE_MANAGEMENT", "ROLE_GATE_STAFF")
                .requestMatchers(HttpMethod.POST,
                    "/api/parking/sessions/*/operations/*/evidence/*/preservation")
                    .hasAuthority("ROLE_MANAGEMENT")
                .requestMatchers(HttpMethod.DELETE,
                    "/api/parking/sessions/*/operations/*/evidence/*/preservation")
                    .hasAuthority("ROLE_MANAGEMENT")
                .requestMatchers(HttpMethod.GET, "/api/parking/open", "/api/parking/lookup",
                    "/api/parking/slots", "/api/parking/recent-unassigned")
                    .hasAnyAuthority("ROLE_MANAGEMENT", "ROLE_GATE_STAFF")
                .anyRequest().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain disabledDesktopApiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(desktopApiMatcher())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/parking/health").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    private static RequestMatcher desktopApiMatcher() {
        return new OrRequestMatcher(
            new AntPathRequestMatcher("/api/auth/**"),
            new AndRequestMatcher(
                new AntPathRequestMatcher("/api/parking/**"),
                new NegatedRequestMatcher(new AntPathRequestMatcher("/api/parking/revenue/**"))));
    }
}
