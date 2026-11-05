package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.GateEvidenceAssetRepository;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.repository.ParkingSessionRepository;

@Service
public class GuestRegisteredFaceService {
    private final GateEvidenceConsumptionService consumption;
    private final ParkingSessionRepository parkingSessions;
    private final GateEvidenceRepository evidences;
    private final GateEvidenceAssetRepository assets;
    private final PrivateEvidenceStorage storage;

    public GuestRegisteredFaceService(GateEvidenceConsumptionService consumption,
            ParkingSessionRepository parkingSessions, GateEvidenceRepository evidences,
            GateEvidenceAssetRepository assets, PrivateEvidenceStorage storage) {
        this.consumption = consumption;
        this.parkingSessions = parkingSessions;
        this.evidences = evidences;
        this.assets = assets;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public RegisteredGuestFace loadForExit(String exitVehicleEvidenceId) {
        return loadForExit(exitVehicleEvidenceId, null);
    }

    /**
     * Đối chiếu khuôn mặt khách vãng lai. Khi nhân viên đã chọn đúng lượt xe đang
     * đỗ (ví dụ lượt vào dùng mã tạm NPL vì chưa đọc được biển), dùng thẳng
     * sessionId thay vì dò lại theo biển số.
     */
    @Transactional(readOnly = true)
    public RegisteredGuestFace loadForExit(String exitVehicleEvidenceId, Long sessionId) {
        var trusted = consumption.resolveForPreview(exitVehicleEvidenceId, null,
            EvidenceOperation.EXIT, null);
        if (trusted.legacy() || trusted.plateNumber() == null || trusted.plateNumber().isBlank())
            throw new IllegalStateException("Không xác định được biển số từ evidence xe ra");

        ParkingSession parkingSession = sessionId != null
            ? parkingSessions.findById(sessionId)
                .filter(item -> item.getStatus() == SessionStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException(
                    "Lượt xe vào đã đóng hoặc không còn mở; vui lòng chọn lại xe đang đỗ"))
            : parkingSessions
                .findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
                    trusted.plateNumber(), SessionStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy phiên gửi xe đang mở"));
        if (parkingSession.getVehicle() != null)
            throw new IllegalStateException("Đối chiếu guest face chỉ dành cho khách vãng lai");

        GateEvidence entryFace = evidences
            .findFirstByParkingSessionIdAndEvidenceTypeAndOperationAndStatusOrderByConsumedAtDesc(
                parkingSession.getId(), EvidenceType.FACE, EvidenceOperation.ENTRY,
                EvidenceStatus.CONSUMED)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy face evidence lúc vào"));
        GateEvidenceAsset asset = assets
            .findFirstByEvidenceIdAndAssetRoleAndDeletedAtIsNull(
                entryFace.getId(), EvidenceAssetRole.REALTIME_FACE)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy ảnh khuôn mặt lúc vào"));

        byte[] bytes = storage.read(asset.getStorageKey(), asset.getSha256());
        return new RegisteredGuestFace(parkingSession.getId(), entryFace.getId(), bytes,
            asset.getMediaType());
    }

    public record RegisteredGuestFace(Long parkingSessionId, String entryEvidenceId,
        byte[] bytes, String mediaType) { }
}
