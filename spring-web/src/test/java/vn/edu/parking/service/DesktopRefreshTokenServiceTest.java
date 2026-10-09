package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.DesktopRefreshToken;
import vn.edu.parking.domain.DesktopSession;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.DesktopRefreshTokenRepository;
import vn.edu.parking.repository.DesktopSessionRepository;

import java.time.Instant;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class DesktopRefreshTokenServiceTest {
    private final DesktopSessionRepository sessions = mock(DesktopSessionRepository.class);
    private final DesktopRefreshTokenRepository tokens = mock(DesktopRefreshTokenRepository.class);
    private final DesktopRefreshTokenService service = new DesktopRefreshTokenService(sessions, tokens);

    @Test
    void createsTenHourSessionAndStoresOnlySha256RefreshDigest() {
        SystemAccount account = account();
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));

        var issued = service.createSession(account, "garage desktop");

        assertNotNull(issued.sessionId());
        assertEquals(43, issued.refreshToken().length());
        ArgumentCaptor<DesktopSession> sessionCaptor = ArgumentCaptor.forClass(DesktopSession.class);
        ArgumentCaptor<DesktopRefreshToken> tokenCaptor = ArgumentCaptor.forClass(DesktopRefreshToken.class);
        verify(sessions).save(sessionCaptor.capture());
        verify(tokens).save(tokenCaptor.capture());
        assertNotEquals(issued.refreshToken(), tokenCaptor.getValue().getTokenHash());
        assertEquals(64, tokenCaptor.getValue().getTokenHash().length());
        assertTrue(tokenCaptor.getValue().getExpiresAt().equals(sessionCaptor.getValue().getAbsoluteExpiresAt()));
        assertEquals(java.time.Duration.ofHours(10), java.time.Duration.between(
            sessionCaptor.getValue().getCreatedAt(), sessionCaptor.getValue().getAbsoluteExpiresAt()));
    }

    @Test
    void rotatesValidRefreshTokenAndLinksTheConsumedDigestToReplacement() throws Exception {
        SystemAccount account = account();
        Instant now = Instant.now();
        DesktopSession session = new DesktopSession("session-1", account, now, now.plusSeconds(36000), "desktop");
        String rawToken = "A".repeat(43);
        DesktopRefreshToken current = new DesktopRefreshToken(session, hash(rawToken), now,
            session.getAbsoluteExpiresAt());
        when(tokens.lockByTokenHash(any())).thenReturn(Optional.of(current));
        when(sessions.lockBySessionId("session-1")).thenReturn(Optional.of(session));
        when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.rotate(rawToken);

        assertTrue(result.rotated());
        assertNotEquals(rawToken, result.refreshToken());
        assertNotNull(current.getConsumedAt());
        assertEquals(64, current.getReplacedByHash().length());
        verify(tokens).save(current);
    }

    @Test
    void reuseRevokesOnlyTheAffectedDesktopSession() throws Exception {
        SystemAccount account = account();
        Instant now = Instant.now();
        DesktopSession session = new DesktopSession("session-2", account, now, now.plusSeconds(36000), "desktop");
        String rawToken = "B".repeat(43);
        DesktopRefreshToken consumed = new DesktopRefreshToken(session, hash(rawToken), now,
            session.getAbsoluteExpiresAt());
        consumed.consume(now, "c".repeat(64));
        when(tokens.lockByTokenHash(any())).thenReturn(Optional.of(consumed));
        when(sessions.lockBySessionId("session-2")).thenReturn(Optional.of(session));

        var result = service.rotate(rawToken);

        assertFalse(result.rotated());
        assertTrue(result.reuseDetected());
        assertNotNull(session.getRevokedAt());
        verify(sessions).save(session);
    }

    private static SystemAccount account() {
        return new SystemAccount("gate-test", "synthetic-test-hash", AccountRole.GATE_STAFF, false);
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
