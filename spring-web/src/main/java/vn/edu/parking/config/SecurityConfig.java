package vn.edu.parking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityAuditService audit,
            SystemAccountRepository accounts, LoginAttemptThrottle loginThrottle,
            SessionRegistry sessionRegistry) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error")
                    .permitAll()
                .requestMatchers("/account/password")
                    .authenticated()
                .requestMatchers("/admin-data/gate-evidences/**", "/admin-data/members/*/face-image")
                    .hasAnyAuthority("PRIVATE_EVIDENCE_VIEW", "ROLE_MANAGEMENT")
                .requestMatchers("/account-admin/audit/**", "/security-audit")
                    .hasAnyAuthority("SECURITY_AUDIT_VIEW", "ROLE_MANAGEMENT")
                .requestMatchers("/account-management/shifts/**")
                    .hasAnyAuthority("SHIFT_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/account-management", "/account-management/live")
                    .hasAnyAuthority("ACCOUNT_MANAGE", "SHIFT_MANAGE", "SECURITY_AUDIT_VIEW", "ROLE_MANAGEMENT")
                .requestMatchers("/account-management/**", "/account-admin/**")
                    .hasAnyAuthority("ACCOUNT_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/roles/**")
                    .hasAnyAuthority("ROLE_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/sessions")
                    .hasAnyAuthority("HISTORY_VIEW", "ROLE_MANAGEMENT")
                .requestMatchers("/payments/**")
                    .hasAnyAuthority("PAYMENT_RECONCILE", "ROLE_MANAGEMENT")
                .requestMatchers("/api/parking/revenue/**")
                    .hasAnyAuthority("DASHBOARD_VIEW", "HISTORY_VIEW", "ROLE_MANAGEMENT")
                .requestMatchers("/api/cccd/**", "/households/**", "/members/**",
                    "/admin-data/members/**")
                    .hasAnyAuthority("RESIDENT_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/vehicles/**", "/registrations/**", "/admin-data/vehicles/**")
                    .hasAnyAuthority("VEHICLE_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/slots/**", "/settings/**")
                    .hasAnyAuthority("SLOT_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/cards/**").hasAnyAuthority("CARD_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/pricing/**").hasAnyAuthority("PRICING_MANAGE", "ROLE_MANAGEMENT")
                .requestMatchers("/").hasAnyAuthority("DASHBOARD_VIEW", "ROLE_MANAGEMENT")
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().denyAll())
            .formLogin(login -> login.loginPage("/login").permitAll()
                .successHandler((request, response, authentication) -> {
                loginThrottle.recordSuccess(request.getParameter("username"), request.getRemoteAddr());
                audit.recordLoginSuccess(authentication);
                boolean passwordChangeRequired = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_PASSWORD_CHANGE_REQUIRED"));
                response.sendRedirect(passwordChangeRequired ? "/account/password" : "/");
            }).failureHandler((request, response, exception) -> {
                loginThrottle.recordFailure(request.getParameter("username"), request.getRemoteAddr());
                audit.recordLoginFailure(request.getParameter("username"));
                response.sendRedirect("/login?error");
            }))
            .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) -> {
                audit.record(authentication, "LOGOUT", "WEB_SESSION", null, "SUCCESS", null, null);
                response.sendRedirect("/login?logout");
            }))
            .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessionRegistry))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/parking/**", "/h2-console/**"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.addFilterAfter(new WebAccountSessionValidationFilter(accounts), SecurityContextHolderFilter.class);
        http.addFilterBefore(new WebLoginThrottleFilter(loginThrottle, audit), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }
}
