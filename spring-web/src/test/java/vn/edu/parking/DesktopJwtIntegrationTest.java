package vn.edu.parking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.parking.service.AiProxyService;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.EvidenceOperation;
import vn.edu.parking.domain.EvidenceSourceKind;
import vn.edu.parking.domain.EvidenceType;
import vn.edu.parking.domain.GateEvidence;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.DesktopRefreshTokenRepository;
import vn.edu.parking.repository.DesktopSessionRepository;
import vn.edu.parking.repository.GateEvidenceAssetRepository;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.ManualOverrideEventRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:desktop-jwt-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.upload-dir=target/test-desktop-jwt-uploads",
    "parking.private-storage-path=target/test-desktop-jwt-private"
})
@AutoConfigureMockMvc
class DesktopJwtIntegrationTest {
    private static final String PASSWORD = "GateTestPassword123!";
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SystemAccountRepository accounts;
    @Autowired DesktopSessionRepository sessions;
    @Autowired DesktopRefreshTokenRepository refreshTokens;
    @Autowired GateEvidenceRepository evidences;
    @Autowired GateEvidenceAssetRepository evidenceAssets;
    @Autowired GateShiftAssignmentRepository shifts;
    @Autowired ManualOverrideEventRepository manualEvents;
    @Autowired ParkingSessionRepository parkingSessions;
    @Autowired ParkingSlotRepository parkingSlots;
    @MockitoBean AiProxyService aiProxy;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("parking.security.jwt.enabled", () -> "true");
        registry.add("parking.security.jwt.issuer", () -> "https://parking.test");
        registry.add("parking.security.jwt.audience", () -> "parking-desktop");
        registry.add("parking.security.jwt.kid", () -> "integration-key");
        registry.add("parking.security.jwt.public-key-pem", () -> publicPem(KEY_PAIR));
        registry.add("parking.security.jwt.private-key-pem", () -> privatePem(KEY_PAIR));
    }

    @BeforeEach
    void setUp() {
        manualEvents.deleteAll();
        evidenceAssets.deleteAll();
        evidences.deleteAll();
        parkingSlots.deleteAll();
        parkingSessions.deleteAll();
        shifts.deleteAll();
        refreshTokens.deleteAll();
        sessions.deleteAll();
        accounts.deleteAll();
        accounts.save(new SystemAccount("gate-01", passwordEncoder.encode(PASSWORD),
            AccountRole.GATE_STAFF, false));
    }

    @Test
    void protectsDesktopApiAndAllowsGateStaffWithIssuedToken() throws Exception {
        mvc.perform(get("/api/parking/slots"))
            .andExpect(status().isUnauthorized());

        Tokens tokens = login();
        mvc.perform(get("/api/parking/slots")
                .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isOk());

        mvc.perform(post("/api/parking/slots/batch-generate")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void protectsAiProxyAndPreservesCorrelationId() throws Exception {
        mvc.perform(get("/api/ai/health"))
            .andExpect(status().isUnauthorized());

        when(aiProxy.health(anyString())).thenReturn("{\"status\":\"UP\"}".getBytes());
        Tokens tokens = login();
        mvc.perform(get("/api/ai/health")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Request-ID", "desktop-test-123"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-ID", "desktop-test-123"))
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void recognitionCreatesSpringOwnedEvidenceForAuthenticatedDesktopSession() throws Exception {
        String requestId = "vehicle-evidence-test";
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        byte[] upstream = objectMapper.writeValueAsBytes(java.util.Map.ofEntries(
            java.util.Map.entry("contractVersion", "1.0"),
            java.util.Map.entry("requestId", requestId),
            java.util.Map.entry("evidenceId", "fastapi-temporary-id"),
            java.util.Map.entry("modelVersion", "integration-model"),
            java.util.Map.entry("plateText", "59A1-123.45"),
            java.util.Map.entry("vehicleType", "MOTORBIKE"),
            java.util.Map.entry("detectionConfidence", 0.9),
            java.util.Map.entry("ocrConfidence", 0.8),
            java.util.Map.entry("vehicleConfidence", 0.7),
            java.util.Map.entry("frameIndex", 1),
            java.util.Map.entry("annotatedImageBase64", png)));
        when(aiProxy.recognizeImage(any(), anyString())).thenReturn(upstream);
        Tokens tokens = login();

        String response = mvc.perform(multipart("/api/ai/recognize/image")
                .file("file", new byte[]{1, 2, 3})
                .param("operation", "ENTRY")
                .header("Content-Type", "multipart/form-data")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Request-ID", requestId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-ID", requestId))
            .andExpect(jsonPath("$.evidenceId").isNotEmpty())
            .andExpect(jsonPath("$.evidenceId").value(org.hamcrest.Matchers.not("fastapi-temporary-id")))
            .andReturn().getResponse().getContentAsString();

        String evidenceId = objectMapper.readTree(response).get("evidenceId").asText();
        var stored = evidences.findById(evidenceId).orElseThrow();
        assert stored.getStatus() == vn.edu.parking.domain.EvidenceStatus.READY;
        assert stored.getPlateNumber().equals("59A112345");
        assert evidenceAssets.findByEvidenceIdAndDeletedAtIsNull(evidenceId).size() == 1;
    }

    @Test
    void parkingEntryTrustsSpringEvidenceAndRejectsReplay() throws Exception {
        String requestId = "consume-evidence-test";
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        byte[] upstream = objectMapper.writeValueAsBytes(java.util.Map.ofEntries(
            java.util.Map.entry("contractVersion", "1.0"), java.util.Map.entry("requestId", requestId),
            java.util.Map.entry("evidenceId", "fastapi-id"), java.util.Map.entry("modelVersion", "integration-model"),
            java.util.Map.entry("plateText", "77Z9-876.54"), java.util.Map.entry("vehicleType", "MOTORBIKE"),
            java.util.Map.entry("detectionConfidence", 0.9), java.util.Map.entry("ocrConfidence", 0.8),
            java.util.Map.entry("vehicleConfidence", 0.7), java.util.Map.entry("frameIndex", 1),
            java.util.Map.entry("annotatedImageBase64", png)));
        when(aiProxy.recognizeImage(any(), anyString())).thenReturn(upstream);
        Tokens tokens = login();
        String recognition = mvc.perform(multipart("/api/ai/recognize/image")
                .file("file", new byte[]{1}).param("operation", "ENTRY")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Request-ID", requestId))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String evidenceId = objectMapper.readTree(recognition).get("evidenceId").asText();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
            "plateNumber", "CLIENT-CANNOT-OVERRIDE", "vehicleType", "CAR",
            "vehicleEvidenceId", evidenceId));

        mvc.perform(post("/api/parking/entry")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber").value("77Z987654"))
            .andExpect(jsonPath("$.vehicleType").value("MOTORBIKE"));

        mvc.perform(post("/api/parking/entry")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        assert evidences.findById(evidenceId).orElseThrow().getStatus()
            == vn.edu.parking.domain.EvidenceStatus.CONSUMED;
    }

    @Test
    void onShiftGateStaffCanCorrectAiResultAndAuditBeforeAfterValues() throws Exception {
        String requestId = "manual-correction-test";
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        byte[] upstream = objectMapper.writeValueAsBytes(java.util.Map.ofEntries(
            java.util.Map.entry("contractVersion", "1.0"), java.util.Map.entry("requestId", requestId),
            java.util.Map.entry("evidenceId", "fastapi-correction-id"),
            java.util.Map.entry("modelVersion", "integration-model"),
            java.util.Map.entry("plateText", "59A1-123.45"), java.util.Map.entry("vehicleType", "MOTORBIKE"),
            java.util.Map.entry("detectionConfidence", 0.9), java.util.Map.entry("ocrConfidence", 0.8),
            java.util.Map.entry("vehicleConfidence", 0.7), java.util.Map.entry("frameIndex", 1),
            java.util.Map.entry("annotatedImageBase64", png)));
        when(aiProxy.recognizeImage(any(), anyString())).thenReturn(upstream);
        Tokens tokens = login();
        String recognition = mvc.perform(multipart("/api/ai/recognize/image")
                .file("file", new byte[]{1}).param("operation", "ENTRY")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Request-ID", requestId))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String vehicleEvidenceId = objectMapper.readTree(recognition).get("evidenceId").asText();

        var account = accounts.findByUsername("gate-01").orElseThrow();
        var desktopSession = sessions.findAll().stream().findFirst().orElseThrow();
        Instant now = Instant.now();
        shifts.save(new GateShiftAssignment(account, "Ca kiểm thử", now.minusSeconds(60),
            now.plusSeconds(3600), "Kiểm soát cổng", null));
        var face = new GateEvidence(UUID.randomUUID().toString(), EvidenceType.FACE,
            EvidenceOperation.ENTRY, EvidenceSourceKind.IMAGE, account, desktopSession,
            "face-correction-test", "integration-face", "a".repeat(64), now, now.plusSeconds(600));
        face.setFaceResult("upstream-face", null, "REVIEW", 0.31, 0.36, 0.30);
        face.markReady();
        face = evidences.save(face);

        String body = objectMapper.writeValueAsString(java.util.Map.ofEntries(
            java.util.Map.entry("plateNumber", "51H88888"),
            java.util.Map.entry("cardCode", "51H88888"),
            java.util.Map.entry("vehicleType", "CAR"),
            java.util.Map.entry("manualOverride", true),
            java.util.Map.entry("vehicleEvidenceId", vehicleEvidenceId),
            java.util.Map.entry("faceEvidenceId", face.getId()),
            java.util.Map.entry("manualOverrideReasonCode", "PLATE_CORRECTION"),
            java.util.Map.entry("manualOverrideReasonText", "Đã kiểm tra trực tiếp biển số trên ảnh camera")));

        mvc.perform(post("/api/parking/entry")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber").value("51H88888"))
            .andExpect(jsonPath("$.vehicleType").value("CAR"));

        var event = manualEvents.findAll().stream().findFirst().orElseThrow();
        assert event.getOriginalPlateNumber().equals("59A112345");
        assert event.getCorrectedPlateNumber().equals("51H88888");
        assert event.getOriginalVehicleType().equals("MOTORBIKE");
        assert event.getCorrectedVehicleType().equals("CAR");
        assert evidences.findById(vehicleEvidenceId).orElseThrow().getStatus()
            == vn.edu.parking.domain.EvidenceStatus.CONSUMED;
        assert evidences.findById(face.getId()).orElseThrow().getStatus()
            == vn.edu.parking.domain.EvidenceStatus.CONSUMED;
    }

    @Test
    void rotatesRefreshTokenAndRevokesSessionWhenOldTokenIsReused() throws Exception {
        Tokens initial = login();
        String refreshBody = objectMapper.writeValueAsString(
            java.util.Map.of("refreshToken", initial.refreshToken()));
        String rotatedJson = mvc.perform(post("/api/auth/refresh")
                .contentType("application/json").content(refreshBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        JsonNode rotated = objectMapper.readTree(rotatedJson);

        mvc.perform(post("/api/auth/refresh")
                .contentType("application/json").content(refreshBody))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/parking/slots")
                .header("Authorization", "Bearer " + rotated.get("accessToken").asText()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesDesktopSession() throws Exception {
        Tokens tokens = login();
        mvc.perform(get("/api/auth/session")
                .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("gate-01"))
            .andExpect(jsonPath("$.fullName").value("gate-01"));

        mvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/session")
                .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/parking/slots")
                .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isUnauthorized());
    }

    private Tokens login() throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "username", "gate-01", "password", PASSWORD, "deviceLabel", "test-gate"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.username").value("gate-01"))
            .andExpect(jsonPath("$.fullName").value("gate-01"))
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return new Tokens(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static String publicPem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static String privatePem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    }

    private record Tokens(String accessToken, String refreshToken) { }
}
