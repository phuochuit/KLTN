package vn.edu.parking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.security.WebAccountSessionValidationFilter;
import vn.edu.parking.security.WebLoginThrottleFilter;
import vn.edu.parking.service.LoginAttemptThrottle;
import vn.edu.parking.service.SecurityAuditService;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, SystemAccountRepository accounts,
            SecurityAuditService audit, LoginAttemptThrottle loginThrottle) throws Exception {
        http
            .securityMatcher(new NegatedRequestMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/api/auth/**"),
                new AndRequestMatcher(
                    new AntPathRequestMatcher("/api/parking/**"),
                    new NegatedRequestMatcher(new AntPathRequestMatcher("/api/parking/revenue/**"))))))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error")
                    .permitAll()
                .requestMatchers("/account/password")
                    .hasAnyAuthority("ROLE_PASSWORD_CHANGE_REQUIRED", "ROLE_MANAGEMENT", "ROLE_GATE_STAFF")
                .requestMatchers("/api/parking/revenue/**")
                    .hasAuthority("ROLE_MANAGEMENT")
                .requestMatchers("/api/cccd/**")
                    .hasAuthority("ROLE_MANAGEMENT")
                .requestMatchers("/uploads/**")
                    .hasAuthority("ROLE_MANAGEMENT")
                .requestMatchers("/api/**", "/h2-console/**")
                    .denyAll()
                .anyRequest().hasAuthority("ROLE_MANAGEMENT"))
            .formLogin(login -> login.successHandler((request, response, authentication) -> {
                loginThrottle.recordSuccess(request.getRemoteAddr());
                audit.recordLoginSuccess(authentication);
                boolean passwordChangeRequired = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_PASSWORD_CHANGE_REQUIRED"));
                response.sendRedirect(passwordChangeRequired ? "/account/password" : "/");
            }).failureHandler((request, response, exception) -> {
                loginThrottle.recordFailure(request.getRemoteAddr());
                audit.recordLoginFailure(request.getParameter("username"));
                response.sendRedirect("/login?error");
            }))
            .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) -> {
                audit.record(authentication, "LOGOUT", "WEB_SESSION", null, "SUCCESS", null, null);
                response.sendRedirect("/login?logout");
            }))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    auditCccdQrDenial(request, audit, "UNAUTHENTICATED");
                    if (expectsJson(request)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write("{\"error\":\"unauthorized\"}");
                    } else {
                        new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, exception);
                    }
                })
                .accessDeniedHandler((request, response, exception) -> {
                    auditCccdQrDenial(request, audit, "INSUFFICIENT_ROLE");
                    if (expectsJson(request)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write("{\"error\":\"forbidden\"}");
                    } else {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    }
                }))
            .headers(headers -> headers.frameOptions(frame -> frame.deny()));
        http.addFilterAfter(new WebAccountSessionValidationFilter(accounts), SecurityContextHolderFilter.class);
        http.addFilterBefore(new WebLoginThrottleFilter(loginThrottle), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void auditCccdQrDenial(HttpServletRequest request, SecurityAuditService audit, String reason) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/cccd/scan-qr".equals(requestPath))
            audit.recordCurrent("CCCD_QR_ACCESS_DENIED", "CCCD_QR", null, "FAILURE", reason, null);
    }

    private static boolean expectsJson(HttpServletRequest request) {
        String path = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return path.startsWith("/api/") || path.startsWith("/admin-data/")
            || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
            || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
    }
}
