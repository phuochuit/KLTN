package vn.edu.parking.service;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GateShiftServiceTest {
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final GateShiftAssignmentRepository shifts = mock(GateShiftAssignmentRepository.class);
    private final GateShiftService service = new GateShiftService(accounts, shifts);

    @Test
    void assignsAnOvernightShiftAndNormalizesText() {
        var staff = account("gate-1", AccountRole.GATE_STAFF, true);
        var startsAt = Instant.parse("2026-10-15T15:00:00Z");
        var endsAt = Instant.parse("2026-10-16T00:00:00Z");
        when(accounts.findByUsername("gate-1")).thenReturn(Optional.of(staff));
        when(shifts.countOverlapping(staff.getId(), startsAt, endsAt)).thenReturn(0L);
        when(shifts.save(any(GateShiftAssignment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.assign(" gate-1 ", " Ca đêm ", startsAt, endsAt,
            " Kiểm soát cổng ", "   ");

        var saved = ArgumentCaptor.forClass(GateShiftAssignment.class);
        verify(shifts).save(saved.capture());
        assertEquals("Ca đêm", saved.getValue().getShiftName());
        assertEquals("Kiểm soát cổng", saved.getValue().getDuty());
        assertNull(saved.getValue().getNotes());
        assertEquals(startsAt, saved.getValue().getStartsAt());
        assertEquals(endsAt, saved.getValue().getEndsAt());
    }

    @Test
    void rejectsOverlappingShift() {
        var staff = account("gate-1", AccountRole.GATE_STAFF, true);
        var startsAt = Instant.parse("2026-10-15T01:00:00Z");
        var endsAt = Instant.parse("2026-10-15T09:00:00Z");
        when(accounts.findByUsername("gate-1")).thenReturn(Optional.of(staff));
        when(shifts.countOverlapping(staff.getId(), startsAt, endsAt)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
            () -> service.assign("gate-1", "Ca sáng", startsAt, endsAt, "Trực cổng", null));
        verify(shifts, never()).save(any());
    }

    @Test
    void rejectsShiftLongerThanTwentyFourHours() {
        var startsAt = Instant.parse("2026-10-15T01:00:00Z");
        var endsAt = Instant.parse("2026-10-16T01:00:01Z");

        assertThrows(IllegalArgumentException.class,
            () -> service.assign("gate-1", "Ca dài", startsAt, endsAt, "Trực cổng", null));
        verify(accounts, never()).findByUsername(any());
    }

    @Test
    void rejectsEndBeforeStart() {
        var startsAt = Instant.parse("2026-10-15T09:00:00Z");
        var endsAt = Instant.parse("2026-10-15T08:00:00Z");

        assertThrows(IllegalArgumentException.class,
            () -> service.assign("gate-1", "Ca lỗi", startsAt, endsAt, "Trực cổng", null));
    }

    @Test
    void rejectsManagementAndDisabledAccounts() {
        when(accounts.findByUsername("manager"))
            .thenReturn(Optional.of(account("manager", AccountRole.MANAGEMENT, true)));
        when(accounts.findByUsername("disabled"))
            .thenReturn(Optional.of(account("disabled", AccountRole.GATE_STAFF, false)));
        var startsAt = Instant.parse("2026-10-15T01:00:00Z");
        var endsAt = Instant.parse("2026-10-15T09:00:00Z");

        assertThrows(IllegalArgumentException.class,
            () -> service.assign("manager", "Ca sáng", startsAt, endsAt, "Trực cổng", null));
        assertThrows(IllegalArgumentException.class,
            () -> service.assign("disabled", "Ca sáng", startsAt, endsAt, "Trực cổng", null));
        verify(shifts, never()).save(any());
    }

    @Test
    void deletesExistingShiftAndRejectsMissingShift() {
        when(shifts.existsById(10L)).thenReturn(true);
        when(shifts.existsById(11L)).thenReturn(false);

        service.delete(10L);
        verify(shifts).deleteById(10L);
        assertThrows(IllegalArgumentException.class, () -> service.delete(11L));
        verify(shifts, never()).deleteById(11L);
    }

    private static SystemAccount account(String username, AccountRole role, boolean enabled) {
        var account = new SystemAccount(username, "test-password-hash", role, false);
        account.setEnabled(enabled);
        return account;
    }
}
