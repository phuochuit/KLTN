package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

class GateEvidenceServiceTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final DesktopSessionRepository sessions = mock(DesktopSessionRepository.class);
    private final FamilyMemberRepository members = mock(FamilyMemberRepository.class);
    private final GateEvidenceRepository evidences = mock(GateEvidenceRepository.class);
    private final GateEvidenceAssetRepository assets = mock(GateEvidenceAssetRepository.class);
    private GateEvidenceService service;
    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() throws Exception {
        Instant now = Instant.now();
        var account = new SystemAccount("gate", "hash", AccountRole.GATE_STAFF, false);
        var session = new DesktopSession("session-1", account, now, now.plusSeconds(3600), "test");
        when(accounts.findById(42L)).thenReturn(Optional.of(account));
        when(sessions.findById("session-1")).thenReturn(Optional.of(session));
        service = new GateEvidenceService(json, accounts, sessions, members, evidences, assets,
            new PrivateEvidenceStorage(temp.toString()), 10);
        Jwt jwt = new Jwt("token", now, now.plusSeconds(300), Map.of("alg", "none"),
            Map.of("sub", "42", "sid", "session-1"));
        authentication = new JwtAuthenticationToken(jwt);
    }

    @Test
    void createsVehicleEvidenceAndReplacesUpstreamId() throws Exception {
        String requestId = UUID.randomUUID().toString();
        byte[] upstream = json.writeValueAsBytes(Map.ofEntries(
            Map.entry("contractVersion", "1.0"), Map.entry("requestId", requestId),
            Map.entry("evidenceId", "upstream-id"), Map.entry("modelVersion", "plate-v1"),
            Map.entry("plateText", "59A1-123.45"), Map.entry("vehicleType", "MOTORBIKE"),
            Map.entry("detectionConfidence", 0.9), Map.entry("ocrConfidence", 0.8),
            Map.entry("vehicleConfidence", 0.7), Map.entry("frameIndex", 4),
            Map.entry("annotatedImageBase64", Base64.getEncoder().encodeToString(png()))));

        var created = service.createVehicle(authentication, EvidenceOperation.ENTRY,
            EvidenceSourceKind.IMAGE, requestId, upstream);
        var response = json.readTree(created.responseBody());

        assertEquals(created.evidenceId(), response.get("evidenceId").asText());
        assertNotEquals("upstream-id", created.evidenceId());
        verify(evidences, times(2)).save(any(GateEvidence.class));
        verify(assets).save(any(GateEvidenceAsset.class));
    }

    @Test
    void rejectsMismatchedRequestIdBeforeWritingEvidence() throws Exception {
        byte[] upstream = json.writeValueAsBytes(Map.of(
            "contractVersion", "1.0", "requestId", "other", "modelVersion", "plate-v1"));
        assertThrows(IllegalArgumentException.class, () -> service.createVehicle(authentication,
            EvidenceOperation.ENTRY, EvidenceSourceKind.IMAGE, "expected", upstream));
        verifyNoInteractions(evidences, assets);
    }

    @Test
    void requiresDesktopJwt() {
        assertThrows(IllegalArgumentException.class, () -> service.createVehicle(null,
            EvidenceOperation.ENTRY, EvidenceSourceKind.IMAGE, "request", "{}".getBytes()));
    }

    @Test
    void storesFaceLivenessVerdictFromUpstream() throws Exception {
        String requestId = UUID.randomUUID().toString();
        byte[] upstream = json.writeValueAsBytes(Map.ofEntries(
            Map.entry("contractVersion", "1.0"), Map.entry("requestId", requestId),
            Map.entry("evidenceId", "upstream-face"), Map.entry("modelVersion", "face-v1"),
            Map.entry("decision", "PASS"), Map.entry("similarity", 0.91),
            Map.entry("matchThreshold", 0.363), Map.entry("reviewThreshold", 0.30),
            Map.entry("livenessDecision", "SPOOF"), Map.entry("livenessConfidence", 0.12),
            Map.entry("livenessModel", "motion-blink/1.1"),
            Map.entry("quality", Map.of(
                "status", "WARNING",
                "version", "quality/1.0",
                "warnings", java.util.List.of(
                    Map.of("code", "FACE_BLURRY", "severity", "warning")))),
            Map.entry("realtimeImageBase64", Base64.getEncoder().encodeToString(png()))));

        var created = service.createFace(authentication, EvidenceOperation.ENTRY,
            EvidenceSourceKind.IMAGE, null, requestId, upstream);

        var captor = ArgumentCaptor.forClass(GateEvidence.class);
        verify(evidences, times(2)).save(captor.capture());
        GateEvidence saved = captor.getValue();
        assertEquals(created.evidenceId(), saved.getId());
        assertEquals(LivenessStatus.FAIL, saved.getLivenessStatus());
        assertEquals(0.12, saved.getLivenessScore().doubleValue(), 0.0001);
        assertEquals("anpr-service", saved.getLivenessProvider());
        assertEquals("motion-blink/1.1", saved.getLivenessModelVersion());
        assertEquals("WARNING", saved.getQualityStatus());
        assertEquals("FACE_BLURRY", saved.getQualityWarnings());
        assertEquals("quality/1.0", saved.getQualityVersion());
    }

    private static byte[] png() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } finally { image.flush(); }
    }
}
