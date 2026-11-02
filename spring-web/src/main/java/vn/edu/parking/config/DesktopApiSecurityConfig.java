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
                .requestMatchers(HttpMethod.GET, "/api/auth/session", "/api/auth/shift-summary").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                .requestMatchers("/api/ai/**")
                    .hasAuthority("GATE_OPERATE")
                .requestMatchers(HttpMethod.POST, "/api/parking/entry", "/api/parking/exit-preview",
                    "/api/parking/exit-confirm").hasAuthority("GATE_OPERATE")
                .requestMatchers(HttpMethod.GET, "/api/parking/open", "/api/parking/lookup",
                    "/api/parking/slots", "/api/parking/recent-unassigned",
                    "/api/parking/evidence-comparison", "/api/parking/exceptions",
                    "/api/parking/exceptions/export-csv")
                    .hasAuthority("GATE_OPERATE")
                .requestMatchers(HttpMethod.GET, "/api/parking/vehicles-unassigned")
                    .hasAuthority("VEHICLE_MANAGE")
                .requestMatchers(HttpMethod.POST, "/api/parking/slots/*/dispatch-session")
                    .hasAuthority("GATE_OPERATE")
                .requestMatchers(HttpMethod.POST, "/api/parking/slots/**")
                    .hasAuthority("SLOT_MANAGE")
                .anyRequest().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "false")
    SecurityFilterChain compatibilityDesktopApiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(desktopApiMatcher())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/parking/health").permitAll()
                .requestMatchers("/api/parking/**").permitAll()
                .requestMatchers("/api/ai/**").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    private static RequestMatcher desktopApiMatcher() {
        return new OrRequestMatcher(
            new AntPathRequestMatcher("/api/auth/**"),
            new AntPathRequestMatcher("/api/ai/**"),
            new AndRequestMatcher(
                new AntPathRequestMatcher("/api/parking/**"),
                new NegatedRequestMatcher(new AntPathRequestMatcher("/api/parking/revenue/**"))));
    }
}
