package vn.edu.parking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

@Service
public class GateEvidenceService {
    private final ObjectMapper json;
    private final SystemAccountRepository accounts;
    private final DesktopSessionRepository desktopSessions;
    private final FamilyMemberRepository members;
    private final GateEvidenceRepository evidences;
    private final GateEvidenceAssetRepository assets;
    private final PrivateEvidenceStorage storage;
    private final Duration ttl;

    public GateEvidenceService(ObjectMapper json, SystemAccountRepository accounts,
            DesktopSessionRepository desktopSessions, FamilyMemberRepository members,
            GateEvidenceRepository evidences, GateEvidenceAssetRepository assets,
            PrivateEvidenceStorage storage,
            @Value("${parking.evidence.ttl-minutes:10}") long ttlMinutes) {
        if (ttlMinutes < 1 || ttlMinutes > 60)
            throw new IllegalArgumentException("Evidence TTL phải từ 1 đến 60 phút");
        this.json = json; this.accounts = accounts; this.desktopSessions = desktopSessions;
        this.members = members; this.evidences = evidences; this.assets = assets;
        this.storage = storage; this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Transactional
    public CreatedEvidence createVehicle(Authentication authentication, EvidenceOperation operation,
            EvidenceSourceKind source, String expectedRequestId, byte[] upstreamJson) {
        Identity identity = identity(authentication);
        ObjectNode result = parse(upstreamJson);
        validateCommon(result, expectedRequestId);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var evidence = new GateEvidence(id, EvidenceType.VEHICLE, operation, source,
            identity.account(), identity.session(), expectedRequestId, requiredText(result, "modelVersion"),
            sha256(upstreamJson), now, now.plus(ttl));
        evidence.setVehicleResult(text(result, "evidenceId"),
            PlateNormalizer.normalize(requiredText(result, "plateText")), requiredText(result, "vehicleType"),
            number(result, "detectionConfidence"), number(result, "ocrConfidence"),
            number(result, "vehicleConfidence"), integer(result, "frameIndex"));
        return persistWithImage(evidence, result, "annotatedImageBase64",
            EvidenceAssetRole.ANNOTATED_VEHICLE);
    }

    @Transactional
    public CreatedEvidence createFace(Authentication authentication, EvidenceOperation operation,
            EvidenceSourceKind source, Long familyMemberId, String expectedRequestId, byte[] upstreamJson) {
        Identity identity = identity(authentication);
        ObjectNode result = parse(upstreamJson);
        validateCommon(result, expectedRequestId);
        FamilyMember member = familyMemberId == null ? null : members.findById(familyMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên đối chiếu"));
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var evidence = new GateEvidence(id, EvidenceType.FACE, operation, source,
            identity.account(), identity.session(), expectedRequestId, requiredText(result, "modelVersion"),
            sha256(upstreamJson), now, now.plus(ttl));
        evidence.setFaceResult(text(result, "evidenceId"), member, requiredText(result, "decision"),
            number(result, "similarity"), number(result, "matchThreshold"), number(result, "reviewThreshold"));
        evidence.setLivenessResult(
            vn.edu.parking.domain.LivenessStatus.fromDecision(text(result, "livenessDecision")),
            number(result, "livenessConfidence"), "anpr-service", text(result, "livenessModel"));
        return persistWithImage(evidence, result, "realtimeImageBase64", EvidenceAssetRole.REALTIME_FACE);
    }

    private void applyQuality(GateEvidence evidence, ObjectNode result) {
        com.fasterxml.jackson.databind.JsonNode quality = result.get("quality");
        if (quality == null || !quality.isObject()) return;
        String status = quality.path("status").asText("");
        String version = quality.path("version").asText("");
        StringBuilder codes = new StringBuilder();
        for (com.fasterxml.jackson.databind.JsonNode warning : quality.path("warnings")) {
            String code = warning.path("code").asText("");
            if (code.isBlank()) continue;
            if (codes.length() > 0) codes.append(',');
            codes.append(code);
        }
        if (status.isBlank() && codes.length() == 0) return;
        String warnings = codes.length() > 400 ? codes.substring(0, 400) : codes.toString();
        evidence.setQuality(status.isBlank() ? null : status,
            warnings.isBlank() ? null : warnings, version.isBlank() ? null : version);
    }

    private CreatedEvidence persistWithImage(GateEvidence evidence, ObjectNode result,
            String imageField, EvidenceAssetRole role) {
        applyQuality(evidence, result);
        PrivateEvidenceStorage.StoredAsset stored = null;
        try {
            String encoded = requiredText(result, imageField);
            int comma = encoded.indexOf(',');
            byte[] image = Base64.getDecoder().decode(comma >= 0 ? encoded.substring(comma + 1) : encoded);
            stored = storage.storeImage(evidence.getId(), role, image);
            evidences.save(evidence);
            assets.save(new GateEvidenceAsset(UUID.randomUUID().toString(), evidence, role,
                stored.storageKey(), stored.sha256(), stored.mediaType(), stored.sizeBytes(),
                stored.width(), stored.height(), Instant.now()));
            evidence.markReady();
            evidences.save(evidence);
            result.put("evidenceId", evidence.getId());
            return new CreatedEvidence(evidence.getId(), json.writeValueAsBytes(result));
        } catch (IllegalArgumentException ex) {
            if (stored != null) storage.delete(stored.storageKey());
            throw ex;
        } catch (Exception ex) {
            if (stored != null) storage.delete(stored.storageKey());
            throw new IllegalStateException("Không thể tạo Gate Evidence", ex);
        }
    }

    private Identity identity(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt))
            throw new IllegalArgumentException("Gate Evidence yêu cầu Desktop JWT");
        Long accountId;
        try { accountId = Long.valueOf(jwt.getToken().getSubject()); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("JWT thiếu account ID hợp lệ", ex); }
        String sessionId = jwt.getToken().getClaimAsString("sid");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("JWT thiếu Desktop session");
        SystemAccount account = accounts.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản tạo evidence"));
        DesktopSession session = desktopSessions.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Desktop session tạo evidence"));
        if (!session.isActiveAt(Instant.now())) throw new IllegalArgumentException("Desktop session đã hết hiệu lực");
        return new Identity(account, session);
    }

    private ObjectNode parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Kết quả AI trống");
        try {
            JsonNode node = json.readTree(bytes);
            if (!(node instanceof ObjectNode object)) throw new IllegalArgumentException("Kết quả AI không phải JSON object");
            return object;
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Kết quả AI không đúng JSON", ex); }
    }

    private static void validateCommon(ObjectNode result, String expectedRequestId) {
        if (expectedRequestId == null || !expectedRequestId.equals(requiredText(result, "requestId")))
            throw new IllegalArgumentException("Request ID của kết quả AI không khớp");
        String contract = requiredText(result, "contractVersion");
        if (!"1.0".equals(contract)) throw new IllegalArgumentException("Contract AI không được hỗ trợ: " + contract);
    }

    private static String requiredText(ObjectNode node, String name) {
        String value = text(node, name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Kết quả AI thiếu " + name);
        return value;
    }
    private static String text(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }
    private static Double number(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.doubleValue();
    }
    private static Integer integer(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.intValue();
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 không khả dụng", ex); }
    }

    private record Identity(SystemAccount account, DesktopSession session) { }
    public record CreatedEvidence(String evidenceId, byte[] responseBody) { }
}
