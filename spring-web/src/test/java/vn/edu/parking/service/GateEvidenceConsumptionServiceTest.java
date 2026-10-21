package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.SystemAccountRepository;

class GateEvidenceConsumptionServiceTest {
    private static final String VEHICLE_ID = "10000000-0000-0000-0000-000000000001";
    private static final String FACE_ID = "10000000-0000-0000-0000-000000000002";
    private final GateEvidenceRepository evidences = mock(GateEvidenceRepository.class);
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final SystemAccount account = mock(SystemAccount.class);
    private final DesktopSession desktopSession = mock(DesktopSession.class);
    private GateEvidenceConsumptionService service;

    @BeforeEach
    void setUp() {
        service = new GateEvidenceConsumptionService(evidences, accounts, true);
        when(account.getId()).thenReturn(42L);
        when(desktopSession.getSessionId()).thenReturn("session-1");
        when(accounts.findById(42L)).thenReturn(Optional.of(account));
        authenticate(42L, "session-1");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsEvidenceFromAnotherDesktopSession() {
        GateEvidence vehicle = vehicle(EvidenceOperation.ENTRY, Instant.now().plusSeconds(60));
        DesktopSession otherSession = session("other-session");
        when(vehicle.getDesktopSession()).thenReturn(otherSession);
        lock(VEHICLE_ID, vehicle);

        var error = assertThrows(IllegalStateException.class, () ->
            service.resolveForConsumption(VEHICLE_ID, null, EvidenceOperation.ENTRY, null));

        assertTrue(error.getMessage().contains("Desktop session"));
    }

    @Test
    void rejectsEvidenceFromAnotherAccount() {
        GateEvidence vehicle = vehicle(EvidenceOperation.ENTRY, Instant.now().plusSeconds(60));
        SystemAccount other = mock(SystemAccount.class);
        when(other.getId()).thenReturn(99L);
        when(vehicle.getAccount()).thenReturn(other);
        lock(VEHICLE_ID, vehicle);

        var error = assertThrows(IllegalStateException.class, () ->
            service.resolveForConsumption(VEHICLE_ID, null, EvidenceOperation.ENTRY, null));

        assertTrue(error.getMessage().contains("tài khoản"));
    }

    @Test
    void rejectsWrongOperation() {
        lock(VEHICLE_ID, vehicle(EvidenceOperation.ENTRY, Instant.now().plusSeconds(60)));

        var error = assertThrows(IllegalStateException.class, () ->
            service.resolveForConsumption(VEHICLE_ID, null, EvidenceOperation.EXIT, null));

        assertTrue(error.getMessage().contains("loại hoặc chiều"));
    }

    @Test
    void rejectsExpiredEvidence() {
        lock(VEHICLE_ID, vehicle(EvidenceOperation.ENTRY, Instant.now().minusSeconds(1)));

        var error = assertThrows(IllegalStateException.class, () ->
            service.resolveForConsumption(VEHICLE_ID, null, EvidenceOperation.ENTRY, null));

        assertTrue(error.getMessage().contains("hết hạn"));
    }

    @Test
    void rejectsFaceEvidenceForAnotherMember() {
        GateEvidence vehicle = vehicle(EvidenceOperation.ENTRY, Instant.now().plusSeconds(60));
        GateEvidence face = face(7L, EvidenceOperation.ENTRY, Instant.now().plusSeconds(60));
        lock(VEHICLE_ID, vehicle);
        lock(FACE_ID, face);

        var error = assertThrows(IllegalStateException.class, () ->
            service.resolveForConsumption(VEHICLE_ID, FACE_ID, EvidenceOperation.ENTRY, 8L));

        assertTrue(error.getMessage().contains("thành viên"));
    }

    @Test
    void previewValidatesWithoutConsumingAndConfirmConsumesOnce() {
        GateEvidence vehicle = vehicle(EvidenceOperation.EXIT, Instant.now().plusSeconds(60));
        GateEvidence face = face(7L, EvidenceOperation.EXIT, Instant.now().plusSeconds(60));
        when(evidences.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        when(evidences.findById(FACE_ID)).thenReturn(Optional.of(face));

        var preview = service.resolveForPreview(VEHICLE_ID, FACE_ID, EvidenceOperation.EXIT, 7L);
        verify(vehicle, never()).consume(any(), any(), any());
        verify(face, never()).consume(any(), any(), any());

        lock(VEHICLE_ID, vehicle);
        lock(FACE_ID, face);
        var confirm = service.resolveForConsumption(VEHICLE_ID, FACE_ID, EvidenceOperation.EXIT, 7L);
        ParkingSession parkingSession = new ParkingSession();
        service.consume(confirm, parkingSession);

        verify(vehicle).consume(eq(parkingSession), eq(account), any(Instant.class));
        verify(face).consume(eq(parkingSession), eq(account), any(Instant.class));
        assertFalse(preview.legacy());
    }

    @Test
    void requiresEvidenceWhenEnforcementIsEnabled() {
        var error = assertThrows(IllegalStateException.class, () ->
            service.resolveForConsumption(null, null, EvidenceOperation.ENTRY, null));
        assertTrue(error.getMessage().contains("yêu cầu Gate Evidence"));
    }

    private GateEvidence vehicle(EvidenceOperation operation, Instant expiresAt) {
        GateEvidence evidence = baseEvidence(EvidenceType.VEHICLE, operation, expiresAt);
        when(evidence.getPlateNumber()).thenReturn("59A112345");
        when(evidence.getVehicleType()).thenReturn("MOTORBIKE");
        return evidence;
    }

    private GateEvidence face(long memberId, EvidenceOperation operation, Instant expiresAt) {
        GateEvidence evidence = baseEvidence(EvidenceType.FACE, operation, expiresAt);
        FamilyMember member = mock(FamilyMember.class);
        when(member.getId()).thenReturn(memberId);
        when(evidence.getFamilyMember()).thenReturn(member);
        when(evidence.getFaceDecision()).thenReturn("PASS");
        when(evidence.getFaceSimilarity()).thenReturn(BigDecimal.valueOf(0.9));
        return evidence;
    }

    private GateEvidence baseEvidence(EvidenceType type, EvidenceOperation operation, Instant expiresAt) {
        GateEvidence evidence = mock(GateEvidence.class);
        when(evidence.getStatus()).thenReturn(EvidenceStatus.READY);
        when(evidence.getEvidenceType()).thenReturn(type);
        when(evidence.getOperation()).thenReturn(operation);
        when(evidence.getAccount()).thenReturn(account);
        when(evidence.getDesktopSession()).thenReturn(desktopSession);
        when(evidence.getExpiresAt()).thenReturn(expiresAt);
        return evidence;
    }

    private DesktopSession session(String id) {
        DesktopSession session = mock(DesktopSession.class);
        when(session.getSessionId()).thenReturn(id);
        return session;
    }

    private void lock(String id, GateEvidence evidence) {
        assertDoesNotThrow(() -> UUID.fromString(id));
        when(evidences.lockById(id)).thenReturn(Optional.of(evidence));
    }

    private static void authenticate(long accountId, String sessionId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(300), Map.of("alg", "none"),
            Map.of("sub", Long.toString(accountId), "sid", sessionId));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
