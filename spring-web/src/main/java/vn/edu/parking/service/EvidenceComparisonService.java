package vn.edu.parking.service;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.GateEvidenceAssetRepository;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.web.dto.EvidenceComparisonResponse;

@Service
public class EvidenceComparisonService {
    private final ParkingSessionRepository sessions;
    private final GateEvidenceRepository evidences;
    private final GateEvidenceAssetRepository assets;
    private final PrivateEvidenceStorage storage;
    private final ResidentImageStorage residentImages;
    private final SecurityAuditService audit;

    public EvidenceComparisonService(ParkingSessionRepository sessions,
            GateEvidenceRepository evidences,
            GateEvidenceAssetRepository assets,
            PrivateEvidenceStorage storage,
            ResidentImageStorage residentImages,
            SecurityAuditService audit) {
        this.sessions = sessions;
        this.evidences = evidences;
        this.assets = assets;
        this.storage = storage;
        this.residentImages = residentImages;
        this.audit = audit;
    }

    @Transactional
    public EvidenceComparisonResponse compare(Long sessionId, String plate, String exitVehicleType,
            String exitVehicleEvidenceId, String exitFaceEvidenceId) {
        ParkingSession session = resolveSession(sessionId, plate);
        GateEvidence entryVehicle = findConsumedEvidence(session.getId(), EvidenceType.VEHICLE, EvidenceOperation.ENTRY);
        GateEvidence entryFace = findConsumedEvidence(session.getId(), EvidenceType.FACE, EvidenceOperation.ENTRY);
        GateEvidence exitVehicle = resolveExitEvidence(session.getId(), EvidenceType.VEHICLE, exitVehicleEvidenceId);
        GateEvidence exitFace = resolveExitEvidence(session.getId(), EvidenceType.FACE, exitFaceEvidenceId);

        String entryPlate = PlateNormalizer.normalize(
            session.getEntryPlate() != null && !session.getEntryPlate().isBlank()
                ? session.getEntryPlate()
                : (entryVehicle != null ? entryVehicle.getPlateNumber() : ""));
        String resolvedExitPlate = PlateNormalizer.normalize(
            exitVehicle != null && exitVehicle.getPlateNumber() != null && !exitVehicle.getPlateNumber().isBlank()
                ? exitVehicle.getPlateNumber()
                : (plate != null && !plate.isBlank() ? plate
                    : (session.getExitPlate() != null ? session.getExitPlate() : "")));

        String entryType = session.getDetectedVehicleType() != null
            ? session.getDetectedVehicleType().name()
            : (entryVehicle != null && entryVehicle.getVehicleType() != null ? entryVehicle.getVehicleType() : "UNKNOWN");
        String resolvedExitType = exitVehicle != null && exitVehicle.getVehicleType() != null && !exitVehicle.getVehicleType().isBlank()
            ? exitVehicle.getVehicleType()
            : (exitVehicleType != null && !exitVehicleType.isBlank() ? exitVehicleType : entryType);

        boolean plateMatched = !entryPlate.isBlank() && !resolvedExitPlate.isBlank()
            && entryPlate.equalsIgnoreCase(resolvedExitPlate);
        boolean vehicleTypeMatched = isTypeCompatible(entryType, resolvedExitType);

        String entryFaceDecision = entryFace != null && entryFace.getFaceDecision() != null
            ? entryFace.getFaceDecision()
            : (session.isEntryFaceVerified() ? "PASS" : "NOT_EVALUATED");
        Double entryFaceSim = (entryFace != null && entryFace.getFaceSimilarity() != null)
            ? Double.valueOf(entryFace.getFaceSimilarity().doubleValue())
            : session.getEntryFaceSimilarity();

        String exitFaceDecision = exitFace != null && exitFace.getFaceDecision() != null
            ? exitFace.getFaceDecision()
            : (session.isExitFaceVerified() ? "PASS" : "NOT_EVALUATED");
        Double exitFaceSim = (exitFace != null && exitFace.getFaceSimilarity() != null)
            ? Double.valueOf(exitFace.getFaceSimilarity().doubleValue())
            : session.getExitFaceSimilarity();

        boolean faceMatched = "PASS".equalsIgnoreCase(exitFaceDecision) || session.isExitFaceVerified();

        String entryVehImg = readAssetBase64(entryVehicle, EvidenceAssetRole.ANNOTATED_VEHICLE);
        String exitVehImg = readAssetBase64(exitVehicle, EvidenceAssetRole.ANNOTATED_VEHICLE);
        String entryFaceImg = readFaceBase64(session, entryFace, true);
        String exitFaceImg = readFaceBase64(session, exitFace, false);

        String matchStatus;
        String summaryMessage;
        if (!plateMatched || (!resolvedExitType.equalsIgnoreCase("UNKNOWN") && !vehicleTypeMatched)
                || "REJECT".equalsIgnoreCase(exitFaceDecision) || "FAIL".equalsIgnoreCase(exitFaceDecision)) {
            matchStatus = "NO_MATCH";
            summaryMessage = "KHÔNG KHỚP: Biển số, loại xe hoặc khuôn mặt lúc ra không trùng với lúc vào. Cần kiểm tra hoặc xử lý thủ công.";
        } else if ("REVIEW".equalsIgnoreCase(exitFaceDecision)
                || isLowConfidence(exitVehicle)
                || (!faceMatched && !"NOT_EVALUATED".equalsIgnoreCase(exitFaceDecision))) {
            matchStatus = "REVIEW";
            summaryMessage = "CẦN XEM XÉT (REVIEW): Độ tin cậy AI thấp hoặc khuôn mặt ở ngưỡng cảnh báo. Nhân viên cần đối chiếu mắt thường.";
        } else if (plateMatched && vehicleTypeMatched) {
            matchStatus = "MATCH";
            summaryMessage = "KHỚP HỢP LỆ (MATCH): Biển số (" + entryPlate + ") và loại xe (" + entryType + ") trùng khớp giữa lúc vào và lúc ra.";
        } else {
            matchStatus = "REVIEW";
            summaryMessage = "Đang chờ nhận diện đầy đủ thông tin xe ra để đối chiếu.";
        }

        audit.recordCurrent("EVIDENCE_COMPARISON_VIEW", "PARKING_SESSION",
            String.valueOf(session.getId()), "SUCCESS", matchStatus,
            exitVehicle != null ? exitVehicle.getId() : (entryVehicle != null ? entryVehicle.getId() : null));

        return new EvidenceComparisonResponse(
            session.getId(),
            session.getStatus().name(),
            session.getEntryTime(),
            session.getExitTime(),
            entryPlate,
            resolvedExitPlate,
            plateMatched,
            entryType,
            resolvedExitType,
            vehicleTypeMatched,
            toDouble(entryVehicle == null ? null : entryVehicle.getDetectionConfidence()),
            toDouble(entryVehicle == null ? null : entryVehicle.getOcrConfidence()),
            toDouble(entryVehicle == null ? null : entryVehicle.getVehicleConfidence()),
            toDouble(exitVehicle == null ? null : exitVehicle.getDetectionConfidence()),
            toDouble(exitVehicle == null ? null : exitVehicle.getOcrConfidence()),
            toDouble(exitVehicle == null ? null : exitVehicle.getVehicleConfidence()),
            entryFaceDecision,
            entryFaceSim,
            exitFaceDecision,
            exitFaceSim,
            faceMatched,
            session.isManualOverride(),
            matchStatus,
            summaryMessage,
            entryVehicle == null ? null : entryVehicle.getId(),
            exitVehicle == null ? null : exitVehicle.getId(),
            entryFace == null ? null : entryFace.getId(),
            exitFace == null ? null : exitFace.getId(),
            entryVehImg,
            exitVehImg,
            entryFaceImg,
            exitFaceImg
        );
    }

    private ParkingSession resolveSession(Long sessionId, String plate) {
        if (sessionId != null) {
            return sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên gửi xe ID " + sessionId));
        }
        String normalized = PlateNormalizer.normalize(plate == null ? "" : plate);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập hoặc nhận diện biển số để đối chiếu ảnh vào/ra");
        }
        Optional<ParkingSession> open = sessions
            .findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(normalized, SessionStatus.OPEN);
        if (open.isPresent()) return open.get();
        return sessions.findAllByOrderByEntryTimeDesc().stream()
            .filter(s -> normalized.equalsIgnoreCase(PlateNormalizer.normalize(s.getEntryPlate())))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy lượt xe vào cho biển số " + normalized));
    }

    private GateEvidence findConsumedEvidence(Long sessionId, EvidenceType type, EvidenceOperation op) {
        return evidences.findFirstByParkingSessionIdAndEvidenceTypeAndOperationAndStatusOrderByConsumedAtDesc(
            sessionId, type, op, EvidenceStatus.CONSUMED).orElse(null);
    }

    private GateEvidence resolveExitEvidence(Long sessionId, EvidenceType type, String evidenceId) {
        if (evidenceId != null && !evidenceId.isBlank()) {
            Optional<GateEvidence> byId = evidences.findById(evidenceId.trim());
            if (byId.isPresent() && byId.get().getEvidenceType() == type) {
                return byId.get();
            }
        }
        return findConsumedEvidence(sessionId, type, EvidenceOperation.EXIT);
    }

    private String readAssetBase64(GateEvidence evidence, EvidenceAssetRole role) {
        if (evidence == null) return "";
        try {
            var assetOpt = assets.findFirstByEvidenceIdAndAssetRoleAndDeletedAtIsNull(evidence.getId(), role);
            if (assetOpt.isEmpty()) return "";
            GateEvidenceAsset asset = assetOpt.get();
            byte[] bytes = storage.read(asset.getStorageKey(), asset.getSha256());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readFaceBase64(ParkingSession session, GateEvidence faceEvidence, boolean entry) {
        String fromEvidence = readAssetBase64(faceEvidence, EvidenceAssetRole.REALTIME_FACE);
        if (!fromEvidence.isBlank()) return fromEvidence;
        if (entry) {
            String path = session.getEntryFaceImagePath();
            if (path != null && path.startsWith("evidence:")) {
                String evId = path.substring("evidence:".length()).trim();
                GateEvidence ev = evidences.findById(evId).orElse(null);
                String encoded = readAssetBase64(ev, EvidenceAssetRole.REALTIME_FACE);
                if (!encoded.isBlank()) return encoded;
            } else if (path != null && !path.isBlank()) {
                try {
                    byte[] bytes = residentImages.readStoredReference(path);
                    return Base64.getEncoder().encodeToString(bytes);
                } catch (Exception ignored) { }
            }
            if (session.getEntryMember() != null
                    && session.getEntryMember().getRegistrationFaceImagePath() != null
                    && !session.getEntryMember().getRegistrationFaceImagePath().isBlank()) {
                try {
                    byte[] bytes = residentImages.readStoredReference(
                        session.getEntryMember().getRegistrationFaceImagePath());
                    return Base64.getEncoder().encodeToString(bytes);
                } catch (Exception ignored) { }
            }
        }
        return "";
    }

    private static boolean isTypeCompatible(String entryType, String exitType) {
        if (entryType == null || exitType == null) return true;
        if ("UNKNOWN".equalsIgnoreCase(entryType) || "UNKNOWN".equalsIgnoreCase(exitType)) return true;
        if (entryType.equalsIgnoreCase(exitType)) return true;
        boolean entryCar = entryType.toUpperCase().startsWith("CAR");
        boolean exitCar = exitType.toUpperCase().startsWith("CAR");
        if (entryCar && exitCar) return true;
        boolean entryTwoWheeler = entryType.toUpperCase().contains("MOTORBIKE") || entryType.toUpperCase().contains("BICYCLE");
        boolean exitTwoWheeler = exitType.toUpperCase().contains("MOTORBIKE") || exitType.toUpperCase().contains("BICYCLE");
        return entryTwoWheeler && exitTwoWheeler;
    }

    private static boolean isLowConfidence(GateEvidence vehicle) {
        if (vehicle == null) return false;
        Double det = toDouble(vehicle.getDetectionConfidence());
        Double ocr = toDouble(vehicle.getOcrConfidence());
        return (det != null && det < 0.25) || (ocr != null && ocr < 0.45);
    }

    private static Double toDouble(BigDecimal val) {
        return val == null ? null : val.doubleValue();
    }
}
