package vn.edu.parking.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.parking.domain.GateEvidenceAsset;
import vn.edu.parking.repository.GateEvidenceAssetRepository;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.service.PrivateEvidenceStorage;
import vn.edu.parking.service.SecurityAuditService;

@RestController
public class GateEvidenceAdminController {
    private final GateEvidenceRepository evidences;
    private final GateEvidenceAssetRepository assets;
    private final PrivateEvidenceStorage storage;
    private final SecurityAuditService audit;

    public GateEvidenceAdminController(GateEvidenceRepository evidences,
            GateEvidenceAssetRepository assets, PrivateEvidenceStorage storage,
            SecurityAuditService audit) {
        this.evidences = evidences;
        this.assets = assets;
        this.storage = storage;
        this.audit = audit;
    }

    @GetMapping("/admin-data/gate-evidences/{evidenceId}")
    @Transactional
    public Map<String, Object> evidence(@PathVariable String evidenceId) {
        var evidence = evidences.findById(evidenceId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Gate Evidence"));
        List<Map<String, Object>> assetData = assets.findByEvidenceIdAndDeletedAtIsNull(evidenceId)
            .stream().map(asset -> Map.<String, Object>of(
                "id", asset.getId(),
                "role", asset.getAssetRole().name(),
                "mediaType", asset.getMediaType(),
                "sizeBytes", asset.getSizeBytes(),
                "viewUrl", "/admin-data/gate-evidences/" + evidenceId + "/assets/" + asset.getId()
            )).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", evidence.getId());
        result.put("type", evidence.getEvidenceType().name());
        result.put("operation", evidence.getOperation().name());
        result.put("source", evidence.getSourceKind().name());
        result.put("status", evidence.getStatus().name());
        result.put("plateNumber", evidence.getPlateNumber());
        result.put("vehicleType", evidence.getVehicleType());
        result.put("faceDecision", evidence.getFaceDecision());
        result.put("faceSimilarity", evidence.getFaceSimilarity());
        result.put("modelVersion", evidence.getModelVersion());
        result.put("createdAt", evidence.getCreatedAt());
        result.put("expiresAt", evidence.getExpiresAt());
        result.put("consumedAt", evidence.getConsumedAt());
        result.put("assets", assetData);
        audit.recordCurrent("GATE_EVIDENCE_VIEW", "GATE_EVIDENCE", evidenceId,
            "SUCCESS", null, evidenceId);
        return result;
    }

    @GetMapping("/admin-data/gate-evidences/{evidenceId}/assets/{assetId}")
    @Transactional
    public ResponseEntity<byte[]> asset(@PathVariable String evidenceId,
            @PathVariable String assetId) {
        GateEvidenceAsset asset = assets.findByIdAndEvidenceIdAndDeletedAtIsNull(assetId, evidenceId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy asset evidence"));
        byte[] bytes = storage.read(asset.getStorageKey(), asset.getSha256());
        audit.recordCurrent("GATE_EVIDENCE_ASSET_VIEW", "GATE_EVIDENCE_ASSET", assetId,
            "SUCCESS", null, evidenceId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(asset.getMediaType()))
            .body(bytes);
    }
}
