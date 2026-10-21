package vn.edu.parking.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.parking.service.DesktopAuthenticationService;
import vn.edu.parking.service.LoginAttemptThrottle;
import vn.edu.parking.service.SecurityAuditService;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "true")
public class DesktopAuthController {
    private final DesktopAuthenticationService authenticationService;
    private final SecurityAuditService audit;
    private final LoginAttemptThrottle loginThrottle;

    public DesktopAuthController(DesktopAuthenticationService authenticationService,
            SecurityAuditService audit, LoginAttemptThrottle loginThrottle) {
        this.authenticationService = authenticationService;
        this.audit = audit;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        if (request == null || isBlank(request.username()) || request.username().length() > 80
                || request.password() == null || request.password().isEmpty()
                || request.password().length() > 1024)
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
        String remoteAddress = servletRequest.getRemoteAddr();
        if (loginThrottle.isBlocked(request.username(), remoteAddress))
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "too_many_attempts"));
        try {
            var tokens = authenticationService.login(
                request.username(), request.password(), request.deviceLabel());
            loginThrottle.recordSuccess(request.username(), remoteAddress);
            return ResponseEntity.ok(toResponse(tokens));
        } catch (AuthenticationException ex) {
            loginThrottle.recordFailure(request.username(), remoteAddress);
            audit.recordLoginFailure(request.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid_credentials"));
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
        String sessionId = authentication instanceof JwtAuthenticationToken jwt
            ? jwt.getToken().getClaimAsString("sid") : null;
        audit.record(authentication, "LOGOUT", "DESKTOP_SESSION", sessionId,
            "SUCCESS", null, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    ResponseEntity<ProfileResponse> session(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(toResponse(authenticationService.profile(
                Long.parseLong(jwt.getToken().getSubject()))));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private static TokenResponse toResponse(DesktopAuthenticationService.DesktopTokens tokens) {
        return new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn(),
            tokens.refreshToken(), tokens.sessionId(), tokens.absoluteExpiresAt(),
            tokens.profile().username(), tokens.profile().fullName(),
            toResponse(tokens.profile().activeShift()));
    }

    private static ProfileResponse toResponse(DesktopAuthenticationService.OperatorProfile profile) {
        return new ProfileResponse(profile.username(), profile.fullName(),
            toResponse(profile.activeShift()));
    }

    private static ShiftResponse toResponse(DesktopAuthenticationService.ActiveShift shift) {
        return shift == null ? null : new ShiftResponse(shift.id(), shift.name(), shift.startsAt(),
            shift.endsAt(), shift.duty());
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    public record LoginRequest(String username, String password, String deviceLabel) { }
    public record RefreshRequest(String refreshToken) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn,
            String refreshToken, String sessionId, java.time.Instant absoluteExpiresAt,
            String username, String fullName, ShiftResponse activeShift) { }
    public record ProfileResponse(String username, String fullName, ShiftResponse activeShift) { }
    public record ShiftResponse(Long id, String name, java.time.Instant startsAt,
            java.time.Instant endsAt, String duty) { }
}
