package vn.edu.parking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.repository.DesktopSessionRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "true")
public class DesktopAuthenticationService {
    private static final long ACCESS_TOKEN_SECONDS = 15 * 60;

    private final AuthenticationManager authenticationManager;
    private final SystemAccountRepository accounts;
    private final DesktopSessionRepository sessions;
    private final DesktopRefreshTokenService refreshTokens;
    private final SecurityAuditService audit;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final String keyId;

    public DesktopAuthenticationService(AuthenticationManager authenticationManager,
            SystemAccountRepository accounts, DesktopSessionRepository sessions,
            DesktopRefreshTokenService refreshTokens, SecurityAuditService audit, JwtEncoder jwtEncoder,
            @Value("${parking.security.jwt.issuer}") String issuer,
            @Value("${parking.security.jwt.audience}") String audience,
            @Value("${parking.security.jwt.kid}") String keyId) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.keyId = keyId;
    }

    @Transactional
    public DesktopTokens login(String username, String password, String deviceLabel) {
        Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        if (!(authentication.getPrincipal() instanceof AccountPrincipal principal)
                || principal.mustChangePassword()) throw invalidCredentials();

        var account = accounts.findById(principal.getAccountId())
            .filter(current -> current.isEnabled() && !current.isMustChangePassword()
                && current.getSecurityVersion() == principal.getSecurityVersion())
            .orElseThrow(DesktopAuthenticationService::invalidCredentials);
        audit.recordLoginSuccess(authentication);
        var grant = refreshTokens.createSession(account, deviceLabel);
        return issueTokens(account.getId(), account.getSecurityVersion(), grant.sessionId(),
            grant.refreshToken(), grant.absoluteExpiresAt());
    }

    @Transactional
    public Optional<DesktopTokens> refresh(String refreshToken) {
        var rotation = refreshTokens.rotate(refreshToken);
        if (!rotation.rotated()) return Optional.empty();

        var session = sessions.findById(rotation.sessionId())
            .orElseThrow(() -> new IllegalStateException("Rotated Desktop session is missing"));
        var account = session.getAccount();
        if (!account.isEnabled() || account.isMustChangePassword()
                || account.getSecurityVersion() != session.getSecurityVersion()) return Optional.empty();
        return Optional.of(issueTokens(account.getId(), account.getSecurityVersion(), session.getSessionId(),
            rotation.refreshToken(), session.getAbsoluteExpiresAt()));
    }

    @Transactional
    public void logout(Authentication authentication) {
        if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt))
            return;
        long accountId = Long.parseLong(jwt.getToken().getSubject());
        refreshTokens.logout(jwt.getToken().getClaimAsString("sid"), accountId);
    }

    private DesktopTokens issueTokens(Long accountId, long securityVersion, String sessionId,
            String refreshToken, Instant absoluteExpiresAt) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ACCESS_TOKEN_SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(accountId.toString())
            .audience(List.of(audience))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("sid", sessionId)
            .claim("ver", securityVersion)
            .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyId).type("JWT").build(), claims)).getTokenValue();
        return new DesktopTokens(accessToken, ACCESS_TOKEN_SECONDS, refreshToken, sessionId, absoluteExpiresAt);
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid username or password");
    }

    public record DesktopTokens(String accessToken, long expiresIn, String refreshToken,
            String sessionId, Instant absoluteExpiresAt) { }
}
