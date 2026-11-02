package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.GateEvidenceAssetRepository;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.ParkingSessionRepository;

class EvidenceComparisonServiceTest {
    private ParkingSessionRepository sessions;
    private GateEvidenceRepository evidences;
    private GateEvidenceAssetRepository assets;
    private PrivateEvidenceStorage storage;
    private ResidentImageStorage residentImages;
    private SecurityAuditService audit;
    private EvidenceComparisonService service;

    @BeforeEach
    void setUp() {
        sessions = mock(ParkingSessionRepository.class);
        evidences = mock(GateEvidenceRepository.class);
        assets = mock(GateEvidenceAssetRepository.class);
        storage = mock(PrivateEvidenceStorage.class);
        residentImages = mock(ResidentImageStorage.class);
        audit = mock(SecurityAuditService.class);
        service = new EvidenceComparisonService(sessions, evidences, assets, storage, residentImages, audit);
    }

    @Test
    void returnsMatchWhenEntryAndExitPlatesAndTypesMatch() throws Exception {
        ParkingSession session = new ParkingSession();
        var idField = ParkingSession.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(session, 101L);
        session.setEntryPlate("59A12345");
        session.setDetectedVehicleType(VehicleType.CAR);
        session.setEntryTime(LocalDateTime.now().minusHours(2));
        session.setStatus(SessionStatus.OPEN);

        when(sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc("59A12345", SessionStatus.OPEN))
            .thenReturn(Optional.of(session));

        SystemAccount account = new SystemAccount("thinh", "hash", AccountRole.GATE_STAFF, false);
        DesktopSession desktopSession = new DesktopSession("sid-1", account, Instant.now(), Instant.now().plusSeconds(3600), "gate");

        GateEvidence entryVeh = new GateEvidence("ev-entry-veh", EvidenceType.VEHICLE, EvidenceOperation.ENTRY,
            EvidenceSourceKind.IMAGE, account, desktopSession, "req-1", "v1", "sha", Instant.now(), Instant.now().plusSeconds(600));
        entryVeh.setVehicleResult("up-1", "59A12345", "CAR", 0.92, 0.88, 0.90, 0);

        GateEvidence exitVeh = new GateEvidence("ev-exit-veh", EvidenceType.VEHICLE, EvidenceOperation.EXIT,
            EvidenceSourceKind.IMAGE, account, desktopSession, "req-2", "v1", "sha", Instant.now(), Instant.now().plusSeconds(600));
        exitVeh.setVehicleResult("up-2", "59A12345", "CAR", 0.94, 0.91, 0.93, 0);

        GateEvidence exitFace = new GateEvidence("ev-exit-face", EvidenceType.FACE, EvidenceOperation.EXIT,
            EvidenceSourceKind.IMAGE, account, desktopSession, "req-3", "v1", "sha", Instant.now(), Instant.now().plusSeconds(600));
        exitFace.setFaceResult("up-3", null, "PASS", 0.78, 0.45, 0.35);

        when(evidences.findFirstByParkingSessionIdAndEvidenceTypeAndOperationAndStatusOrderByConsumedAtDesc(
            101L, EvidenceType.VEHICLE, EvidenceOperation.ENTRY, EvidenceStatus.CONSUMED))
            .thenReturn(Optional.of(entryVeh));
        when(evidences.findById("ev-exit-veh")).thenReturn(Optional.of(exitVeh));
        when(evidences.findById("ev-exit-face")).thenReturn(Optional.of(exitFace));

        GateEvidenceAsset asset1 = new GateEvidenceAsset("a1", entryVeh, EvidenceAssetRole.ANNOTATED_VEHICLE,
            "key1", "sha1", "image/jpeg", 4, 10, 10, Instant.now());
        when(assets.findFirstByEvidenceIdAndAssetRoleAndDeletedAtIsNull("ev-entry-veh", EvidenceAssetRole.ANNOTATED_VEHICLE))
            .thenReturn(Optional.of(asset1));
        when(storage.read("key1", "sha1")).thenReturn("img1".getBytes(StandardCharsets.UTF_8));

        var res = service.compare(null, "59A-123.45", "CAR", "ev-exit-veh", "ev-exit-face");

        assertEquals(101L, res.sessionId());
        assertTrue(res.plateMatched());
        assertTrue(res.vehicleTypeMatched());
        assertTrue(res.faceMatched());
        assertEquals("MATCH", res.matchStatus());
        assertFalse(res.entryVehicleImageBase64().isBlank());
        verify(audit).recordCurrent(eq("EVIDENCE_COMPARISON_VIEW"), eq("PARKING_SESSION"),
            eq("101"), eq("SUCCESS"), eq("MATCH"), eq("ev-exit-veh"));
    }

    @Test
    void returnsNoMatchWhenExitPlateDiffersFromEntryPlate() throws Exception {
        ParkingSession session = new ParkingSession();
        var idField = ParkingSession.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(session, 102L);
        session.setEntryPlate("59A12345");
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.now().minusHours(1));
        session.setStatus(SessionStatus.OPEN);

        when(sessions.findById(102L)).thenReturn(Optional.of(session));

        SystemAccount account = new SystemAccount("thinh", "hash", AccountRole.GATE_STAFF, false);
        DesktopSession desktopSession = new DesktopSession("sid-1", account, Instant.now(), Instant.now().plusSeconds(3600), "gate");
        GateEvidence exitVeh = new GateEvidence("ev-exit-mismatch", EvidenceType.VEHICLE, EvidenceOperation.EXIT,
            EvidenceSourceKind.IMAGE, account, desktopSession, "req-2", "v1", "sha", Instant.now(), Instant.now().plusSeconds(600));
        exitVeh.setVehicleResult("up-2", "59X99999", "MOTORBIKE", 0.90, 0.85, 0.88, 0);
        when(evidences.findById("ev-exit-mismatch")).thenReturn(Optional.of(exitVeh));

        var res = service.compare(102L, "59X99999", "MOTORBIKE", "ev-exit-mismatch", null);

        assertFalse(res.plateMatched());
        assertEquals("NO_MATCH", res.matchStatus());
    }
}
