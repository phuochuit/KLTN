package vn.edu.parking.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.repository.SecurityAuditRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    @Test
    void deletesExpiredEventsInBatchAndRecordsCleanup() {
        var repository = mock(SecurityAuditRepository.class);
        var expired = new SecurityAuditEvent(null, "manager", "LOGIN_SUCCESS", "SYSTEM_ACCOUNT",
            "manager", "SUCCESS", null, null, NOW.minusSeconds(400L * 86400));
        when(repository.findTop100ByOccurredAtBeforeOrderByOccurredAtAsc(any()))
            .thenReturn(List.of(expired));
        var service = new SecurityAuditRetentionService(repository,
            Clock.fixed(NOW, ZoneOffset.UTC), 365);

        var result = service.cleanup();

        assertEquals(1, result.deletedEvents());
        assertEquals(NOW.minusSeconds(365L * 86400), result.cutoff());
        verify(repository).deleteAllInBatch(List.of(expired));
        verify(repository).save(any(SecurityAuditEvent.class));
    }

    @Test
    void rejectsRetentionShorterThanThirtyDays() {
        assertThrows(IllegalArgumentException.class, () -> new SecurityAuditRetentionService(
            mock(SecurityAuditRepository.class), Clock.fixed(NOW, ZoneOffset.UTC), 29));
    }
}
