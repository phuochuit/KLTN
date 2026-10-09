package vn.edu.parking.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.parking.service.DesktopAuthenticationService;
import vn.edu.parking.service.LoginAttemptThrottle;
import vn.edu.parking.service.SecurityAuditService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "true")
public class DesktopAuthController {
    private final DesktopAuthenticationService authenticationService;
    private final SecurityAuditService audit;
    private final LoginAttemptThrottle loginThrottle;

    public DesktopAuthController(DesktopAuthenticationService authenticationService, SecurityAuditService audit,
            LoginAttemptThrottle loginThrottle) {
        this.authenticationService = authenticationService;
        this.audit = audit;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.username()) || request.username().length() > 80
                || request.password() == null || request.password().isEmpty() || request.password().length() > 1024)
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
        String remoteAddress = servletRequest.getRemoteAddr();
        if (loginThrottle.isBlocked(remoteAddress))
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "too_many_attempts"));
        try {
            var tokens = authenticationService.login(request.username(), request.password(), request.deviceLabel());
            loginThrottle.recordSuccess(remoteAddress);
            return ResponseEntity.ok(toResponse(tokens));
        } catch (AuthenticationException ex) {
            loginThrottle.recordFailure(remoteAddress);
            audit.recordLoginFailure(request.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
        }
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().length() != 43)
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
        return authenticationService.refresh(request.refreshToken())
            .<ResponseEntity<?>>map(tokens -> ResponseEntity.ok(toResponse(tokens)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid_refresh_token")));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(Authentication authentication) {
        authenticationService.logout(authentication);
        String sessionId = authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt
            ? jwt.getToken().getClaimAsString("sid") : null;
        audit.record(authentication, "LOGOUT", "DESKTOP_SESSION", sessionId, "SUCCESS", null, null);
        return ResponseEntity.noContent().build();
    }

    private static TokenResponse toResponse(DesktopAuthenticationService.DesktopTokens tokens) {
        return new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn(),
            tokens.refreshToken(), tokens.sessionId(), tokens.absoluteExpiresAt());
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    public record LoginRequest(String username, String password, String deviceLabel) { }
    public record RefreshRequest(String refreshToken) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
            String sessionId, java.time.Instant absoluteExpiresAt) { }
}
