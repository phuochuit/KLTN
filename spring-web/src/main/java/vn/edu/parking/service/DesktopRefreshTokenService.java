package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.DesktopRefreshToken;
import vn.edu.parking.domain.DesktopSession;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.DesktopRefreshTokenRepository;
import vn.edu.parking.repository.DesktopSessionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class DesktopRefreshTokenService {
    private static final Duration ABSOLUTE_SESSION_LIFETIME = Duration.ofHours(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DesktopSessionRepository sessions;
    private final DesktopRefreshTokenRepository tokens;

    public DesktopRefreshTokenService(DesktopSessionRepository sessions, DesktopRefreshTokenRepository tokens) {
        this.sessions = sessions;
        this.tokens = tokens;
    }

    @Transactional
    public DesktopSessionGrant createSession(SystemAccount account, String deviceLabel) {
        if (account == null || !account.isEnabled() || account.isMustChangePassword())
            throw new IllegalArgumentException("Account cannot create a Desktop session");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ABSOLUTE_SESSION_LIFETIME);
        DesktopSession session = sessions.save(new DesktopSession(UUID.randomUUID().toString(), account,
            now, expiresAt, normalizeDeviceLabel(deviceLabel)));
        String rawToken = newRefreshToken();
        tokens.save(new DesktopRefreshToken(session, hash(rawToken), now, expiresAt));
        return new DesktopSessionGrant(session.getSessionId(), rawToken, expiresAt);
    }

    @Transactional
    public RotationResult rotate(String presentedToken) {
        Optional<String> tokenHash = validatedHash(presentedToken);
        if (tokenHash.isEmpty()) return RotationResult.invalid();

        DesktopRefreshToken current = tokens.lockByTokenHash(tokenHash.get()).orElse(null);
        if (current == null) return RotationResult.invalid();

        Instant now = Instant.now();
        DesktopSession session = sessions.lockBySessionId(current.getSession().getSessionId()).orElse(null);
        if (session == null) return RotationResult.invalid();

        if (current.getConsumedAt() != null) {
            session.revoke(now);
            sessions.save(session);
            return RotationResult.reused();
        }

        SystemAccount account = session.getAccount();
        if (current.isExpiredAt(now) || !session.isActiveAt(now) || !account.isEnabled()
                || account.getSecurityVersion() != session.getSecurityVersion()) {
            session.revoke(now);
            sessions.save(session);
            return RotationResult.invalid();
        }

        String replacementToken = newRefreshToken();
        String replacementHash = hash(replacementToken);
        current.consume(now, replacementHash);
        session.touch(now);
        tokens.save(current);
        tokens.save(new DesktopRefreshToken(session, replacementHash, now, session.getAbsoluteExpiresAt()));
        sessions.save(session);
        return RotationResult.rotated(session.getSessionId(), replacementToken);
    }

    @Transactional
    public boolean touchIfSessionActive(String sessionId, Long accountId, long tokenSecurityVersion, Instant now) {
        DesktopSession session = sessions.lockBySessionId(sessionId).orElse(null);
        if (session == null || session.getAccount().getId() == null
                || !session.getAccount().getId().equals(accountId)
                || session.getSecurityVersion() != tokenSecurityVersion
                || session.getAccount().getSecurityVersion() != tokenSecurityVersion
                || !session.getAccount().isEnabled()) return false;
        if (!session.isActiveAt(now)) return false;
        session.touch(now);
        sessions.save(session);
        return true;
    }

    @Transactional
    public boolean logout(String sessionId, Long accountId) {
        DesktopSession session = sessions.lockBySessionId(sessionId).orElse(null);
        if (session == null || session.getAccount().getId() == null
                || !session.getAccount().getId().equals(accountId)) return false;
        session.revoke(Instant.now());
        sessions.save(session);
        return true;
    }

    @Transactional
    public void revokeAllForAccount(Long accountId) {
        Instant now = Instant.now();
        for (DesktopSession session : sessions.findAllByAccount_IdAndRevokedAtIsNull(accountId)) {
            session.revoke(now);
            sessions.save(session);
        }
    }

    private static Optional<String> validatedHash(String rawToken) {
        if (rawToken == null || rawToken.length() != 43 || !rawToken.matches("[A-Za-z0-9_-]{43}"))
            return Optional.empty();
        try {
            if (Base64.getUrlDecoder().decode(rawToken).length != 32) return Optional.empty();
            return Optional.of(hash(rawToken));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String newRefreshToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String normalizeDeviceLabel(String deviceLabel) {
        if (deviceLabel == null) return null;
        String normalized = deviceLabel.trim();
        if (normalized.isEmpty()) return null;
        return normalized.substring(0, Math.min(normalized.length(), 120));
    }

    public record DesktopSessionGrant(String sessionId, String refreshToken, Instant absoluteExpiresAt) { }

    public record RotationResult(boolean rotated, boolean reuseDetected, String sessionId, String refreshToken) {
        static RotationResult invalid() { return new RotationResult(false, false, null, null); }
        static RotationResult reused() { return new RotationResult(false, true, null, null); }
        static RotationResult rotated(String sessionId, String refreshToken) {
            return new RotationResult(true, false, sessionId, refreshToken);
        }
    }
}
