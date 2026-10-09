package vn.edu.parking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.PricingRuleRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.repository.DesktopSessionRepository;
import vn.edu.parking.repository.DesktopRefreshTokenRepository;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.service.AnprServiceClient;
import vn.edu.parking.service.AccountPasswordService;
import vn.edu.parking.service.AccountManagementService;
import vn.edu.parking.service.DesktopRefreshTokenService;
import vn.edu.parking.service.GateEvidencePolicy;
import vn.edu.parking.service.GateEvidenceRetentionService;
import vn.edu.parking.service.GateEvidenceStorage;
import vn.edu.parking.service.ResidentImageStorage;
import vn.edu.parking.web.ParkingApiController;
import vn.edu.parking.web.dto.SlotAssignRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.anyOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "test-manager", roles = "MANAGEMENT")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:parking-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "parking.security.jwt.enabled=true",
    "parking.anpr.service-token=",
    "parking.upload-dir=target/test-uploads",
    "parking.gate-evidence.directory=target/test-gate-evidence"
})
class ParkingFlowIntegrationTest {
    private static final java.security.KeyPair TEST_JWT_KEY_PAIR = createTestJwtKeyPair();

    @DynamicPropertySource
    static void configureTestJwtKeys(DynamicPropertyRegistry properties) {
        properties.add("parking.security.jwt.public-key-pem", () -> publicPem(TEST_JWT_KEY_PAIR.getPublic()));
        properties.add("parking.security.jwt.private-key-pem", () -> privatePem(TEST_JWT_KEY_PAIR.getPrivate()));
        properties.add("parking.security.jwt.issuer", () -> "https://parking.test");
        properties.add("parking.security.jwt.audience", () -> "parking-desktop");
        properties.add("parking.security.jwt.kid", () -> "integration-test-kid");
    }

    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;
    @Autowired ParkingSessionRepository parkingSessions;
    @Autowired ParkingSlotRepository parkingSlots;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired PricingRuleRepository prices;
    @Autowired SystemAccountRepository systemAccounts;
    @Autowired DesktopSessionRepository desktopSessions;
    @Autowired DesktopRefreshTokenRepository desktopRefreshTokens;
    @Autowired SecurityAuditRepository auditEvents;
    @Autowired GateEvidenceRepository gateEvidence;
    @Autowired DesktopRefreshTokenService desktopRefreshService;
    @Autowired GateEvidencePolicy gateEvidencePolicy;
    @Autowired GateEvidenceRetentionService gateEvidenceRetention;
    @Autowired GateEvidenceStorage gateEvidenceStorage;
    @MockitoBean AnprServiceClient mockedAnprService;
    @Autowired JwtEncoder jwtEncoder;
    @MockitoBean PasswordEncoder passwordEncoder;
    @Autowired AccountPasswordService accountPasswordService;
    @Autowired AccountManagementService accountManagement;
    @Autowired ResidentImageStorage residentImageStorage;
    @Autowired ParkingApiController parkingApiController;
    @Autowired vn.edu.parking.service.LoginAttemptThrottle loginAttemptThrottle;
    @Autowired Environment environment;
    @Autowired ObjectMapper objectMapper;

    private final PasswordEncoder testPasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final java.util.concurrent.atomic.AtomicReference<PasswordMatchPause> passwordMatchPause =
        new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void configurePasswordEncoderMock() {
        passwordMatchPause.set(null);
        when(passwordEncoder.encode(anyString()))
            .thenAnswer(invocation -> testPasswordEncoder.encode(invocation.getArgument(0)));
        when(passwordEncoder.matches(anyString(), anyString())).thenAnswer(invocation -> {
            String rawPassword = invocation.getArgument(0);
            boolean matches = testPasswordEncoder.matches(rawPassword, invocation.getArgument(1));
            PasswordMatchPause pause = passwordMatchPause.get();
            if (pause != null && rawPassword.equals(pause.rawPassword())
                    && Thread.currentThread().getName().equals(pause.threadName())
                    && passwordMatchPause.compareAndSet(pause, null)) {
                pause.reached().countDown();
                if (!pause.release().await(10, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("Timed out holding account mutation transaction");
            }
            return matches;
        });
    }

    @Test
    void webSessionConfigurationUsesSecureBoundedCookieDefaults() {
        assertEquals("30m", environment.getProperty("server.servlet.session.timeout"));
        assertEquals(true, environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class));
        assertEquals(true, environment.getProperty("server.servlet.session.cookie.secure", Boolean.class));
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"));
    }

    @Test
    void dailyRetentionDeletesEvidenceAfterThirtyDaysFromCaptureButRespectsFiniteHold() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant capturedAt = now.minus(java.time.Duration.ofDays(31));
        String expiredFile = gateEvidenceStorage.store(new byte[]{1}, 10);
        String heldFile = gateEvidenceStorage.store(new byte[]{2}, 10);
        GateEvidence expired = new GateEvidence(java.util.UUID.randomUUID().toString(), 1L,
            java.util.UUID.randomUUID().toString(), GateOperationType.ENTRY, GateEvidenceKind.AI_RECOGNITION,
            expiredFile, "image/jpeg", 1, "a".repeat(64), capturedAt,
            gateEvidencePolicy.expiresAt(capturedAt), now);
        GateEvidence held = new GateEvidence(java.util.UUID.randomUUID().toString(), 1L,
            java.util.UUID.randomUUID().toString(), GateOperationType.EXIT, GateEvidenceKind.FACE_VERIFICATION,
            heldFile, "image/jpeg", 1, "b".repeat(64), capturedAt,
            gateEvidencePolicy.expiresAt(capturedAt), now);
        held.approvePreservation(2L, now.plus(java.time.Duration.ofHours(1)), "Synthetic incident test", now);
        gateEvidence.saveAllAndFlush(java.util.List.of(expired, held));

        try {
            var result = gateEvidenceRetention.cleanup(now);

            assertEquals(new vn.edu.parking.service.GateEvidenceRetentionService.CleanupResult(1, 0, 0), result);
            assertFalse(gateEvidence.existsById(expired.getId()));
            assertTrue(gateEvidence.existsById(held.getId()));
            java.nio.file.Path evidenceRoot = java.nio.file.Path.of("target/test-gate-evidence");
            assertFalse(java.nio.file.Files.exists(evidenceRoot.resolve(expiredFile)));
            assertTrue(java.nio.file.Files.exists(evidenceRoot.resolve(heldFile)));
            assertTrue(auditEvents.findAll().stream().anyMatch(event ->
                event.getAction().equals("GATE_EVIDENCE_RETENTION_CLEANUP")
                    && event.getReason().equals("expiredDeleted=1;orphanDeleted=0;failed=0")
                    && event.getEvidenceReference() == null));
        } finally {
            gateEvidenceStorage.delete(heldFile);
            gateEvidence.deleteById(held.getId());
        }
    }

    @Test
    void imageRecognitionPersistsPrivateEvidenceAndScopesDownloadsToOwningDesktopSession() throws Exception {
        SystemAccount gateStaff = systemAccounts.saveAndFlush(new SystemAccount(
            "evidence-owner-" + java.util.UUID.randomUUID(), "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        var ownerGrant = desktopRefreshService.createSession(gateStaff, "evidence-owner-test");
        String ownerToken = createAccessToken(gateStaff, ownerGrant.sessionId());
        SystemAccount otherStaff = systemAccounts.saveAndFlush(new SystemAccount(
            "evidence-other-" + java.util.UUID.randomUUID(), "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        var otherGrant = desktopRefreshService.createSession(otherStaff, "evidence-other-test");
        String otherToken = createAccessToken(otherStaff, otherGrant.sessionId());
        String managementUsername = "evidence-management-" + java.util.UUID.randomUUID();
        String managementToken = createGateAccessToken(managementUsername, AccountRole.MANAGEMENT);
        String plate = ("96G" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8))
            .toUpperCase(java.util.Locale.ROOT);
        byte[] image = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        when(mockedAnprService.recognizeImage(any(byte[].class), anyString())).thenReturn(
            objectMapper.readTree("""
                {"plateText":"%s","vehicleType":"MOTORBIKE","detectionConfidence":0.9,
                 "ocrConfidence":0.8,"vehicleConfidence":0.7,"boundingBox":null,"frameIndex":0,
                 "annotatedImageBase64":"","message":"synthetic result"}
                """.formatted(plate)));

        String evidenceId = mvc.perform(multipart("/api/parking/anpr/recognize/image")
                .file(new MockMultipartFile("file", "plate.jpg", "image/jpeg", image))
                .param("operation", "ENTRY")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateText", is(plate)))
            .andExpect(jsonPath("$.evidenceId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(evidenceId).path("evidenceId").asText();
        GateEvidence saved = gateEvidence.findById(id).orElseThrow();
        assertEquals(gateStaff.getId(), saved.getOwnerAccountId());
        assertEquals(ownerGrant.sessionId(), saved.getDesktopSessionId());
        assertEquals(GateOperationType.ENTRY, saved.getOperationType());
        assertEquals(plate, saved.getRecognizedPlate());
        assertEquals(gateEvidencePolicy.expiresAt(saved.getCapturedAt()), saved.getExpiresAt());
        assertNotNull(saved.getSha256());
        assertNull(saved.getDerivedFileName());

        Long parkingSessionId = null;
        String exitEvidenceId = null;
        String expiredEvidenceId = null;
        String expiredEvidenceFilename = null;
        try {
        mvc.perform(post("/api/parking/entry")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(java.util.Map.of("plateNumber", plate))))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/parking/entry")
                .header("Authorization", "Bearer " + otherToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "plateNumber", plate, "evidenceId", id))))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/parking/entry")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "plateNumber", "59A999999", "evidenceId", id))))
            .andExpect(status().isBadRequest());

        String entryResponse = mvc.perform(post("/api/parking/entry")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "plateNumber", plate, "evidenceId", id))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber", is(plate)))
            .andReturn().getResponse().getContentAsString();
        parkingSessionId = objectMapper.readTree(entryResponse).path("sessionId").asLong();

            assertEquals(parkingSessionId, gateEvidence.findById(id).orElseThrow().getParkingSessionId());
            assertNotNull(gateEvidence.findById(id).orElseThrow().getConsumedAt());
            String entryEvidenceUrl = "/api/parking/sessions/" + parkingSessionId
                + "/operations/ENTRY/evidence/AI_RECOGNITION/" + id + "/image";
            mvc.perform(get(entryEvidenceUrl).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(image));
            mvc.perform(get(entryEvidenceUrl).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
            mvc.perform(get(entryEvidenceUrl).header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(image));
            mvc.perform(get("/api/parking/sessions/" + (parkingSessionId + 1)
                    + "/operations/ENTRY/evidence/AI_RECOGNITION/" + id + "/image")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isNotFound());
            mvc.perform(get("/api/parking/sessions/" + parkingSessionId
                    + "/operations/EXIT/evidence/AI_RECOGNITION/" + id + "/image")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isNotFound());
            mvc.perform(get("/api/parking/sessions/" + parkingSessionId
                    + "/operations/ENTRY/evidence/FACE_VERIFICATION/" + id + "/image")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isNotFound());
            mvc.perform(get(entryEvidenceUrl).with(anonymous()))
                .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/parking/evidence/" + id + "/image")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isForbidden());
            Long evidenceSessionId = parkingSessionId;
            assertTrue(auditEvents.findAll().stream().anyMatch(event ->
                event.getAction().equals("GATE_EVIDENCE_READ") && event.getOutcome().equals("SUCCESS")
                    && "PARKING_SESSION".equals(event.getTargetType())
                    && evidenceSessionId.toString().equals(event.getTargetReference())
                    && id.equals(event.getEvidenceReference()) && "ENTRY:ORIGINAL_IMAGE".equals(event.getReason())));
            var managementRead = auditEvents.findAll().stream()
                .filter(event -> event.getAction().equals("GATE_EVIDENCE_READ")
                    && managementUsername.equals(event.getActorUsername())
                    && id.equals(event.getEvidenceReference())
                    && event.getOutcome().equals("SUCCESS"))
                .findFirst().orElseThrow();
            assertEquals(systemAccounts.findByUsername(managementUsername).orElseThrow().getId(),
                managementRead.getActorAccountId());
            assertEquals("PARKING_SESSION", managementRead.getTargetType());
            assertEquals(evidenceSessionId.toString(), managementRead.getTargetReference());
            assertEquals("ENTRY:ORIGINAL_IMAGE", managementRead.getReason());
            assertNotNull(managementRead.getOccurredAt());
            assertFalse(managementRead.getOccurredAt().isAfter(java.time.Instant.now()));
            assertFalse(managementRead.getReason().contains(
                java.util.Base64.getEncoder().encodeToString(image)));
            var managementReadAttempts = auditEvents.findAll().stream()
                .filter(event -> event.getAction().equals("GATE_EVIDENCE_READ")
                    && managementUsername.equals(event.getActorUsername()))
                .toList();
            assertEquals(4, managementReadAttempts.size());
            assertTrue(managementReadAttempts.stream().allMatch(event ->
                event.getActorAccountId().equals(managementRead.getActorAccountId())
                    && event.getOccurredAt() != null
                    && !event.getOccurredAt().isAfter(java.time.Instant.now())
                    && "PARKING_SESSION".equals(event.getTargetType())
                    && id.equals(event.getEvidenceReference())
                    && event.getReason() != null
                    && ("ENTRY:ORIGINAL_IMAGE".equals(event.getReason())
                        || "EXIT:ORIGINAL_IMAGE".equals(event.getReason()))));
            expiredEvidenceFilename = gateEvidenceStorage.store(image, image.length + 1);
            java.time.Instant expiredCapturedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(31));
            expiredEvidenceId = java.util.UUID.randomUUID().toString();
            GateEvidence expiredEvidence = new GateEvidence(expiredEvidenceId, gateStaff.getId(),
                ownerGrant.sessionId(), GateOperationType.ENTRY, GateEvidenceKind.AI_RECOGNITION,
                expiredEvidenceFilename, "image/jpeg", image.length, "c".repeat(64), expiredCapturedAt,
                expiredCapturedAt.plus(java.time.Duration.ofDays(30)), java.time.Instant.now());
            expiredEvidence.bindToParkingSession(parkingSessionId);
            gateEvidence.saveAndFlush(expiredEvidence);
            mvc.perform(get("/api/parking/sessions/" + parkingSessionId
                    + "/operations/ENTRY/evidence/AI_RECOGNITION/" + expiredEvidenceId + "/image")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isNotFound());
            mvc.perform(post("/api/parking/entry")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "plateNumber", plate, "evidenceId", id))))
                .andExpect(status().isConflict());

            String exitRecognition = mvc.perform(multipart("/api/parking/anpr/recognize/image")
                    .file(new MockMultipartFile("file", "exit.jpg", "image/jpeg", image))
                    .param("operation", "EXIT")
                    .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            exitEvidenceId = objectMapper.readTree(exitRecognition).path("evidenceId").asText();
            String exitRequest = objectMapper.writeValueAsString(java.util.Map.of(
                "plateNumber", plate, "evidenceId", exitEvidenceId));
            mvc.perform(post("/api/parking/exit-preview")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content(exitRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PREVIEW")));
            assertEquals(parkingSessionId, gateEvidence.findById(exitEvidenceId).orElseThrow().getParkingSessionId());
            mvc.perform(post("/api/parking/exit-confirm")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content(exitRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
            assertNotNull(gateEvidence.findById(exitEvidenceId).orElseThrow().getConsumedAt());
            mvc.perform(post("/api/parking/exit-confirm")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content(exitRequest))
                .andExpect(status().isNotFound());

        } finally {
            if (expiredEvidenceId != null) {
                if (expiredEvidenceFilename != null) gateEvidenceStorage.delete(expiredEvidenceFilename);
                gateEvidence.deleteById(expiredEvidenceId);
            }
            if (exitEvidenceId != null) {
                gateEvidence.findById(exitEvidenceId).ifPresent(item -> {
                    try { gateEvidenceStorage.delete(item.getFileName()); }
                    catch (java.io.IOException ex) { throw new RuntimeException(ex); }
                });
                gateEvidence.deleteById(exitEvidenceId);
            }
            gateEvidenceStorage.delete(saved.getFileName());
            gateEvidence.deleteById(id);
            if (parkingSessionId != null) {
                parkingSlots.findFirstByCurrentSessionId(parkingSessionId).ifPresent(slot -> {
                    slot.setCurrentSession(null);
                    parkingSlots.save(slot);
                });
                parkingSessions.deleteById(parkingSessionId);
            }
        }
    }

    @Test
    void managementCanApproveFiniteEvidencePreservationWithReasonAndDeadlineAudit() throws Exception {
        SystemAccount management = systemAccounts.saveAndFlush(new SystemAccount(
            "evidence-manager-" + java.util.UUID.randomUUID(), "synthetic-test-hash", AccountRole.MANAGEMENT, false));
        var grant = desktopRefreshService.createSession(management, "evidence-management-test");
        String token = createAccessToken(management, grant.sessionId());
        String filename = gateEvidenceStorage.store(new byte[]{1}, 10);
        java.time.Instant capturedAt = java.time.Instant.now();
        ParkingSession session = new ParkingSession();
        session.setEntryPlate("91A" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        session.setEntryTime(java.time.LocalDateTime.now());
        session.setStatus(SessionStatus.OPEN);
        ParkingSession savedSession = parkingSessions.saveAndFlush(session);
        GateEvidence item = new GateEvidence(java.util.UUID.randomUUID().toString(), management.getId(),
            grant.sessionId(), GateOperationType.ENTRY, GateEvidenceKind.VEHICLE_IMAGE, filename,
            "image/jpeg", 1, "a".repeat(64), capturedAt, gateEvidencePolicy.expiresAt(capturedAt), capturedAt);
        item.bindToParkingSession(savedSession.getId());
        gateEvidence.saveAndFlush(item);
        java.time.Instant preserveUntil = java.time.Instant.now().plus(java.time.Duration.ofDays(2));
        java.time.Instant storedPreserveUntil = preserveUntil.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String reason = "Synthetic incident retention test";

        try {
            String preservationUrl = "/api/parking/sessions/" + savedSession.getId()
                + "/operations/ENTRY/evidence/" + item.getId() + "/preservation";
            mvc.perform(post("/api/parking/sessions/" + (savedSession.getId() + 1)
                    + "/operations/ENTRY/evidence/" + item.getId() + "/preservation")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(
                        java.util.Map.of("preserveUntil", preserveUntil, "reason", reason))))
                .andExpect(status().isNotFound());
            mvc.perform(post(preservationUrl)
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(
                        java.util.Map.of("preserveUntil", preserveUntil, "reason", reason))))
                .andExpect(status().isNoContent());

            GateEvidence preserved = gateEvidence.findById(item.getId()).orElseThrow();
            assertEquals(management.getId(), preserved.getPreserveApprovedBy());
            assertEquals(reason, preserved.getPreserveReason());
            assertEquals(storedPreserveUntil, preserved.getPreserveUntil());
            assertTrue(auditEvents.findAll().stream().anyMatch(event ->
                event.getAction().equals("GATE_EVIDENCE_HOLD_APPROVED")
                    && "PARKING_SESSION".equals(event.getTargetType())
                    && event.getTargetReference().equals(savedSession.getId().toString())
                    && item.getId().equals(event.getEvidenceReference())
                    && event.getReason().contains(reason) && event.getReason().contains(storedPreserveUntil.toString())));
        } finally {
            gateEvidenceStorage.delete(filename);
            gateEvidence.deleteById(item.getId());
            parkingSessions.deleteById(savedSession.getId());
        }
    }

    @Test
    void residentMediaIsAvailableToManagementButNotGateStaff() throws Exception {
        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(new QRCodeWriter().encode(
            "synthetic-resident-media", BarcodeFormat.QR_CODE, 128, 128), "png", imageBytes);
        String imagePath = residentImageStorage.save(new MockMultipartFile(
            "image", "resident.png", "image/png", imageBytes.toByteArray()), null);

        mvc.perform(get(imagePath))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/jpeg"));

        mvc.perform(get(imagePath).with(user("test-gate-media").roles("GATE_STAFF")))
            .andExpect(status().isForbidden());

        mvc.perform(get(imagePath).with(anonymous()))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void completesEntryPreviewAndExitFlow() throws Exception {
        String accessToken = createGateAccessToken("gate-entry-exit-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        String plate = ("97G" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8))
            .toUpperCase(java.util.Locale.ROOT);
        String entryEvidenceId = recognizeForTest(accessToken, "ENTRY", plate);
        String entryBody = objectMapper.writeValueAsString(java.util.Map.of(
            "plateNumber", plate, "vehicleType", "MOTORBIKE", "evidenceId", entryEvidenceId));

        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(entryBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber", is(plate)))
            .andExpect(jsonPath("$.status", is("OPEN")))
            .andExpect(jsonPath("$.warning", is(false)));

        String exitEvidenceId = recognizeForTest(accessToken, "EXIT", plate);
        String exitBody = objectMapper.writeValueAsString(java.util.Map.of(
            "plateNumber", plate, "evidenceId", exitEvidenceId));
        mvc.perform(post("/api/parking/exit-preview").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(exitBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PREVIEW")))
            .andExpect(jsonPath("$.fee", anyOf(is(5000), is(8000))));

        mvc.perform(post("/api/parking/exit-confirm").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(exitBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.fee", anyOf(is(5000), is(8000))));
    }

    @Test
    void residentEntryWithoutSelectedMemberFailsWithoutConsumingEvidenceOrCreatingSession() throws Exception {
        String accessToken = createGateAccessToken("resident-no-member-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        String plate = "93A" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        createResidentFixture(plate, false);
        String evidenceId = recognizeForTest(accessToken, "ENTRY", plate);
        assertResidentEntryRejected(accessToken, plate, null, evidenceId);
    }

    @Test
    void residentEntryRequiresActiveAuthorizedMemberAndOverrideForMissingReferenceImage() throws Exception {
        String accessToken = createGateAccessToken("resident-invalid-member-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        String plate = "94A" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ResidentFixture fixture = createResidentFixture(plate, false);

        String missingImageEvidence = recognizeForTest(accessToken, "ENTRY", plate);
        assertResidentEntryRejected(accessToken, plate, fixture.member().getId(), missingImageEvidence);

        fixture.member().setActive(false);
        members.saveAndFlush(fixture.member());
        String inactiveEvidence = recognizeForTest(accessToken, "ENTRY", plate);
        assertResidentEntryRejected(accessToken, plate, fixture.member().getId(), inactiveEvidence);

        FamilyMember unrelated = new FamilyMember();
        unrelated.setHousehold(fixture.member().getHousehold());
        unrelated.setFullName("Synthetic unrelated member");
        unrelated.setCitizenId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        unrelated = members.saveAndFlush(unrelated);
        String unauthorizedEvidence = recognizeForTest(accessToken, "ENTRY", plate);
        assertResidentEntryRejected(accessToken, plate, unrelated.getId(), unauthorizedEvidence);
    }

    @Test
    void monthlyPassKeepsOwnerAndMakesVisitFeeZero() throws Exception {
        String plate = "29A006131";
        ResidentFixture fixture = createResidentFixture(plate, true);
        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase(plate).orElseThrow();

        ParkingCard card = new ParkingCard();
        card.setCardCode("CARD030");
        card.setVehicle(vehicle);
        card.setStatus(CardStatus.ACTIVE);
        card.setPassType(PassType.MONTHLY);
        card.setValidFrom(LocalDate.now());
        card.setValidUntil(LocalDate.now().plusDays(29));
        card.setSubscriptionFee(BigDecimal.valueOf(120000));
        cards.save(card);

        String accessToken = createGateAccessToken("gate-monthly-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        String encodedFace = java.util.Base64.getEncoder().encodeToString(fixture.faceImage());
        when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
            {"decision":"PASS","similarity":0.8,"matchThreshold":0.363,
             "message":"synthetic verification","realtimeImageBase64":"%s"}
            """.formatted(encodedFace)));
        String entryEvidenceId = recognizeForTest(accessToken, "ENTRY", plate);
        String entryFaceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"" + plate
                    + "\",\"familyMemberId\":" + fixture.member().getId()
                    + ",\"evidenceId\":\"" + entryEvidenceId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision", is("PASS")))
            .andReturn().getResponse().getContentAsString();
        String entryFaceId = objectMapper.readTree(entryFaceResponse).path("evidenceId").asText();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
            "plateNumber", "29A0-061.31", "cardCode", "CARD030", "vehicleType", "MOTORBIKE",
            "familyMemberId", fixture.member().getId(), "evidenceId", entryEvidenceId,
            "faceEvidenceId", entryFaceId));
        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerName", is("Synthetic override member")))
            .andExpect(jsonPath("$.warning", is(false)));
        String exitEvidenceId = recognizeForTest(accessToken, "EXIT", plate);
        String exitBody = objectMapper.writeValueAsString(java.util.Map.of(
            "plateNumber", "29A0-061.31", "cardCode", "CARD030", "evidenceId", exitEvidenceId));
        mvc.perform(post("/api/parking/exit-preview").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(exitBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fee", is(0)));
    }

    @Test
    void residentLookupDoesNotExposeOwnerPhoneToGateClients() throws Exception {
        String plate = "88A" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName("Synthetic resident");
        vehicle.setOwnerPhone("0900000000");
        vehicle.setApartmentNumber("T1-0101");
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicles.saveAndFlush(vehicle);

        mvc.perform(get("/api/parking/lookup").param("plate", plate))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registered", is(true)))
            .andExpect(jsonPath("$.ownerName", is("Synthetic resident")))
            .andExpect(jsonPath("$.ownerPhone", is("")));
    }

    @Test
    void operationalSlotApiOmitsResidentContactFieldsForBothSignedRoles() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String plate = "89A" + suffix.substring(0, 8);
        String slotCode = "SEC-" + suffix.substring(0, 12);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName("Slot contact sentinel");
        vehicle.setOwnerPhone("0907654321");
        vehicle.setApartmentNumber("T9-0909");
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicle = vehicles.saveAndFlush(vehicle);

        ParkingSlot slot = new ParkingSlot();
        slot.setSlotCode(slotCode);
        slot.setFloor("Tầng test");
        slot.setZoneName("Khu test");
        slot.setAllowedVehicleType(VehicleType.MOTORBIKE);
        slot.setAssignedVehicle(vehicle);
        parkingSlots.saveAndFlush(slot);

        for (AccountRole role : new AccountRole[]{AccountRole.GATE_STAFF, AccountRole.MANAGEMENT}) {
            String accessToken = createGateAccessToken("slot-view-" + role + "-" + suffix, role);
            String response = mvc.perform(get("/api/parking/slots")
                    .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode slotPayload = null;
            for (com.fasterxml.jackson.databind.JsonNode item : objectMapper.readTree(response)) {
                if (slotCode.equals(item.path("slotCode").asText())) {
                    slotPayload = item;
                    break;
                }
            }

            assertNotNull(slotPayload);
            assertEquals(plate, slotPayload.path("assignedPlate").asText());
            assertEquals("RESERVED_EMPTY", slotPayload.path("status").asText());
            assertFalse(slotPayload.has("assignedOwnerName"));
            assertFalse(slotPayload.has("assignedOwnerPhone"));
            assertFalse(slotPayload.has("assignedApartment"));
            assertFalse(response.contains("Slot contact sentinel"));
            assertFalse(response.contains("0907654321"));
            assertFalse(response.contains("T9-0909"));
        }
    }

    @Test
    void rendersAllAdminPages() throws Exception {
        for (String path : new String[]{"/", "/registrations", "/households", "/vehicles", "/cards", "/pricing", "/settings", "/slots", "/sessions"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    @WithAnonymousUser
    void anonymousCanUseOnlyMinimalHealthAmongProtectedApiRoutes() throws Exception {
        mvc.perform(get("/api/parking/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UP")))
            .andExpect(jsonPath("$.database").doesNotExist());
        mvc.perform(get("/api/parking/open")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void anonymousJsonAdminDataRequestReturnsJsonUnauthorizedInsteadOfLoginHtml() throws Exception {
        mvc.perform(get("/admin-data/vehicles/1").accept("application/json"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.error", is("unauthorized")));
    }

    @Test
    void managementCanCreateAccountWithOneTimeTemporaryPasswordAndSafeReadModel() throws Exception {
        String username = "created-gate-" + java.util.UUID.randomUUID();
        String body = "{\"username\":\"" + username + "\",\"role\":\"GATE_STAFF\"}";

        mvc.perform(post("/account-admin/accounts").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.account.username", is(username)))
            .andExpect(jsonPath("$.account.role", is("GATE_STAFF")))
            .andExpect(jsonPath("$.account.mustChangePassword", is(true)))
            .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        SystemAccount created = systemAccounts.findByUsername(username).orElseThrow();
        assertTrue(created.isMustChangePassword());
        assertTrue(created.getPasswordHash().startsWith("{bcrypt}"));

        mvc.perform(get("/account-admin/accounts/{username}", username))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username", is(username)))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        mvc.perform(post("/account-admin/accounts").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error", is("account_exists")));

        mvc.perform(get("/account-admin/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events").isArray());
        assertTrue(auditEvents.findAll().stream().anyMatch(event -> event.getAction().equals("ACCOUNT_CREATE")
            && created.getId().toString().equals(event.getTargetReference())
            && event.getOutcome().equals("SUCCESS")));

        var reset = mvc.perform(post("/account-admin/accounts/{username}/password-reset", username).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword", is(true)))
            .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
        String temporaryPassword = com.jayway.jsonpath.JsonPath.read(
            reset.getResponse().getContentAsString(), "$.temporaryPassword");
        assertTrue(passwordEncoder.matches(temporaryPassword,
            systemAccounts.findByUsername(username).orElseThrow().getPasswordHash()));

        mvc.perform(put("/account-admin/accounts/{username}/role", username).with(csrf())
                .contentType("application/json").content("{\"role\":\"GATE_STAFF\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/account-admin/accounts/{username}/disable", username).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/account-admin/accounts/{username}", username))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled", is(false)));
        mvc.perform(post("/account-admin/accounts/{username}/enable", username).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/account-admin/accounts/{username}", username))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    @WithMockUser(username = "test-gate-admin-api", roles = "GATE_STAFF")
    void gateStaffCannotReadOrMutateAccountAdministration() throws Exception {
        mvc.perform(get("/account-admin/accounts")).andExpect(status().isForbidden());
        mvc.perform(get("/account-admin/audit")).andExpect(status().isForbidden());
        mvc.perform(post("/account-admin/accounts/example/disable").with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void openSessionApiReturnsAnAllowlistedSummaryInsteadOfJpaEntities() throws Exception {
        String plate = "59A" + String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
        String accessToken = createGateAccessToken("gate-open-session-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        String evidenceId = recognizeForTest(accessToken, "ENTRY", plate);
        String body = objectMapper.writeValueAsString(java.util.Map.of("plateNumber", plate, "cardCode", "",
            "vehicleType", "MOTORBIKE", "manualOverride", false, "evidenceId", evidenceId));
        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(body))
            .andExpect(status().isOk());

        mvc.perform(get("/api/parking/open").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sessionId").exists())
            .andExpect(jsonPath("$[0].plateNumber").exists())
            .andExpect(jsonPath("$[0].vehicleType").exists())
            .andExpect(jsonPath("$[0].entryTime").exists())
            .andExpect(jsonPath("$[0].id").doesNotExist())
            .andExpect(jsonPath("$[0].manualOverride").doesNotExist())
            .andExpect(jsonPath("$[0].entryFaceImagePath").doesNotExist())
            .andExpect(jsonPath("$[0].parkingCard").doesNotExist());
    }

    @Test
    @WithMockUser(username = "test-gate", roles = "GATE_STAFF")
    void gateStaffCanReadOperationalSlotsButCannotManageSlotsOrOpenManagementPages() throws Exception {
        mvc.perform(get("/api/parking/slots")).andExpect(status().isOk());
        mvc.perform(post("/api/parking/slots/batch-generate").with(csrf())
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/parking/vehicles-unassigned")).andExpect(status().isForbidden());
        mvc.perform(get("/")).andExpect(status().isForbidden());
        mvc.perform(get("/account/password")).andExpect(status().isOk());
    }

    @Test
    void unassignedParkingListsOmitResidentNameAndApartment() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String plate = "92A" + suffix.substring(0, 8);
        Household household = new Household();
        household.setHouseholdCode("PII-" + suffix.substring(8, 20));
        household.setApartmentNumber("UNIT-" + suffix.substring(20, 28));
        household = households.saveAndFlush(household);
        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName("Synthetic unassigned PII member");
        member.setCitizenId(suffix.substring(16, 28));
        member = members.saveAndFlush(member);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(member.getFullName());
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(member);
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicle = vehicles.saveAndFlush(vehicle);
        ParkingSession session = new ParkingSession();
        session.setEntryPlate(plate);
        session.setVehicle(vehicle);
        session.setEntryTime(java.time.LocalDateTime.now());
        session.setStatus(SessionStatus.OPEN);
        session = parkingSessions.saveAndFlush(session);
        String managementToken = createGateAccessToken("unassigned-pii-" + java.util.UUID.randomUUID(),
            AccountRole.MANAGEMENT);

        try {
            String recentBody = mvc.perform(get("/api/parking/recent-unassigned")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode recentSessions = objectMapper.readTree(recentBody);
            com.fasterxml.jackson.databind.JsonNode recentSession =
                java.util.stream.StreamSupport.stream(recentSessions.spliterator(), false)
                    .filter(item -> plate.equals(item.path("plateNumber").asText()))
                    .findFirst().orElseThrow();
            assertFalse(recentSession.has("ownerName"));
            assertTrue(recentSession.has("slotCode"));

            String vehiclesBody = mvc.perform(get("/api/parking/vehicles-unassigned")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode unassignedVehicles = objectMapper.readTree(vehiclesBody);
            com.fasterxml.jackson.databind.JsonNode unassignedVehicle =
                java.util.stream.StreamSupport.stream(unassignedVehicles.spliterator(), false)
                    .filter(item -> plate.equals(item.path("plateNumber").asText()))
                    .findFirst().orElseThrow();
            assertFalse(unassignedVehicle.has("ownerName"));
            assertFalse(unassignedVehicle.has("apartmentNumber"));
        } finally {
            parkingSessions.deleteById(session.getId());
            vehicles.deleteById(vehicle.getId());
            members.deleteById(member.getId());
            households.deleteById(household.getId());
        }
    }

    @Test
    @WithMockUser(username = "test-gate-method", roles = "GATE_STAFF")
    void methodSecurityAlsoBlocksDirectManagementControllerInvocation() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> parkingApiController.vehiclesUnassigned());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> parkingApiController.assignVehicle(1L, new SlotAssignRequest(null)));
    }

    @Test
    @WithMockUser(username = "first-login", authorities = "ROLE_PASSWORD_CHANGE_REQUIRED")
    void forcedPasswordChangePageIsAvailableToRestrictedAccounts() throws Exception {
        mvc.perform(get("/account/password"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Current password")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        mvc.perform(get("/")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void roleChangeInvalidatesAnExistingWebSessionBeforeItsAuthoritiesAreUsed() throws Exception {
        String username = "web-session-" + java.util.UUID.randomUUID();
        systemAccounts.saveAndFlush(new SystemAccount(username, passwordEncoder.encode("synthetic password"),
            AccountRole.GATE_STAFF, false));

        var login = mvc.perform(post("/login").with(csrf())
                .param("username", username).param("password", "synthetic password"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertTrue(session != null);

        accountManagement.changeRole(username, AccountRole.MANAGEMENT);

        mvc.perform(get("/account/password").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithAnonymousUser
    void webLoginIsAuditedWithActorButNoCredentials() throws Exception {
        String username = "web-audit-" + java.util.UUID.randomUUID();
        systemAccounts.saveAndFlush(new SystemAccount(username, passwordEncoder.encode("synthetic password"),
            AccountRole.GATE_STAFF, false));

        var login = mvc.perform(post("/login").with(csrf())
                .param("username", username).param("password", "synthetic password"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertTrue(session != null);
        assertTrue(auditEvents.findAll().stream().anyMatch(event -> event.getAction().equals("LOGIN_SUCCESS")
            && username.equals(event.getActorUsername()) && event.getOutcome().equals("SUCCESS")));

    }

    @Test
    void authenticatedWebLogoutIsAudited() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertTrue(auditEvents.findAll().stream().anyMatch(event -> event.getAction().equals("LOGOUT")
            && event.getTargetType().equals("WEB_SESSION")
            && "test-manager".equals(event.getActorUsername()) && event.getOutcome().equals("SUCCESS")));
    }

    @Test
    @WithAnonymousUser
    void failedWebLoginIsAuditedWithGenericReason() throws Exception {
        String username = "web-failure-" + java.util.UUID.randomUUID();

        mvc.perform(post("/login").with(csrf()).param("username", username).param("password", "synthetic password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));

        assertTrue(auditEvents.findAll().stream().anyMatch(event -> event.getAction().equals("LOGIN_FAILURE")
            && username.equals(event.getTargetReference()) && event.getOutcome().equals("FAILURE")
            && "INVALID_CREDENTIALS".equals(event.getReason())));
    }

    @Test
    @WithAnonymousUser
    void webAndDesktopLoginEndpointsLimitAttemptsPerRemoteAddress() throws Exception {
        String webIp = "192.0.2.41";
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/login").with(request -> { request.setRemoteAddr(webIp); return request; }).with(csrf())
                    .param("username", "missing-web-user").param("password", "invalid"))
                .andExpect(status().is3xxRedirection());
        }
        assertTrue(loginAttemptThrottle.isBlocked(webIp));
        mvc.perform(post("/login").with(request -> { request.setRemoteAddr(webIp); return request; }).with(csrf())
                .param("username", "missing-web-user").param("password", "invalid"))
            .andExpect(status().isTooManyRequests());

        String desktopIp = "192.0.2.42";
        String invalidLogin = "{\"username\":\"missing-desktop-user\",\"password\":\"invalid\"}";
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr(desktopIp); return request; })
                    .contentType("application/json").content(invalidLogin))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr(desktopIp); return request; })
                .contentType("application/json").content(invalidLogin))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void databaseLockProtectsFinalManagementAccountDuringDisable() {
        String suffix = java.util.UUID.randomUUID().toString();
        String managerName = "manager-" + suffix;
        String gateName = "gate-" + suffix;
        var previouslyEnabledManagers = systemAccounts.findAll().stream()
            .filter(account -> account.isEnabled() && account.getRole() == AccountRole.MANAGEMENT).toList();
        previouslyEnabledManagers.forEach(account -> {
            account.setEnabled(false);
            systemAccounts.save(account);
        });
        SystemAccount manager = systemAccounts.saveAndFlush(
            new SystemAccount(managerName, "synthetic-test-hash", AccountRole.MANAGEMENT, false));
        SystemAccount gate = systemAccounts.saveAndFlush(
            new SystemAccount(gateName, "synthetic-test-hash", AccountRole.GATE_STAFF, false));

        try {
            assertThrows(vn.edu.parking.service.LastManagementAccountException.class,
                () -> accountManagement.disable(managerName));
            accountManagement.disable(gateName);

            assertFalse(systemAccounts.findByUsername(gateName).orElseThrow().isEnabled());
            assertEquals(0, systemAccounts.findByUsername(managerName).orElseThrow().getSecurityVersion());
        } finally {
            systemAccounts.delete(gate);
            systemAccounts.delete(manager);
            previouslyEnabledManagers.forEach(account -> {
                account.setEnabled(true);
                systemAccounts.save(account);
            });
        }
    }

    @Test
    void persistsDesktopSessionAndOnlyTheRefreshDigest() {
        String username = "session-" + java.util.UUID.randomUUID();
        SystemAccount account = systemAccounts.saveAndFlush(
            new SystemAccount(username, "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        java.time.Instant now = java.time.Instant.now();
        DesktopSession session = desktopSessions.saveAndFlush(new DesktopSession(
            java.util.UUID.randomUUID().toString(), account, now, now.plusSeconds(36_000), "test-device"));
        String digest = "b".repeat(64);
        desktopRefreshTokens.saveAndFlush(new DesktopRefreshToken(session, digest, now, now.plusSeconds(36_000)));

        assertTrue(desktopSessions.findById(session.getSessionId()).orElseThrow().isActiveAt(now.plusSeconds(1)));
        assertEquals(digest, desktopRefreshTokens.findAll().stream()
            .filter(token -> token.getSession().getSessionId().equals(session.getSessionId()))
            .findFirst().orElseThrow().getTokenHash());
        assertFalse(desktopSessions.findById(session.getSessionId()).orElseThrow()
            .isActiveAt(now.plus(java.time.Duration.ofHours(2)).plusSeconds(1)));
    }

    @Test
    void rotatesDatabaseRefreshTokenAndRevokesSessionOnReuse() {
        String username = "rotate-" + java.util.UUID.randomUUID();
        SystemAccount account = systemAccounts.saveAndFlush(
            new SystemAccount(username, "synthetic-test-hash", AccountRole.GATE_STAFF, false));

        var issued = desktopRefreshService.createSession(account, "rotation-test");
        var rotated = desktopRefreshService.rotate(issued.refreshToken());

        assertTrue(rotated.rotated());
        assertTrue(desktopRefreshService.touchIfSessionActive(rotated.sessionId(), account.getId(),
            account.getSecurityVersion(), java.time.Instant.now()));
        var replay = desktopRefreshService.rotate(issued.refreshToken());
        assertTrue(replay.reuseDetected());
        assertFalse(desktopRefreshService.touchIfSessionActive(rotated.sessionId(), account.getId(),
            account.getSecurityVersion(), java.time.Instant.now()));
    }

    @Test
    void supportsPerSessionLogoutAndAccountWideRevocation() {
        String username = "logout-" + java.util.UUID.randomUUID();
        SystemAccount account = systemAccounts.saveAndFlush(
            new SystemAccount(username, "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        var first = desktopRefreshService.createSession(account, "desktop-one");
        var second = desktopRefreshService.createSession(account, "desktop-two");

        assertTrue(desktopRefreshService.logout(first.sessionId(), account.getId()));
        assertFalse(desktopRefreshService.touchIfSessionActive(first.sessionId(), account.getId(), 0,
            java.time.Instant.now()));
        assertTrue(desktopRefreshService.touchIfSessionActive(second.sessionId(), account.getId(), 0,
            java.time.Instant.now()));

        desktopRefreshService.revokeAllForAccount(account.getId());
        assertFalse(desktopRefreshService.touchIfSessionActive(second.sessionId(), account.getId(), 0,
            java.time.Instant.now()));
    }

    @Test
    void accountDisableMarksDesktopSessionRowsRevokedInTheAccountTransaction() {
        String username = "disable-session-" + java.util.UUID.randomUUID();
        SystemAccount account = systemAccounts.saveAndFlush(
            new SystemAccount(username, "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        var first = desktopRefreshService.createSession(account, "disable-test-one");
        var second = desktopRefreshService.createSession(account, "disable-test-two");

        accountManagement.disable(username);

        assertFalse(systemAccounts.findById(account.getId()).orElseThrow().isEnabled());
        assertTrue(desktopSessions.findById(first.sessionId()).orElseThrow().getRevokedAt() != null);
        assertTrue(desktopSessions.findById(second.sessionId()).orElseThrow().getRevokedAt() != null);
    }

    @Test
    void concurrentRefreshReuseIsSingleUseAndRevokesTheSession() throws Exception {
        String username = "concurrent-refresh-" + java.util.UUID.randomUUID();
        SystemAccount account = systemAccounts.saveAndFlush(
            new SystemAccount(username, "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        var issued = desktopRefreshService.createSession(account, "concurrency-test");
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return desktopRefreshService.rotate(issued.refreshToken());
            });
            var second = executor.submit(() -> {
                start.await();
                return desktopRefreshService.rotate(issued.refreshToken());
            });
            start.countDown();
            var firstResult = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var secondResult = second.get(10, java.util.concurrent.TimeUnit.SECONDS);

            assertEquals(1, (firstResult.rotated() ? 1 : 0) + (secondResult.rotated() ? 1 : 0));
            assertTrue(firstResult.reuseDetected() || secondResult.reuseDetected());
            assertFalse(desktopRefreshService.touchIfSessionActive(issued.sessionId(), account.getId(), 0,
                java.time.Instant.now()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void selfPasswordChangeSerializesWithManagementResetWithoutLosingVersionOrRevocation() throws Exception {
        String username = createConcurrentMutationAccount();
        var session = desktopRefreshService.createSession(systemAccounts.findByUsername(username).orElseThrow(),
            "self-change-reset-test");
        java.util.concurrent.atomic.AtomicReference<String> resetPassword = new java.util.concurrent.atomic.AtomicReference<>();

        runConcurrentSelfPasswordChange(username, "initial concurrency password",
            () -> resetPassword.set(accountManagement.resetPassword(username)));

        SystemAccount account = systemAccounts.findByUsername(username).orElseThrow();
        assertEquals(2, account.getSecurityVersion());
        assertTrue(account.isMustChangePassword());
        assertTrue(passwordEncoder.matches(resetPassword.get(), account.getPasswordHash()));
        assertFalse(passwordEncoder.matches("self changed concurrency password", account.getPasswordHash()));
        assertNotNull(desktopSessions.findById(session.sessionId()).orElseThrow().getRevokedAt());
    }

    @Test
    void selfPasswordChangeSerializesWithDisableWithoutReenablingTheAccount() throws Exception {
        String username = createConcurrentMutationAccount();
        var session = desktopRefreshService.createSession(systemAccounts.findByUsername(username).orElseThrow(),
            "self-change-disable-test");

        runConcurrentSelfPasswordChange(username, "initial concurrency password",
            () -> accountManagement.disable(username));

        SystemAccount account = systemAccounts.findByUsername(username).orElseThrow();
        assertEquals(2, account.getSecurityVersion());
        assertFalse(account.isEnabled());
        assertTrue(passwordEncoder.matches("self changed concurrency password", account.getPasswordHash()));
        assertNotNull(desktopSessions.findById(session.sessionId()).orElseThrow().getRevokedAt());
    }

    @Test
    void selfPasswordChangeSerializesWithRoleChangeWithoutLosingEitherMutation() throws Exception {
        String username = createConcurrentMutationAccount();
        var session = desktopRefreshService.createSession(systemAccounts.findByUsername(username).orElseThrow(),
            "self-change-role-test");

        runConcurrentSelfPasswordChange(username, "initial concurrency password",
            () -> accountManagement.changeRole(username, AccountRole.MANAGEMENT));

        SystemAccount account = systemAccounts.findByUsername(username).orElseThrow();
        assertEquals(2, account.getSecurityVersion());
        assertEquals(AccountRole.MANAGEMENT, account.getRole());
        assertTrue(passwordEncoder.matches("self changed concurrency password", account.getPasswordHash()));
        assertNotNull(desktopSessions.findById(session.sessionId()).orElseThrow().getRevokedAt());
    }

    @Test
    void signedBearerUsesDatabaseRoleAndRevokedSessionIsRejected() throws Exception {
        String username = "bearer-" + java.util.UUID.randomUUID();
        SystemAccount account = systemAccounts.saveAndFlush(
            new SystemAccount(username, "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        var session = desktopRefreshService.createSession(account, "bearer-test");
        String accessToken = createAccessToken(account, session.sessionId());

        mvc.perform(get("/api/parking/open").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());
        mvc.perform(get("/api/parking/vehicles-unassigned").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isForbidden());

        desktopRefreshService.logout(session.sessionId(), account.getId());
        mvc.perform(get("/api/parking/open").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anprImageProxyRequiresDesktopJwtAndFailsClosedWithoutInternalCredential() throws Exception {
        String path = "/api/parking/anpr/recognize/image";
        MockMultipartFile validImage = new MockMultipartFile("file", "plate.jpg", "image/jpeg",
            new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});
        mvc.perform(multipart(path).file(validImage).param("operation", "ENTRY").with(anonymous()))
            .andExpect(status().isUnauthorized());

        SystemAccount gate = systemAccounts.saveAndFlush(new SystemAccount(
            "anpr-gate-" + java.util.UUID.randomUUID(), "synthetic-test-hash", AccountRole.GATE_STAFF, false));
        var session = desktopRefreshService.createSession(gate, "anpr-proxy-test");
        String accessToken = createAccessToken(gate, session.sessionId());
        when(mockedAnprService.recognizeImage(any(byte[].class), anyString())).thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "ANPR service is not configured"));
        mvc.perform(multipart(path).file(validImage).param("operation", "ENTRY")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isServiceUnavailable());

        MockMultipartFile forgedContent = new MockMultipartFile("file", "plate.jpg", "image/jpeg",
            "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart(path).file(forgedContent).param("operation", "ENTRY")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void videoRecognitionUsesAuthenticatedSpringProxyAndPersistsPrivateFrameEvidence() throws Exception {
        String accessToken = createGateAccessToken("video-gate-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        String managementToken = createGateAccessToken("video-management-" + java.util.UUID.randomUUID(),
            AccountRole.MANAGEMENT);
        byte[] jpeg;
        try (var output = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", output);
            jpeg = output.toByteArray();
        }
        String encoded = java.util.Base64.getEncoder().encodeToString(jpeg);
        when(mockedAnprService.recognizeVideo(any(byte[].class), anyString())).thenReturn(objectMapper.readTree("""
            {"plateText":"59A112345","vehicleType":"MOTORBIKE","detectionConfidence":0.9,
             "ocrConfidence":0.8,"vehicleConfidence":0.7,"boundingBox":null,"frameIndex":3,
             "annotatedImageBase64":"%s","message":"synthetic video result"}
            """.formatted(encoded)));
        byte[] mp4 = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

        mvc.perform(multipart("/api/parking/anpr/recognize/video")
                .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4))
                .param("operation", "ENTRY").with(anonymous()))
            .andExpect(status().isUnauthorized());

        String body = mvc.perform(multipart("/api/parking/anpr/recognize/video")
                .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4))
                .param("operation", "ENTRY")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateText", is("59A112345")))
            .andExpect(jsonPath("$.evidenceId").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String evidenceId = objectMapper.readTree(body).path("evidenceId").asText();
        GateEvidence saved = gateEvidence.findById(evidenceId).orElseThrow();
        try {
            assertEquals(GateEvidenceKind.AI_RECOGNITION, saved.getEvidenceKind());
            assertEquals("fastapi-video", saved.getSourceService());
            assertEquals("59A112345", saved.getRecognizedPlate());
            assertEquals(3, saved.getFrameIndex());
            assertEquals("image/jpeg", saved.getMediaType());
            assertTrue(saved.getCapturedAt().isBefore(java.time.Instant.now().plusSeconds(1)));
            assertNotNull(saved.getRecognitionValidUntil());
            mvc.perform(get("/api/parking/evidence/{id}/image", evidenceId)
                    .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/parking/sessions/9223372036854775807/operations/ENTRY/evidence/AI_RECOGNITION/{id}/image",
                    evidenceId)
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isNotFound());
        } finally {
            gateEvidenceStorage.delete(saved.getFileName());
            gateEvidence.deleteById(evidenceId);
        }
    }

    @Test
    void residentFaceVerificationUsesServerRegistrationImageAndBindsEntryAndExitEvidence() throws Exception {
        String gateUsername = "resident-face-gate-" + java.util.UUID.randomUUID();
        String accessToken = createGateAccessToken(gateUsername, AccountRole.GATE_STAFF);
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String plate = "77R" + suffix.substring(0, 8);
        byte[] faceImage;
        try (var output = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", output);
            faceImage = output.toByteArray();
        }
        String registrationReference = residentImageStorage.save(
            new MockMultipartFile("registration", "registered.jpg", "image/jpeg", faceImage), null);

        Household household = new Household();
        household.setHouseholdCode("FACE-" + suffix.substring(0, 12));
        household.setApartmentNumber("TEST-" + suffix.substring(12, 16));
        household = households.saveAndFlush(household);
        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName("Synthetic face member");
        member.setCitizenId(suffix.substring(0, 12));
        member.setRegistrationFaceImagePath(registrationReference);
        member = members.saveAndFlush(member);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(member.getFullName());
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(member);
        vehicle.setAuthorizedMembers(java.util.Set.of(member));
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicle = vehicles.saveAndFlush(vehicle);

        String encodedFace = java.util.Base64.getEncoder().encodeToString(faceImage);
        when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
            {"decision":"PASS","similarity":0.8,"matchThreshold":0.363,
             "message":"synthetic verification","realtimeImageBase64":"%s"}
            """.formatted(encodedFace)));

        String entryRecognitionId = null;
        String entryFaceId = null;
        String exitRecognitionId = null;
        String exitFaceId = null;
        Long parkingSessionId = null;
        Long vehicleId = vehicle.getId();
        Long memberId = member.getId();
        Long householdId = household.getId();
        try {
            entryRecognitionId = recognizeForTest(accessToken, "ENTRY", plate);
            String entryFaceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                     .contentType("application/json")
                     .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"" + plate
                        + "\",\"familyMemberId\":" + memberId + ",\"evidenceId\":\""
                        + entryRecognitionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("PASS")))
                .andReturn().getResponse().getContentAsString();
            entryFaceId = objectMapper.readTree(entryFaceResponse).path("evidenceId").asText();
            GateEvidence pendingEntryFace = gateEvidence.findById(entryFaceId).orElseThrow();
            assertEquals(memberId, pendingEntryFace.getFamilyMemberId());
            assertEquals(GateOperationType.ENTRY, pendingEntryFace.getOperationType());
            assertNull(pendingEntryFace.getParkingSessionId());

            String entryBody = mvc.perform(post("/api/parking/entry")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"plateNumber\":\"" + plate + "\",\"familyMemberId\":" + memberId
                        + ",\"evidenceId\":\"" + entryRecognitionId + "\",\"faceEvidenceId\":\""
                        + entryFaceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            parkingSessionId = objectMapper.readTree(entryBody).path("sessionId").asLong();
            var openSession = parkingSessions.findById(parkingSessionId).orElseThrow();
            assertEquals(memberId, openSession.getEntryMember().getId());
            assertTrue(openSession.isEntryFaceVerified());
            assertEquals(0.8, openSession.getEntryFaceSimilarity());
            GateEvidence boundEntryFace = gateEvidence.findById(entryFaceId).orElseThrow();
            assertEquals(parkingSessionId, boundEntryFace.getParkingSessionId());
            assertNotNull(boundEntryFace.getConsumedAt());

            exitRecognitionId = recognizeForTest(accessToken, "EXIT", plate);
            String exitFaceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                     .contentType("application/json")
                     .content("{\"operation\":\"EXIT\",\"plateNumber\":\"" + plate
                        + "\",\"familyMemberId\":" + memberId + ",\"evidenceId\":\""
                        + exitRecognitionId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            exitFaceId = objectMapper.readTree(exitFaceResponse).path("evidenceId").asText();
            GateEvidence pendingExitFace = gateEvidence.findById(exitFaceId).orElseThrow();
            assertEquals(parkingSessionId, pendingExitFace.getParkingSessionId());
            assertEquals(memberId, pendingExitFace.getFamilyMemberId());

            String exitRequest = "{\"plateNumber\":\"" + plate + "\",\"familyMemberId\":" + memberId
                + ",\"evidenceId\":\"" + exitRecognitionId + "\",\"faceEvidenceId\":\""
                + exitFaceId + "\"}";
            mvc.perform(post("/api/parking/exit-preview").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitRequest))
                .andExpect(status().isOk());
            mvc.perform(post("/api/parking/exit-confirm").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitRequest))
                .andExpect(status().isOk());

            var completedSession = parkingSessions.findById(parkingSessionId).orElseThrow();
            assertEquals(SessionStatus.COMPLETED, completedSession.getStatus());
            assertTrue(completedSession.isExitFaceVerified());
            assertEquals(0.8, completedSession.getExitFaceSimilarity());
            assertNotNull(gateEvidence.findById(exitFaceId).orElseThrow().getConsumedAt());
            assertTrue(org.mockito.Mockito.mockingDetails(mockedAnprService).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("verifyFaceWithCamera"))
                .allMatch(invocation -> java.util.Arrays.equals(faceImage, (byte[]) invocation.getArgument(0))));
        } finally {
            for (String evidenceId : java.util.Arrays.stream(new String[]{entryRecognitionId, entryFaceId,
                    exitRecognitionId, exitFaceId}).filter(java.util.Objects::nonNull).toList()) {
                gateEvidence.findById(evidenceId).ifPresent(item -> {
                    try {
                        gateEvidenceStorage.delete(item.getFileName());
                        if (item.getDerivedFileName() != null) gateEvidenceStorage.delete(item.getDerivedFileName());
                    } catch (java.io.IOException ignored) { }
                    gateEvidence.delete(item);
                });
            }
            if (parkingSessionId != null) {
                parkingSlots.findFirstByCurrentSessionId(parkingSessionId).ifPresent(slot -> {
                    slot.setCurrentSession(null);
                    parkingSlots.save(slot);
                });
                parkingSessions.deleteById(parkingSessionId);
            }
            vehicles.deleteById(vehicleId);
            members.deleteById(memberId);
            households.deleteById(householdId);
            java.nio.file.Files.deleteIfExists(residentImageStorage.getUploadRoot()
                .resolve("residents").resolve(registrationReference.substring("/uploads/residents/".length())));
        }
    }

    @Test
    void guestFaceCaptureAndExitVerificationStayBehindSpringAndBindToOneParkingSession() throws Exception {
        String accessToken = createGateAccessToken("face-gate-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        byte[] jpeg;
        try (var output = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", output);
            jpeg = output.toByteArray();
        }
        String encoded = java.util.Base64.getEncoder().encodeToString(jpeg);
        when(mockedAnprService.captureFaceWithCamera()).thenReturn(objectMapper.readTree("""
            {"decision":"CAPTURED","similarity":0.0,"message":"synthetic capture",
             "realtimeImageBase64":"%s"}
            """.formatted(encoded)));
        when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
            {"decision":"PASS","similarity":0.8,"matchThreshold":0.363,
             "message":"synthetic verification","realtimeImageBase64":"%s"}
            """.formatted(encoded)));
        when(mockedAnprService.recognizeImage(any(byte[].class), anyString())).thenReturn(objectMapper.readTree("""
            {"plateText":"77Z99123","vehicleType":"MOTORBIKE","detectionConfidence":0.9,
             "ocrConfidence":0.8,"vehicleConfidence":0.7,"boundingBox":null,"frameIndex":0,
             "annotatedImageBase64":"","message":"synthetic recognition"}
            """));

        String guestCaptureId = null;
        String entryRecognitionId = null;
        String exitRecognitionId = null;
        String exitFaceId = null;
        Long parkingSessionId = null;
        try {
            mvc.perform(post("/api/parking/anpr/face/capture")
                    .contentType("application/json")
                    .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"77Z99123\"}")
                    .with(anonymous()))
                .andExpect(status().isUnauthorized());

            String captureBody = mvc.perform(post("/api/parking/anpr/face/capture")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"77Z99123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("CAPTURED")))
                .andExpect(jsonPath("$.evidenceId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
            guestCaptureId = objectMapper.readTree(captureBody).path("evidenceId").asText();

            String entryRecognitionBody = mvc.perform(multipart("/api/parking/anpr/recognize/image")
                    .file(new MockMultipartFile("file", "plate.jpg", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01}))
                    .param("operation", "ENTRY")
                    .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            entryRecognitionId = objectMapper.readTree(entryRecognitionBody).path("evidenceId").asText();

            String entryBody = mvc.perform(post("/api/parking/entry")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "plateNumber", "77Z99123", "evidenceId", entryRecognitionId,
                        "faceEvidenceId", guestCaptureId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            parkingSessionId = objectMapper.readTree(entryBody).path("sessionId").asLong();
            GateEvidence entryFace = gateEvidence.findById(guestCaptureId).orElseThrow();
            assertEquals(GateEvidenceKind.FACE_VERIFICATION, entryFace.getEvidenceKind());
            assertEquals("CAPTURED", entryFace.getVerificationDecision());
            assertEquals(parkingSessionId, entryFace.getParkingSessionId());
            assertNotNull(entryFace.getConsumedAt());

            String exitRecognitionBody = mvc.perform(multipart("/api/parking/anpr/recognize/image")
                    .file(new MockMultipartFile("file", "plate.jpg", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01}))
                    .param("operation", "EXIT")
                    .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            exitRecognitionId = objectMapper.readTree(exitRecognitionBody).path("evidenceId").asText();

            String verifyBody = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"EXIT\",\"plateNumber\":\"77Z99123\",\"evidenceId\":\""
                        + exitRecognitionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("PASS")))
                .andExpect(jsonPath("$.evidenceId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
            exitFaceId = objectMapper.readTree(verifyBody).path("evidenceId").asText();
            GateEvidence exitFace = gateEvidence.findById(exitFaceId).orElseThrow();
            assertEquals("PASS", exitFace.getVerificationDecision());
            assertEquals(parkingSessionId, exitFace.getParkingSessionId());

            String exitRequest = objectMapper.writeValueAsString(java.util.Map.of(
                "plateNumber", "77Z99123", "evidenceId", exitRecognitionId, "faceEvidenceId", exitFaceId));

            mvc.perform(post("/api/parking/exit-preview")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitRequest))
                .andExpect(status().isOk());
            mvc.perform(post("/api/parking/exit-confirm")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitRequest))
                .andExpect(status().isOk());
            assertNotNull(gateEvidence.findById(exitFaceId).orElseThrow().getConsumedAt());
            assertEquals(SessionStatus.COMPLETED, parkingSessions.findById(parkingSessionId).orElseThrow().getStatus());
        } finally {
            for (String evidenceId : java.util.Arrays.stream(new String[]{guestCaptureId, entryRecognitionId,
                    exitRecognitionId, exitFaceId}).filter(java.util.Objects::nonNull).toList()) {
                gateEvidence.findById(evidenceId).ifPresent(item -> {
                    try {
                        gateEvidenceStorage.delete(item.getFileName());
                        if (item.getDerivedFileName() != null) gateEvidenceStorage.delete(item.getDerivedFileName());
                    } catch (java.io.IOException ignored) { }
                    gateEvidence.delete(item);
                });
            }
            if (parkingSessionId != null) {
                parkingSlots.findFirstByCurrentSessionId(parkingSessionId).ifPresent(slot -> {
                    slot.setCurrentSession(null);
                    parkingSlots.save(slot);
                });
                parkingSessions.deleteById(parkingSessionId);
            }
        }
    }

    @Test
    void desktopAuthEndpointsLoginRotateDetectReplayAndLogout() throws Exception {
        String username = "auth-api-" + java.util.UUID.randomUUID();
        String password = "synthetic integration password";
        systemAccounts.saveAndFlush(new SystemAccount(username, passwordEncoder.encode(password),
            AccountRole.GATE_STAFF, false));
        String loginBody = "{\"username\":\"" + username + "\",\"password\":\"" + password +
            "\",\"deviceLabel\":\"test device\"}";

        var login = mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType", is("Bearer")))
            .andReturn();
        assertTrue(auditEvents.findAll().stream().anyMatch(event -> event.getAction().equals("LOGIN_SUCCESS")
            && username.equals(event.getActorUsername()) && event.getOutcome().equals("SUCCESS")));
        String originalAccess = com.jayway.jsonpath.JsonPath.read(
            login.getResponse().getContentAsString(), "$.accessToken");
        String originalRefresh = com.jayway.jsonpath.JsonPath.read(
            login.getResponse().getContentAsString(), "$.refreshToken");
        String sessionId = com.jayway.jsonpath.JsonPath.read(
            login.getResponse().getContentAsString(), "$.sessionId");

        var rotation = mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String rotatedAccess = com.jayway.jsonpath.JsonPath.read(
            rotation.getResponse().getContentAsString(), "$.accessToken");
        String rotatedRefresh = com.jayway.jsonpath.JsonPath.read(
            rotation.getResponse().getContentAsString(), "$.refreshToken");
        assertNotEquals(originalAccess, rotatedAccess);
        assertNotEquals(originalRefresh, rotatedRefresh);

        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + originalRefresh + "\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/parking/open").header("Authorization", "Bearer " + rotatedAccess))
            .andExpect(status().isUnauthorized());

        var secondLogin = mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
            .andExpect(status().isOk()).andReturn();
        String logoutAccess = com.jayway.jsonpath.JsonPath.read(
            secondLogin.getResponse().getContentAsString(), "$.accessToken");
        String logoutSessionId = com.jayway.jsonpath.JsonPath.read(
            secondLogin.getResponse().getContentAsString(), "$.sessionId");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + logoutAccess))
            .andExpect(status().isNoContent());
        assertTrue(auditEvents.findAll().stream().anyMatch(event -> event.getAction().equals("LOGOUT")
            && event.getTargetType().equals("DESKTOP_SESSION") && logoutSessionId.equals(event.getTargetReference())
            && event.getOutcome().equals("SUCCESS")));
        mvc.perform(get("/api/parking/open").header("Authorization", "Bearer " + logoutAccess))
            .andExpect(status().isUnauthorized());
        assertTrue(desktopSessions.findById(sessionId).isPresent());
    }

    @Test
    void desktopLoginDoesNotIssueTokensForFirstLoginPasswordChangeAccounts() throws Exception {
        String username = "forced-change-" + java.util.UUID.randomUUID();
        String password = "synthetic forced-change password";
        systemAccounts.saveAndFlush(new SystemAccount(username, passwordEncoder.encode(password),
            AccountRole.MANAGEMENT, true));
        long sessionsBefore = desktopSessions.count();

        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("invalid_credentials")));

        assertTrue(auditEvents.findAll().stream().anyMatch(event -> event.getAction().equals("LOGIN_FAILURE")
            && username.equals(event.getTargetReference()) && event.getOutcome().equals("FAILURE")));

        assertEquals(sessionsBefore, desktopSessions.count());
    }

    @Test
    void clientFaceAndOverrideAssertionsCannotBypassGuestExitVerification() throws Exception {
        String accessToken = createGateAccessToken("gate-face-claims-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        byte[] faceImage;
        try (var output = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", output);
            faceImage = output.toByteArray();
        }
        String encodedFace = java.util.Base64.getEncoder().encodeToString(faceImage);
        when(mockedAnprService.captureFaceWithCamera()).thenReturn(objectMapper.readTree("""
            {"decision":"CAPTURED","similarity":0.0,"message":"synthetic capture",
             "realtimeImageBase64":"%s"}
            """.formatted(encodedFace)));

        String faceEvidenceId = null;
        String entryEvidenceId = null;
        String exitEvidenceId = null;
        Long parkingSessionId = null;
        try {
            String captureBody = mvc.perform(post("/api/parking/anpr/face/capture")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"77A123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            faceEvidenceId = objectMapper.readTree(captureBody).path("evidenceId").asText();
            entryEvidenceId = recognizeForTest(accessToken, "ENTRY", "77A123456");
            String entry = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"evidenceId\":\""
                + entryEvidenceId + "\",\"faceEvidenceId\":\"" + faceEvidenceId
                + "\",\"realtimeFaceImageBase64\":\"client-forged-image\"}";
            String entryBody = mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(entry))
                .andExpect(status().isOk()).andExpect(jsonPath("$.warning", is(false)))
                .andReturn().getResponse().getContentAsString();
            parkingSessionId = objectMapper.readTree(entryBody).path("sessionId").asLong();
            assertEquals("gate-evidence:" + faceEvidenceId,
                parkingSessions.findById(parkingSessionId).orElseThrow().getEntryFaceImagePath());

            exitEvidenceId = recognizeForTest(accessToken, "EXIT", "77A123456");
            String exitWithoutFace = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"evidenceId\":\""
                + exitEvidenceId + "\"}";
            String exitWithFaceClaim = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"evidenceId\":\""
                + exitEvidenceId + "\",\"faceVerified\":true}";
            String exitWithOverrideClaim = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"evidenceId\":\""
                + exitEvidenceId + "\",\"manualOverride\":true}";
            mvc.perform(post("/api/parking/exit-preview").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitWithoutFace))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/parking/exit-confirm").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitWithFaceClaim))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/parking/exit-confirm").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitWithOverrideClaim))
                .andExpect(status().isBadRequest());

            String forgedOverrideEvidence = recognizeForTest(accessToken, "ENTRY", "78A123456");
            String forgedOverride = "{\"plateNumber\":\"78A123456\",\"vehicleType\":\"MOTORBIKE\",\"manualOverride\":true,\"evidenceId\":\""
                + forgedOverrideEvidence + "\"}";
            mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(forgedOverride))
                .andExpect(status().isBadRequest());
            deleteUnusedEvidence(forgedOverrideEvidence);
        } finally {
            for (String evidenceId : java.util.Arrays.stream(
                    new String[]{faceEvidenceId, entryEvidenceId, exitEvidenceId})
                    .filter(java.util.Objects::nonNull).toList()) {
                gateEvidence.findById(evidenceId).ifPresent(item -> {
                    try {
                        gateEvidenceStorage.delete(item.getFileName());
                        if (item.getDerivedFileName() != null) gateEvidenceStorage.delete(item.getDerivedFileName());
                    } catch (java.io.IOException ignored) { }
                    gateEvidence.delete(item);
                });
            }
            if (parkingSessionId != null) {
                parkingSlots.findFirstByCurrentSessionId(parkingSessionId).ifPresent(slot -> {
                    slot.setCurrentSession(null);
                    parkingSlots.save(slot);
                });
                parkingSessions.deleteById(parkingSessionId);
            }
        }
    }

    @Test
    @WithMockUser(username = "test-gate-override", roles = "GATE_STAFF")
    void gateStaffOverrideIsAllowedOnlyForAuthorizedMemberWithoutRegistrationImageAndIsAudited() throws Exception {
        String gateUsername = "test-gate-override-" + java.util.UUID.randomUUID();
        String accessToken = createGateAccessToken(gateUsername, AccountRole.GATE_STAFF);
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        Household household = new Household();
        household.setHouseholdCode("OVERRIDE-" + suffix.substring(0, 12));
        household.setApartmentNumber("T1-0101");
        household = households.saveAndFlush(household);

        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName("Synthetic override member");
        member.setCitizenId(suffix.substring(0, 12));
        member = members.saveAndFlush(member);

        String plate = "83A" + suffix.substring(0, 8);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(member.getFullName());
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(member);
        vehicle.setAuthorizedMembers(java.util.Set.of(member));
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicles.saveAndFlush(vehicle);

        FamilyMember unrelatedMember = new FamilyMember();
        unrelatedMember.setHousehold(household);
        unrelatedMember.setFullName("Synthetic unrelated member");
        unrelatedMember.setCitizenId(suffix.substring(12, 24));
        unrelatedMember = members.saveAndFlush(unrelatedMember);

        String entryEvidenceId = recognizeForTest(accessToken, "ENTRY", plate);
        String overrideRequest = "{\"plateNumber\":\"" + plate
            + "\",\"vehicleType\":\"MOTORBIKE\",\"familyMemberId\":" + member.getId()
            + ",\"evidenceId\":\"" + entryEvidenceId
            + "\",\"override\":{\"reason\":\"Registration image unavailable at gate\"}}";
        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(overrideRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warning", is(true)));

        var session = parkingSessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
            plate, SessionStatus.OPEN).orElseThrow();
        assertTrue(session.isManualOverride());
        var overrideEvent = auditEvents.findAll().stream()
            .filter(event -> event.getAction().equals("OVERRIDE_ENTRY")
                && session.getId().toString().equals(event.getTargetReference()))
            .findFirst().orElseThrow();
        assertEquals(gateUsername, overrideEvent.getActorUsername());
        assertEquals("SUCCESS", overrideEvent.getOutcome());
        assertTrue(overrideEvent.getReason().startsWith("MISSING_REFERENCE_IMAGE:"));
        assertEquals(entryEvidenceId, overrideEvent.getEvidenceReference());

        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(overrideRequest))
            .andExpect(status().isConflict());

        String plateForBlockedCard = "85A" + suffix.substring(0, 8);
        Vehicle vehicleForBlockedCard = new Vehicle();
        vehicleForBlockedCard.setPlateNumber(plateForBlockedCard);
        vehicleForBlockedCard.setOwnerName(member.getFullName());
        vehicleForBlockedCard.setHousehold(household);
        vehicleForBlockedCard.setRegisteredOwner(member);
        vehicleForBlockedCard.setAuthorizedMembers(java.util.Set.of(member));
        vehicleForBlockedCard.setVehicleType(VehicleType.MOTORBIKE);
        vehicleForBlockedCard = vehicles.saveAndFlush(vehicleForBlockedCard);
        ParkingCard blockedCard = new ParkingCard();
        blockedCard.setCardCode("BLOCKED-" + suffix.substring(0, 12));
        blockedCard.setVehicle(vehicleForBlockedCard);
        blockedCard.setStatus(CardStatus.BLOCKED);
        cards.saveAndFlush(blockedCard);
        String blockedEvidence = recognizeForTest(accessToken, "ENTRY", plateForBlockedCard);
        String blockedCardRequest = "{\"plateNumber\":\"" + plateForBlockedCard + "\",\"cardCode\":\""
            + blockedCard.getCardCode() + "\",\"vehicleType\":\"MOTORBIKE\",\"familyMemberId\":"
            + member.getId() + ",\"evidenceId\":\"" + blockedEvidence
            + "\",\"override\":{\"reason\":\"Blocked card manual review\"}}";
        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .content(blockedCardRequest))
            .andExpect(status().isBadRequest());
        deleteUnusedEvidence(blockedEvidence);

        String plateForUnauthorized = "86A" + suffix.substring(0, 8);
        Vehicle vehicleForUnauthorized = new Vehicle();
        vehicleForUnauthorized.setPlateNumber(plateForUnauthorized);
        vehicleForUnauthorized.setOwnerName(member.getFullName());
        vehicleForUnauthorized.setHousehold(household);
        vehicleForUnauthorized.setRegisteredOwner(member);
        vehicleForUnauthorized.setAuthorizedMembers(java.util.Set.of(member));
        vehicleForUnauthorized.setVehicleType(VehicleType.MOTORBIKE);
        vehicles.saveAndFlush(vehicleForUnauthorized);
        String unauthorizedEvidence = recognizeForTest(accessToken, "ENTRY", plateForUnauthorized);
        String unauthorizedMemberRequest = "{\"plateNumber\":\"" + plateForUnauthorized
            + "\",\"vehicleType\":\"MOTORBIKE\",\"familyMemberId\":" + unrelatedMember.getId()
            + ",\"evidenceId\":\"" + unauthorizedEvidence
            + "\",\"override\":{\"reason\":\"Unrelated member manual review\"}}";
        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .content(unauthorizedMemberRequest))
            .andExpect(status().isBadRequest());
        deleteUnusedEvidence(unauthorizedEvidence);

        member.setRegistrationFaceImagePath("/uploads/residents/synthetic-registered-face.png");
        members.saveAndFlush(member);
        String plateWithProfile = "84A" + suffix.substring(0, 8);
        Vehicle vehicleWithProfile = new Vehicle();
        vehicleWithProfile.setPlateNumber(plateWithProfile);
        vehicleWithProfile.setOwnerName(member.getFullName());
        vehicleWithProfile.setHousehold(household);
        vehicleWithProfile.setRegisteredOwner(member);
        vehicleWithProfile.setAuthorizedMembers(java.util.Set.of(member));
        vehicleWithProfile.setVehicleType(VehicleType.MOTORBIKE);
        vehicles.saveAndFlush(vehicleWithProfile);
        String profileEvidence = recognizeForTest(accessToken, "ENTRY", plateWithProfile);
        String profileOverrideRequest = "{\"plateNumber\":\"" + plateWithProfile
            + "\",\"vehicleType\":\"MOTORBIKE\",\"familyMemberId\":" + member.getId()
            + ",\"evidenceId\":\"" + profileEvidence
            + "\",\"override\":{\"reason\":\"Profile exists manual review\"}}";
        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .content(profileOverrideRequest))
            .andExpect(status().isBadRequest());
        deleteUnusedEvidence(profileEvidence);

        String missingMemberEvidence = recognizeForTest(accessToken, "ENTRY", plateWithProfile);
        String missingMemberOverrideRequest = "{\"plateNumber\":\"" + plateWithProfile
            + "\",\"vehicleType\":\"MOTORBIKE\",\"evidenceId\":\""
            + missingMemberEvidence + "\",\"override\":{\"reason\":\"No member selected for override\"}}";
        mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .content(missingMemberOverrideRequest))
            .andExpect(status().isBadRequest());
        deleteUnusedEvidence(missingMemberEvidence);
        mvc.perform(get("/account-admin/audit/overrides"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test-gate-outage", roles = "GATE_STAFF")
    void recognitionOutageOverrideRequiresBackendFaceEvidenceAndIsAudited() throws Exception {
        String gateUsername = "test-gate-outage-" + java.util.UUID.randomUUID();
        String accessToken = createGateAccessToken(gateUsername, AccountRole.GATE_STAFF);
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String plate = "87A" + suffix.substring(0, 8);
        ResidentFixture fixture = createResidentFixture(plate, true);
        byte[] faceImage = fixture.faceImage();
        String encodedFace = java.util.Base64.getEncoder().encodeToString(faceImage);
        when(mockedAnprService.recognizeImage(any(byte[].class), anyString()))
            .thenThrow(new vn.edu.parking.service.AnprServiceUnavailableException());
        when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
            {"decision":"PASS","similarity":0.8,"matchThreshold":0.99,
             "message":"synthetic verification","realtimeImageBase64":"%s"}
            """.formatted(encodedFace)));

        String recognitionId = null;
        String faceEvidenceId = null;
        Long parkingSessionId = null;
        try {
            recognitionId = recognizeUnavailableForTest(accessToken, "ENTRY");
            String noFaceProofOverride = "{\"plateNumber\":\"" + plate + "\",\"familyMemberId\":"
                + fixture.member().getId() + ",\"evidenceId\":\"" + recognitionId
                + "\",\"override\":{\"reason\":\"Manual plate after OCR outage\"}}";
            mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(noFaceProofOverride))
                .andExpect(status().isBadRequest());
            assertTrue(parkingSessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
                plate, SessionStatus.OPEN).isEmpty());
            String faceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"" + plate
                        + "\",\"familyMemberId\":" + fixture.member().getId()
                        + ",\"evidenceId\":\"" + recognitionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("PASS")))
                .andReturn().getResponse().getContentAsString();
            faceEvidenceId = objectMapper.readTree(faceResponse).path("evidenceId").asText();

            String request = "{\"plateNumber\":\"" + plate + "\",\"familyMemberId\":"
                + fixture.member().getId() + ",\"evidenceId\":\"" + recognitionId
                + "\",\"faceEvidenceId\":\"" + faceEvidenceId + "\"}";
            mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(request))
                .andExpect(status().isConflict());
            assertTrue(parkingSessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
                plate, SessionStatus.OPEN).isEmpty());

            String overrideRequest = request.substring(0, request.length() - 1)
                + ",\"override\":{\"reason\":\"Plate read manually after OCR outage\"}}";
            String entryResponse = mvc.perform(post("/api/parking/entry")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(overrideRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            Long openedSessionId = objectMapper.readTree(entryResponse).path("sessionId").asLong();
            parkingSessionId = openedSessionId;

            var event = auditEvents.findAll().stream()
                .filter(item -> item.getAction().equals("OVERRIDE_ENTRY")
                    && openedSessionId.toString().equals(item.getTargetReference()))
                .findFirst().orElseThrow();
            assertEquals(gateUsername, event.getActorUsername());
            assertTrue(event.getReason().startsWith("RECOGNITION_SERVICE_UNAVAILABLE:"));
            assertEquals(recognitionId, event.getEvidenceReference());
            GateEvidence operationEvidence = gateEvidence.findById(recognitionId).orElseThrow();
            assertEquals(GateOperationType.ENTRY, operationEvidence.getOperationType());
            assertEquals(openedSessionId, operationEvidence.getParkingSessionId());
            assertEquals(plate.toUpperCase(java.util.Locale.ROOT),
                parkingSessions.findById(openedSessionId).orElseThrow().getEntryPlate());
            GateEvidence faceEvidence = gateEvidence.findById(faceEvidenceId).orElseThrow();
            assertEquals(openedSessionId, faceEvidence.getParkingSessionId());
            assertNotNull(faceEvidence.getConsumedAt());
        } finally {
            cleanupOverrideTestArtifacts(parkingSessionId, fixture.registrationImageReference(),
                recognitionId, faceEvidenceId);
        }
    }

    @Test
    @WithMockUser(username = "test-gate-review", roles = "GATE_STAFF")
    void onlyBackendReviewCanBeOverriddenAndBackendRejectCannot() throws Exception {
        String gateUsername = "test-gate-review-" + java.util.UUID.randomUUID();
        String accessToken = createGateAccessToken(gateUsername, AccountRole.GATE_STAFF);
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        ResidentFixture reviewFixture = createResidentFixture("88A" + suffix.substring(0, 8), true);
        ResidentFixture rejectFixture = createResidentFixture("89A" + suffix.substring(0, 8), true);
        String encodedFace = java.util.Base64.getEncoder().encodeToString(reviewFixture.faceImage());
        when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
            {"decision":"PASS","similarity":0.33,"matchThreshold":0.99,
             "message":"client-claimed pass","realtimeImageBase64":"%s"}
            """.formatted(encodedFace)));

        String reviewRecognitionId = null;
        String reviewFaceId = null;
        String rejectRecognitionId = null;
        String rejectFaceId = null;
        Long reviewSessionId = null;
        try {
            reviewRecognitionId = recognizeForTest(accessToken, "ENTRY", reviewFixture.plate());
            String reviewFaceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"" + reviewFixture.plate()
                        + "\",\"familyMemberId\":" + reviewFixture.member().getId()
                        + ",\"evidenceId\":\"" + reviewRecognitionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("REVIEW")))
                .andReturn().getResponse().getContentAsString();
            reviewFaceId = objectMapper.readTree(reviewFaceResponse).path("evidenceId").asText();

            String reviewRequest = "{\"plateNumber\":\"" + reviewFixture.plate() + "\",\"familyMemberId\":"
                + reviewFixture.member().getId() + ",\"evidenceId\":\"" + reviewRecognitionId
                + "\",\"faceEvidenceId\":\"" + reviewFaceId + "\"}";
            mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(reviewRequest))
                .andExpect(status().isBadRequest());
            String reviewOverride = reviewRequest.substring(0, reviewRequest.length() - 1)
                + ",\"override\":{\"reason\":\"Reviewed face match at entry gate\"}}";
            String entryResponse = mvc.perform(post("/api/parking/entry")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(reviewOverride))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            reviewSessionId = objectMapper.readTree(entryResponse).path("sessionId").asLong();

            when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
                {"decision":"PASS","similarity":0.1,"matchThreshold":0.0,
                 "message":"client-claimed pass","realtimeImageBase64":"%s"}
                """.formatted(encodedFace)));
            rejectRecognitionId = recognizeForTest(accessToken, "ENTRY", rejectFixture.plate());
            String rejectFaceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"" + rejectFixture.plate()
                        + "\",\"familyMemberId\":" + rejectFixture.member().getId()
                        + ",\"evidenceId\":\"" + rejectRecognitionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("REJECT")))
                .andReturn().getResponse().getContentAsString();
            rejectFaceId = objectMapper.readTree(rejectFaceResponse).path("evidenceId").asText();
            String rejectOverride = "{\"plateNumber\":\"" + rejectFixture.plate() + "\",\"familyMemberId\":"
                + rejectFixture.member().getId() + ",\"evidenceId\":\"" + rejectRecognitionId
                + "\",\"faceEvidenceId\":\"" + rejectFaceId
                + "\",\"override\":{\"reason\":\"Attempt to override rejected face\"}}";
            mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(rejectOverride))
                .andExpect(status().isForbidden());
        } finally {
            cleanupOverrideTestArtifacts(reviewSessionId, reviewFixture.registrationImageReference(),
                reviewRecognitionId, reviewFaceId);
            cleanupOverrideTestArtifacts(null, rejectFixture.registrationImageReference(),
                rejectRecognitionId, rejectFaceId);
        }
    }

    @Test
    @WithMockUser(username = "test-gate-exit-review", roles = "GATE_STAFF")
    void backendReviewExitRequiresReasonedOverrideAndAuditsTheOpenSession() throws Exception {
        String gateUsername = "test-gate-exit-review-" + java.util.UUID.randomUUID();
        String accessToken = createGateAccessToken(gateUsername, AccountRole.GATE_STAFF);
        String managementUsername = "test-evidence-review-" + java.util.UUID.randomUUID();
        String managementToken = createGateAccessToken(managementUsername, AccountRole.MANAGEMENT);
        String otherGateToken = createGateAccessToken("test-other-gate-" + java.util.UUID.randomUUID(),
            AccountRole.GATE_STAFF);
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        ResidentFixture fixture = createResidentFixture("90A" + suffix.substring(0, 8), true);
        String encodedFace = java.util.Base64.getEncoder().encodeToString(fixture.faceImage());
        when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
            {"decision":"PASS","similarity":0.8,"matchThreshold":0.99,
             "message":"synthetic verification","realtimeImageBase64":"%s"}
            """.formatted(encodedFace)));

        String entryRecognitionId = null;
        String entryFaceId = null;
        String exitRecognitionId = null;
        String exitFaceId = null;
        Long parkingSessionId = null;
        try {
            entryRecognitionId = recognizeForTest(accessToken, "ENTRY", fixture.plate());
            String entryFaceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"ENTRY\",\"plateNumber\":\"" + fixture.plate()
                        + "\",\"familyMemberId\":" + fixture.member().getId()
                        + ",\"evidenceId\":\"" + entryRecognitionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("PASS")))
                .andReturn().getResponse().getContentAsString();
            entryFaceId = objectMapper.readTree(entryFaceResponse).path("evidenceId").asText();
            String entryRequest = "{\"plateNumber\":\"" + fixture.plate() + "\",\"familyMemberId\":"
                + fixture.member().getId() + ",\"evidenceId\":\"" + entryRecognitionId
                + "\",\"faceEvidenceId\":\"" + entryFaceId + "\"}";
            String entryResponse = mvc.perform(post("/api/parking/entry")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(entryRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            parkingSessionId = objectMapper.readTree(entryResponse).path("sessionId").asLong();

            when(mockedAnprService.verifyFaceWithCamera(any(byte[].class))).thenReturn(objectMapper.readTree("""
                {"decision":"PASS","similarity":0.33,"matchThreshold":0.99,
                 "message":"client-claimed pass","realtimeImageBase64":"%s"}
                """.formatted(encodedFace)));
            exitRecognitionId = recognizeForTest(accessToken, "EXIT", fixture.plate());
            String exitFaceResponse = mvc.perform(post("/api/parking/anpr/face/verify")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json")
                    .content("{\"operation\":\"EXIT\",\"plateNumber\":\"" + fixture.plate()
                        + "\",\"familyMemberId\":" + fixture.member().getId()
                        + ",\"evidenceId\":\"" + exitRecognitionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("REVIEW")))
                .andReturn().getResponse().getContentAsString();
            exitFaceId = objectMapper.readTree(exitFaceResponse).path("evidenceId").asText();

            String exitRequest = "{\"plateNumber\":\"" + fixture.plate() + "\",\"familyMemberId\":"
                + fixture.member().getId() + ",\"evidenceId\":\"" + exitRecognitionId
                + "\",\"faceEvidenceId\":\"" + exitFaceId + "\"}";
            mvc.perform(post("/api/parking/exit-preview").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(exitRequest))
                .andExpect(status().isBadRequest());

            String overrideRequest = exitRequest.substring(0, exitRequest.length() - 1)
                + ",\"override\":{\"reason\":\"Reviewed resident face match at exit gate\"}}";
            mvc.perform(post("/api/parking/exit-preview").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(overrideRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PREVIEW")));
            mvc.perform(post("/api/parking/exit-confirm").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(overrideRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

            mvc.perform(get("/api/parking/sessions/" + parkingSessionId
                    + "/operations/EXIT/evidence/AI_RECOGNITION/" + exitRecognitionId + "/image")
                    .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
            mvc.perform(get("/api/parking/sessions/" + parkingSessionId
                    + "/operations/EXIT/evidence/AI_RECOGNITION/" + exitRecognitionId + "/image")
                    .header("Authorization", "Bearer " + otherGateToken))
                .andExpect(status().isNotFound());
            mvc.perform(get("/api/parking/sessions/" + parkingSessionId
                    + "/operations/EXIT/evidence/AI_RECOGNITION/" + exitRecognitionId + "/image")
                    .header("Authorization", "Bearer " + managementToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

            Long completedSessionId = parkingSessionId;
            var event = auditEvents.findAll().stream()
                .filter(item -> item.getAction().equals("OVERRIDE_EXIT")
                    && completedSessionId.toString().equals(item.getTargetReference()))
                .findFirst().orElseThrow();
            assertEquals(gateUsername, event.getActorUsername());
            assertTrue(event.getReason().startsWith("AI_REVIEW:"));
            assertEquals(exitRecognitionId, event.getEvidenceReference());
            assertTrue(parkingSessions.findById(parkingSessionId).orElseThrow().isManualOverride());
            GateEvidence operationEvidence = gateEvidence.findById(exitRecognitionId).orElseThrow();
            assertEquals(GateOperationType.EXIT, operationEvidence.getOperationType());
            assertEquals(parkingSessionId, operationEvidence.getParkingSessionId());
            assertNotNull(operationEvidence.getConsumedAt());
            GateEvidence faceEvidence = gateEvidence.findById(exitFaceId).orElseThrow();
            assertEquals(parkingSessionId, faceEvidence.getParkingSessionId());
            assertNotNull(faceEvidence.getConsumedAt());
        } finally {
            cleanupOverrideTestArtifacts(parkingSessionId, fixture.registrationImageReference(),
                entryRecognitionId, entryFaceId, exitRecognitionId, exitFaceId);
        }
    }

    @Test
    void managementCanReadManualOverrideReviewQueue() throws Exception {
        auditEvents.saveAndFlush(new SecurityAuditEvent(null, "synthetic-gate", "OVERRIDE_ENTRY",
            "PARKING_SESSION", "synthetic-session", "SUCCESS", "REGISTRATION_IMAGE_MISSING",
            "family-member:synthetic", java.time.Instant.now()));

        mvc.perform(get("/account-admin/audit/overrides"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("OVERRIDE_ENTRY")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("REGISTRATION_IMAGE_MISSING")));
    }

    @Test
    @WithMockUser(username = "test-manager", roles = "MANAGEMENT")
    void combinedRegistrationCreatesHouseholdMemberVehicleThenOpensPackageStep() throws Exception {
        byte[] image = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile face = new MockMultipartFile("registrationFaceImage", "face.png", "image/png", image);
        MockMultipartFile registration = new MockMultipartFile("registrationImage", "registration.png", "image/png", image);
        mvc.perform(multipart("/registrations").file(face).file(registration).with(csrf())
                .param("householdCode", "HH-COMBINED-01").param("apartmentNumber", "C1-0101")
                .param("buildingName", "C1").param("contactPhone", "0901000001")
                .param("fullName", "Cư dân đăng ký tổng hợp").param("citizenId", "079205019299")
                .param("relationshipToHead", "Chủ hộ").param("phone", "0901000001")
                .param("plateNumber", "61A1-234.56").param("vehicleType", "MOTORBIKE")
                .param("fuelType", "GASOLINE").param("registrationNumber", "DKX-COMBINED")
                .param("brand", "Honda").param("modelName", "Vision").param("color", "Đen")
                .param("chassisNumber", "FRAME-COMBINED").param("engineNumber", "ENGINE-COMBINED"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/cards?vehicleId=*"));

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase("61A123456").orElseThrow();
        assertEquals("HH-COMBINED-01", vehicle.getHousehold().getHouseholdCode());
        assertEquals("Cư dân đăng ký tổng hợp", vehicle.getRegisteredOwner().getFullName());
        assertTrue(vehicle.getRegisteredOwner().getRegistrationFaceImagePath().startsWith("/uploads/residents/"));
    }

    @Test
    void scansVietnameseCitizenCardQrOnServerWithoutBrowserBarcodeDetector() throws Exception {
        long previousAuditId = auditEvents.findAll().stream()
            .filter(event -> event.getAction().equals("CCCD_QR_SCAN"))
            .mapToLong(SecurityAuditEvent::getId).max().orElse(0);
        // Synthetic-only QR payload; it is not based on a real citizen card.
        String syntheticQrPayload = "000000000001|000000002|TEST CITIZEN|01011990|Nam|FICTIONAL TEST ADDRESS|01012020";
        var matrix = new QRCodeWriter().encode(syntheticQrPayload, BarcodeFormat.QR_CODE, 500, 500,
            java.util.Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        MockMultipartFile qr = new MockMultipartFile("file", "cccd-qr.png", "image/png", output.toByteArray());
        mvc.perform(multipart("/api/cccd/scan-qr").file(qr).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raw").doesNotExist())
            .andExpect(jsonPath("$.citizenId", is("000000000001")))
            .andExpect(jsonPath("$.fullName", is("TEST CITIZEN")))
            .andExpect(jsonPath("$.dateOfBirth", is("1990-01-01")));

        var scanAudit = auditEvents.findAll().stream()
            .filter(event -> event.getAction().equals("CCCD_QR_SCAN") && event.getId() > previousAuditId).toList();
        assertEquals(1, scanAudit.size());
        assertEquals("SUCCESS", scanAudit.get(0).getOutcome());
        assertEquals("CCCD_QR", scanAudit.get(0).getTargetType());
        assertNull(scanAudit.get(0).getTargetReference());
        assertNull(scanAudit.get(0).getEvidenceReference());
    }

    @Test
    void failedCitizenCardQrScanIsAuditedWithoutImageOrDecodedPii() throws Exception {
        long previousAuditId = auditEvents.findAll().stream()
            .filter(event -> event.getAction().equals("CCCD_QR_SCAN"))
            .mapToLong(SecurityAuditEvent::getId).max().orElse(0);
        MockMultipartFile invalidImage = new MockMultipartFile("file", "invalid.png", "image/png", new byte[]{1});

        mvc.perform(multipart("/api/cccd/scan-qr").file(invalidImage).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Tệp đã chọn không phải hình ảnh hợp lệ")));

        var scanAudit = auditEvents.findAll().stream()
            .filter(event -> event.getAction().equals("CCCD_QR_SCAN") && event.getId() > previousAuditId).toList();
        assertEquals(1, scanAudit.size());
        assertEquals("FAILURE", scanAudit.get(0).getOutcome());
        assertEquals("INVALID_INPUT", scanAudit.get(0).getReason());
        assertNull(scanAudit.get(0).getTargetReference());
        assertNull(scanAudit.get(0).getEvidenceReference());
    }

    @Test
    @WithMockUser(username = "test-manager", roles = "MANAGEMENT")
    void managementMemberReadDoesNotReturnRawCccdQr() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        Household household = new Household();
        household.setHouseholdCode("PII-" + suffix.substring(0, 12));
        household.setApartmentNumber("T-" + suffix.substring(12, 20));
        household = households.saveAndFlush(household);
        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName("Synthetic QR privacy member");
        member.setCitizenId(suffix.substring(20, 32));
        member.setCccdQrRaw("synthetic raw QR payload that must not be exposed");
        member = members.saveAndFlush(member);

        mvc.perform(get("/admin-data/members/" + member.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.citizenId", is(member.getCitizenId())))
            .andExpect(jsonPath("$.cccdQrRaw").doesNotExist());
    }

    @Test
    void surveyPricesAreTheDefaults() {
        assertEquals(BigDecimal.valueOf(100_000), prices.findByVehicleType(VehicleType.BICYCLE_ELECTRIC_BICYCLE).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(150_000), prices.findByVehicleType(VehicleType.MOTORBIKE).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(300_000), prices.findByVehicleType(VehicleType.LARGE_MOTORBIKE).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(1_500_000), prices.findByVehicleType(VehicleType.CAR).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(1_700_000), prices.findByVehicleType(VehicleType.CAR_6_7).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(1_800_000), prices.findByVehicleType(VehicleType.CAR_8_9).orElseThrow().getMonthlyPrice());
    }

    @Test
    void familyMembersCanBeAuthorizedForTheSameVehicle() throws Exception {
        Household household = new Household();
        household.setHouseholdCode("HH-TEST-SHARED");
        household.setApartmentNumber("T1-0909");
        household.setMaxTwoWheelers(3);
        household = households.save(household);

        FamilyMember husband = new FamilyMember();
        husband.setHousehold(household);
        husband.setFullName("Người chồng kiểm thử");
        husband = members.save(husband);
        FamilyMember wife = new FamilyMember();
        wife.setHousehold(household);
        wife.setFullName("Người vợ kiểm thử");
        wife = members.save(wife);

        mvc.perform(post("/vehicles").with(csrf())
                .param("householdId", household.getId().toString())
                .param("registeredOwnerId", husband.getId().toString())
                .param("authorizedMemberIds", wife.getId().toString())
                .param("plateNumber", "60A9-999.99")
                .param("vehicleType", "MOTORBIKE")
                .param("fuelType", "GASOLINE")
                .param("registrationNumber", "DKX-TEST-01")
                .param("brand", "Honda")
                .param("modelName", "Vision")
                .param("color", "Đen")
                .param("chassisNumber", "RLH-TEST-CHASSIS")
                .param("engineNumber", "JF66E-TEST-ENGINE")
                .param("registrationImagePath", "uploads/test-registration.jpg"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/vehicles"));

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase("60A999999").orElseThrow();
        assertEquals(household.getId(), vehicle.getHousehold().getId());
        assertEquals(2, vehicle.getAuthorizedMembers().size());
        assertTrue(vehicle.getAuthorizedMemberNames().contains("Người chồng kiểm thử"));
        assertTrue(vehicle.getAuthorizedMemberNames().contains("Người vợ kiểm thử"));
    }

    private String createGateAccessToken(String username, AccountRole role) {
        SystemAccount account = systemAccounts.saveAndFlush(
            new SystemAccount(username, "synthetic-test-hash", role, false));
        var session = desktopRefreshService.createSession(account, "synthetic-gate-test");
        return createAccessToken(account, session.sessionId());
    }

    private String createConcurrentMutationAccount() {
        String username = "account-race-" + java.util.UUID.randomUUID();
        systemAccounts.saveAndFlush(new SystemAccount(username,
            passwordEncoder.encode("initial concurrency password"), AccountRole.GATE_STAFF, false));
        return username;
    }

    private void runConcurrentSelfPasswordChange(String username, String currentPassword,
            Runnable competingMutation) throws Exception {
        var passwordChecked = new java.util.concurrent.CountDownLatch(1);
        var releasePasswordChange = new java.util.concurrent.CountDownLatch(1);
        var competingMutationStarted = new java.util.concurrent.CountDownLatch(1);
        var competingMutationFinished = new java.util.concurrent.CountDownLatch(1);
        passwordMatchPause.set(new PasswordMatchPause(currentPassword,
            "security-self-password-change", passwordChecked, releasePasswordChange));

        var threadNumber = new java.util.concurrent.atomic.AtomicInteger();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2, task ->
            new Thread(task, threadNumber.getAndIncrement() == 0
                ? "security-self-password-change" : "security-competing-mutation"));
        try {
            var passwordChange = executor.submit(() -> accountPasswordService.changeOwnPassword(username,
                currentPassword, "self changed concurrency password"));
            assertTrue(passwordChecked.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var mutation = executor.submit(() -> {
                competingMutationStarted.countDown();
                try { competingMutation.run(); }
                finally { competingMutationFinished.countDown(); }
            });
            assertTrue(competingMutationStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(competingMutationFinished.await(250, java.util.concurrent.TimeUnit.MILLISECONDS),
                "Competing mutation must wait for the in-flight password change");
            releasePasswordChange.countDown();
            passwordChange.get(10, java.util.concurrent.TimeUnit.SECONDS);
            mutation.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            passwordMatchPause.set(null);
            releasePasswordChange.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private ResidentFixture createResidentFixture(String plate, boolean withRegistrationImage) throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        Household household = new Household();
        household.setHouseholdCode("OVERRIDE-" + suffix.substring(0, 12));
        household.setApartmentNumber("T-" + suffix.substring(12, 20));
        household = households.saveAndFlush(household);

        byte[] faceImage;
        try (var output = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", output);
            faceImage = output.toByteArray();
        }
        String reference = withRegistrationImage
            ? residentImageStorage.save(new MockMultipartFile("registration", "registered.jpg",
                "image/jpeg", faceImage), null)
            : null;

        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName("Synthetic override member");
        member.setCitizenId(suffix.substring(20, 32));
        member.setRegistrationFaceImagePath(reference);
        member = members.saveAndFlush(member);

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(member.getFullName());
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(member);
        vehicle.setAuthorizedMembers(java.util.Set.of(member));
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicles.saveAndFlush(vehicle);
        return new ResidentFixture(plate, member, faceImage, reference);
    }

    private String recognizeUnavailableForTest(String accessToken, String operation) throws Exception {
        when(mockedAnprService.recognizeImage(any(byte[].class), anyString()))
            .thenThrow(new vn.edu.parking.service.AnprServiceUnavailableException());
        byte[] image = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        String response = mvc.perform(multipart("/api/parking/anpr/recognize/image")
                .file(new MockMultipartFile("file", "plate.jpg", "image/jpeg", image))
                .param("operation", operation)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processingStatus", is("UNAVAILABLE")))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("evidenceId").asText();
    }

    private void assertResidentEntryRejected(String accessToken, String plate, Long memberId, String evidenceId)
            throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("plateNumber", plate);
        body.put("vehicleType", "MOTORBIKE");
        if (memberId != null) body.put("familyMemberId", memberId);
        body.put("evidenceId", evidenceId);
        var result = mvc.perform(post("/api/parking/entry").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(objectMapper.writeValueAsString(body)))
            .andReturn();
        var openSession = parkingSessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
            plate, SessionStatus.OPEN);
        GateEvidence evidence = gateEvidence.findById(evidenceId).orElseThrow();
        try {
            assertEquals(400, result.getResponse().getStatus());
            assertTrue(openSession.isEmpty());
            assertNull(evidence.getConsumedAt());
            assertTrue(parkingSlots.findAll().stream().noneMatch(slot -> slot.getCurrentSession() != null
                && slot.getCurrentSession().getEntryPlate().equalsIgnoreCase(plate)));
        } finally {
            cleanupOverrideTestArtifacts(openSession.map(ParkingSession::getId).orElse(null), null, evidenceId);
        }
    }

    private void cleanupOverrideTestArtifacts(Long parkingSessionId, String registrationImageReference,
            String... evidenceIds) throws java.io.IOException {
        if (parkingSessionId != null) {
            parkingSlots.findFirstByCurrentSessionId(parkingSessionId).ifPresent(slot -> {
                slot.setCurrentSession(null);
                parkingSlots.save(slot);
            });
            parkingSessions.deleteById(parkingSessionId);
        }
        for (String evidenceId : java.util.Arrays.stream(evidenceIds)
                .filter(java.util.Objects::nonNull).distinct().toList()) {
            gateEvidence.findById(evidenceId).ifPresent(item -> {
                try {
                    gateEvidenceStorage.delete(item.getFileName());
                    if (item.getDerivedFileName() != null)
                        gateEvidenceStorage.delete(item.getDerivedFileName());
                } catch (java.io.IOException ignored) { }
                gateEvidence.delete(item);
            });
        }
        if (registrationImageReference != null) {
            String filename = registrationImageReference.substring(registrationImageReference.lastIndexOf('/') + 1);
            java.nio.file.Files.deleteIfExists(residentImageStorage.getUploadRoot()
                .resolve("residents").resolve(filename));
        }
    }

    private record PasswordMatchPause(String rawPassword, String threadName,
            java.util.concurrent.CountDownLatch reached, java.util.concurrent.CountDownLatch release) { }

    private record ResidentFixture(String plate, FamilyMember member, byte[] faceImage,
            String registrationImageReference) { }

    private String recognizeForTest(String accessToken, String operation, String plate) throws Exception {
        when(mockedAnprService.recognizeImage(any(byte[].class), anyString())).thenReturn(objectMapper.readTree("""
            {"plateText":"%s","vehicleType":"MOTORBIKE","detectionConfidence":0.9,
             "ocrConfidence":0.8,"vehicleConfidence":0.7,"boundingBox":null,"frameIndex":0,
             "annotatedImageBase64":"","message":"synthetic result"}
            """.formatted(plate)));
        byte[] image = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        String response = mvc.perform(multipart("/api/parking/anpr/recognize/image")
                .file(new MockMultipartFile("file", "plate.jpg", "image/jpeg", image))
                .param("operation", operation)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("evidenceId").asText();
    }

    private void deleteUnusedEvidence(String evidenceId) throws java.io.IOException {
        GateEvidence item = gateEvidence.findById(evidenceId).orElseThrow();
        if (item.getParkingSessionId() != null)
            throw new IllegalStateException("Cannot discard bound evidence in test");
        gateEvidenceStorage.delete(item.getFileName());
        if (item.getDerivedFileName() != null) gateEvidenceStorage.delete(item.getDerivedFileName());
        gateEvidence.deleteById(evidenceId);
    }

    private static java.security.KeyPair createTestJwtKeyPair() {
        try {
            var generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to create synthetic integration-test key", ex);
        }
    }

    private static String publicPem(java.security.PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded()) +
            "\n-----END PUBLIC KEY-----";
    }

    private static String privatePem(java.security.PrivateKey key) {
        return "-----BEGIN PRIVATE KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded()) +
            "\n-----END PRIVATE KEY-----";
    }

    private String createAccessToken(SystemAccount account, String sessionId) {
        java.time.Instant now = java.time.Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId("integration-test-kid").type("JWT").build(),
            JwtClaimsSet.builder().issuer("https://parking.test").subject(account.getId().toString())
                .audience(java.util.List.of("parking-desktop")).issuedAt(now).expiresAt(now.plusSeconds(60))
                .id(java.util.UUID.randomUUID().toString()).claim("sid", sessionId)
                .claim("ver", account.getSecurityVersion()).build())).getTokenValue();
    }

}
