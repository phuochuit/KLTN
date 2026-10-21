package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.GateEvidenceConsumptionService.ResolvedEvidence;

class ManualOverrideServiceTest {
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final DesktopSessionRepository desktopSessions = mock(DesktopSessionRepository.class);
    private final GateShiftAssignmentRepository shifts = mock(GateShiftAssignmentRepository.class);
    private final ManualOverrideEventRepository events = mock(ManualOverrideEventRepository.class);
    private final ManualOverrideEventRecorder rejectedEvents = mock(ManualOverrideEventRecorder.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final SystemAccount account = mock(SystemAccount.class);
    private final DesktopSession desktop = mock(DesktopSession.class);
    private final GateShiftAssignment shift = mock(GateShiftAssignment.class);
    private ManualOverrideService service;

    @BeforeEach
    void setUp() {
        service = new ManualOverrideService(accounts, desktopSessions, shifts, events,
            rejectedEvents, audit);
        when(accounts.findById(42L)).thenReturn(Optional.of(account));
        when(account.getId()).thenReturn(42L);
        when(account.isEnabled()).thenReturn(true);
        when(account.getRole()).thenReturn(AccountRole.GATE_STAFF);
        when(desktopSessions.findById("session-1")).thenReturn(Optional.of(desktop));
        when(desktop.getSessionId()).thenReturn("session-1");
        when(desktop.getAccount()).thenReturn(account);
        when(desktop.isActiveAt(any())).thenReturn(true);
        when(shift.getId()).thenReturn(9L);
        when(shifts.findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
            eq(42L), any(), any())).thenReturn(List.of(shift));
        authenticate();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizesReviewEvidenceForPermittedOnShiftAccount() {
        ResolvedEvidence evidence = evidence("REVIEW");

        var authorization = service.authorize(true, EvidenceOperation.EXIT,
            "FACE_REVIEW", "Khuôn mặt cần nhân viên kiểm tra trực tiếp", evidence);

        assertTrue(authorization.requested());
        assertEquals(ManualOverrideReasonCode.FACE_REVIEW, authorization.reasonCode());
        assertSame(shift, authorization.shift());
        verifyNoInteractions(rejectedEvents);
    }

    @Test
    void rejectsNonGateAccountAndRecordsEvent() {
        when(account.getRole()).thenReturn(AccountRole.MANAGEMENT);
        ResolvedEvidence evidence = evidence("REVIEW");

        var error = assertThrows(IllegalStateException.class, () -> service.authorize(true,
            EvidenceOperation.ENTRY, "FACE_REVIEW", "Nhân viên đã kiểm tra trực tiếp", evidence));

        assertTrue(error.getMessage().contains("nhân viên trạm gác"));
        verify(rejectedEvents).rejected(eq(EvidenceOperation.ENTRY), eq("FACE_REVIEW"), anyString(),
            eq(42L), eq("session-1"), eq(9L), anyString(), anyString(), contains("nhân viên trạm gác"));
    }

    @Test
    void rejectsWhenNoActiveShift() {
        when(shifts.findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
            eq(42L), any(), any())).thenReturn(List.of());

        var error = assertThrows(IllegalStateException.class, () -> service.authorize(true,
            EvidenceOperation.EXIT, "FACE_FAILED", "Đã đối chiếu giấy tờ của khách", evidence("FAIL")));

        assertTrue(error.getMessage().contains("ca trực"));
        verify(rejectedEvents).rejected(any(), any(), any(), eq(42L), eq("session-1"),
            isNull(), anyString(), anyString(), contains("ca trực"));
    }

    @Test
    void rejectsReasonThatDoesNotMatchFaceDecision() {
        var error = assertThrows(IllegalStateException.class, () -> service.authorize(true,
            EvidenceOperation.EXIT, "FACE_FAILED", "Điểm khuôn mặt cần xem xét thủ công", evidence("REVIEW")));

        assertTrue(error.getMessage().contains("không phù hợp"));
        verify(rejectedEvents).rejected(any(), any(), any(), eq(42L), eq("session-1"),
            eq(9L), anyString(), anyString(), contains("không phù hợp"));
    }

    @Test
    void recordsApprovedEventAgainstParkingSession() {
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var authorization = service.authorize(true, EvidenceOperation.EXIT,
            "FACE_FAILED", "Đã kiểm tra giấy tờ và xác nhận người lái", evidence("FAIL"));
        ParkingSession parkingSession = new ParkingSession();

        service.recordApproved(authorization, parkingSession,
            "59A112345", "59A112346", "MOTORBIKE", "CAR");

        verify(events).save(argThat(event -> event.getOutcome() == ManualOverrideOutcome.APPROVED
            && event.getReasonCode().equals("FACE_FAILED")
            && "59A112345".equals(event.getOriginalPlateNumber())
            && "59A112346".equals(event.getCorrectedPlateNumber())
            && "MOTORBIKE".equals(event.getOriginalVehicleType())
            && "CAR".equals(event.getCorrectedVehicleType())));
        verify(audit).recordCurrent(eq("MANUAL_OVERRIDE_APPROVED"), any(), any(),
            eq("SUCCESS"), isNull(), anyString());
    }

    private ResolvedEvidence evidence(String decision) {
        GateEvidence vehicle = mock(GateEvidence.class);
        when(vehicle.getId()).thenReturn("10000000-0000-0000-0000-000000000001");
        GateEvidence face = mock(GateEvidence.class);
        when(face.getId()).thenReturn("10000000-0000-0000-0000-000000000002");
        when(face.getFaceDecision()).thenReturn(decision);
        return new ResolvedEvidence(false, 42L, vehicle, face, "59A112345", "MOTORBIKE",
            false, 0.31, null);
    }

    private static void authenticate() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(300), Map.of("alg", "none"),
            Map.of("sub", "42", "sid", "session-1"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
