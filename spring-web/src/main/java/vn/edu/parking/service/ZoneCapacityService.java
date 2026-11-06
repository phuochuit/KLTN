package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.web.dto.ZoneConversionPreviewResponse;

import java.time.Instant;
import java.util.*;

@Service
public class ZoneCapacityService {

    private final ParkingZoneRepository parkingZoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final SlotConversionLogRepository slotConversionLogRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final OutboxEventService outboxEventService;

    public ZoneCapacityService(ParkingZoneRepository parkingZoneRepository,
                               ParkingSlotRepository parkingSlotRepository,
                               VehicleTypeRepository vehicleTypeRepository,
                               PricingRuleRepository pricingRuleRepository,
                               SlotConversionLogRepository slotConversionLogRepository,
                               ParkingSessionRepository parkingSessionRepository,
                               OutboxEventService outboxEventService) {
        this.parkingZoneRepository = parkingZoneRepository;
        this.parkingSlotRepository = parkingSlotRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.slotConversionLogRepository = slotConversionLogRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional(readOnly = true)
    public List<String> findBusySlotCodes(Long zoneId) {
        if (zoneId == null) {
            return List.of();
        }
        List<ParkingSlot> zoneSlots = parkingSlotRepository.findByZone_IdAndActiveTrueOrderBySlotCodeAsc(zoneId);
        Set<String> busyCodes = new TreeSet<>(SlotCodeComparator.INSTANCE);

        for (ParkingSlot s : zoneSlots) {
            if (s.getCurrentSession() != null) {
                busyCodes.add(s.getSlotCode());
            } else if (s.getAssignedVehicle() != null) {
                busyCodes.add(s.getSlotCode());
            } else if (s.getBorrowedPlate() != null && !s.getBorrowedPlate().isBlank()) {
                busyCodes.add(s.getSlotCode());
            }
        }

        // Check if there are open sessions referencing slots in this zone
        List<ParkingSession> openSessions = parkingSessionRepository.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN);
        for (ParkingSession session : openSessions) {
            parkingSlotRepository.findFirstByCurrentSessionId(session.getId()).ifPresent(slot -> {
                if (slot.getZone() != null && zoneId.equals(slot.getZone().getId())) {
                    busyCodes.add(slot.getSlotCode());
                }
            });
        }

        List<String> result = new ArrayList<>(busyCodes);
        result.sort(SlotCodeComparator.INSTANCE);
        return result;
    }

    public void ensureZoneEmptyForConversion(ParkingZone zone) {
        List<String> busyCodes = findBusySlotCodes(zone.getId());
        if (!busyCodes.isEmpty()) {
            throw new IllegalStateException("Không thể đổi loại phương tiện cho " + zone.getName()
                    + " vì khu đang có xe đỗ hoặc đã cấp xe tại các ô: " + String.join(", ", busyCodes));
        }
    }

    @Transactional
    public ZoneConversionPreviewResponse convert(Long zoneId, Long targetVehicleTypeId, boolean dryRun, boolean confirmLoss, String actorUsername) {
        if (zoneId == null) {
            throw new IllegalArgumentException("Vui lòng chọn khu vực cần chuyển đổi.");
        }
        if (targetVehicleTypeId == null) {
            throw new IllegalArgumentException("Vui lòng chọn loại phương tiện đích.");
        }

        // Bước 1: Khóa bản ghi khu
        ParkingZone zone = parkingZoneRepository.lockById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khu ID " + zoneId));

        VehicleTypeEntity newType = vehicleTypeRepository.findById(targetVehicleTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phương tiện ID " + targetVehicleTypeId));

        if (!newType.isActive()) {
            throw new IllegalArgumentException("Loại phương tiện '" + newType.getDisplayName() + "' đã ngừng hoạt động.");
        }

        if (!pricingRuleRepository.existsByVehicleTypeRef_Id(newType.getId())) {
            throw new IllegalStateException("Loại phương tiện '" + newType.getDisplayName() + "' chưa được cấu hình bảng giá.");
        }

        if (zone.getVehicleType() != null && zone.getVehicleType().getId().equals(newType.getId())) {
            throw new IllegalArgumentException("Khu đã thuộc loại phương tiện này.");
        }

        // Bước 2: Kiểm tra khu đang có xe đỗ/gán
        List<String> busyCodes = findBusySlotCodes(zoneId);
        if (!busyCodes.isEmpty()) {
            if (dryRun) {
                ZoneConversionPreviewResponse preview = new ZoneConversionPreviewResponse();
                preview.setZoneId(zone.getId());
                preview.setZoneName(zone.getName());
                preview.setOldVehicleTypeName(zone.getVehicleType() != null ? zone.getVehicleType().getDisplayName() : "Chưa xác định");
                preview.setNewVehicleTypeName(newType.getDisplayName());
                preview.setBusySlotCodes(busyCodes);
                preview.setCanConvert(false);
                preview.setWarningMessage("Khu đang có xe tại ô: " + String.join(", ", busyCodes) + " — Không thể thay đổi loại phương tiện.");
                return preview;
            }
            throw new IllegalStateException("Không thể đổi loại phương tiện cho " + zone.getName()
                    + " vì khu đang có xe đỗ hoặc đã cấp xe tại các ô: " + String.join(", ", busyCodes));
        }

        // Bước 3: Lấy danh sách ô đang hoạt động
        List<ParkingSlot> activeSlots = new ArrayList<>(parkingSlotRepository.findByZone_IdAndActiveTrueOrderBySlotCodeAsc(zoneId));
        activeSlots.sort(SlotCodeComparator.SLOT_COMPARATOR);

        // Bước 4: Tính toán quy đổi sức chứa
        int oldUnit = zone.getVehicleType() != null ? zone.getVehicleType().getCapacityUnits() : 1;
        int currentUnits = activeSlots.isEmpty() ? zone.getCapacityUnits() : activeSlots.size() * oldUnit;
        int newUnit = newType.getCapacityUnits();
        int newCount = currentUnits / newUnit; // floor division
        int remainderUnits = currentUnits % newUnit;

        // Bước 5: Chặn nếu newCount == 0
        if (newCount == 0) {
            throw new IllegalArgumentException("Không thể chuyển đổi: Sức chứa hiện tại của khu (" + currentUnits
                    + " đơn vị) không đủ tạo tối thiểu 1 ô cho loại '" + newType.getDisplayName() + "' (cần " + newUnit + " đơn vị/ô).");
        }

        boolean willLoseCapacity = remainderUnits > 0 || newCount < activeSlots.size();
        String warningMsg = willLoseCapacity
                ? "Lưu ý: Chuyển qua lại sẽ mất ô do làm tròn xuống phần dư (" + remainderUnits + " đơn vị dư)."
                : null;

        // Bước 6: Nếu dryRun, trả preview
        if (dryRun) {
            ZoneConversionPreviewResponse preview = new ZoneConversionPreviewResponse();
            preview.setZoneId(zone.getId());
            preview.setZoneName(zone.getName());
            preview.setOldVehicleTypeName(zone.getVehicleType() != null ? zone.getVehicleType().getDisplayName() : "Chưa xác định");
            preview.setNewVehicleTypeName(newType.getDisplayName());
            preview.setOldSlotCount(activeSlots.size());
            preview.setNewSlotCount(newCount);
            preview.setCapacityUnitsBefore(currentUnits);
            preview.setCapacityUnitsAfter(newCount * newUnit);
            preview.setRemainderUnits(remainderUnits);
            preview.setWillLoseCapacity(willLoseCapacity);
            preview.setWarningMessage(warningMsg);
            preview.setBusySlotCodes(List.of());
            preview.setCanConvert(true);
            return preview;
        }

        // Bước 7: Bắt buộc confirmLoss nếu có mất ô/dư sức chứa
        if (willLoseCapacity && !confirmLoss) {
            throw new IllegalStateException("Quy đổi sẽ làm giảm số ô/mất phần dư sức chứa (" + remainderUnits
                    + " đơn vị). Vui lòng xác nhận mất ô trước khi thực hiện.");
        }

        // Bước 8: Thực thi chuyển đổi
        VehicleTypeEntity oldType = zone.getVehicleType();
        int deactivatedCount = 0;
        int createdCount = 0;

        if (newCount <= activeSlots.size()) {
            // Giữ lại newCount ô đầu tiên
            for (int i = 0; i < newCount; i++) {
                ParkingSlot slot = activeSlots.get(i);
                slot.setVehicleTypeRef(newType);
                slot.setAllowedVehicleType(newType.getCategory());
                parkingSlotRepository.save(slot);
            }
            // Soft-delete các ô dư
            for (int i = newCount; i < activeSlots.size(); i++) {
                ParkingSlot slot = activeSlots.get(i);
                slot.setActive(false);
                slot.setDeactivatedReason("CONVERT_REMAINDER");
                parkingSlotRepository.save(slot);
                deactivatedCount++;
            }
        } else {
            // Cập nhật toàn bộ ô hiện có
            for (ParkingSlot slot : activeSlots) {
                slot.setVehicleTypeRef(newType);
                slot.setAllowedVehicleType(newType.getCategory());
                parkingSlotRepository.save(slot);
            }

            // Tạo thêm các ô mới
            int toCreate = newCount - activeSlots.size();
            SlotType templateType = activeSlots.isEmpty() ? SlotType.RESIDENT_RESERVED : activeSlots.get(0).getSlotType();
            String prefix = (zone.getCodePrefix() != null && !zone.getCodePrefix().isBlank())
                    ? zone.getCodePrefix().trim().toUpperCase() : "A";

            int seq = 1;
            while (createdCount < toCreate) {
                String code = prefix + (seq < 10 ? ("0" + seq) : String.valueOf(seq));
                // Phải kiểm tra trên toàn bộ bảng cả active lẫn inactive vì uk_slot_code
                if (!parkingSlotRepository.existsBySlotCodeIgnoreCase(code)) {
                    ParkingSlot ns = new ParkingSlot();
                    ns.setSlotCode(code);
                    ns.setZone(zone);
                    ns.setVehicleTypeRef(newType);
                    ns.setAllowedVehicleType(newType.getCategory());
                    ns.setSlotType(templateType);
                    ns.setActive(true);
                    parkingSlotRepository.save(ns);
                    createdCount++;
                }
                seq++;
            }
        }

        // Cập nhật Zone
        zone.setVehicleType(newType);
        zone.setCapacityUnits(newCount * newUnit);
        parkingZoneRepository.save(zone);

        // Lưu Log chuyển đổi
        SlotConversionLog log = new SlotConversionLog();
        log.setZone(zone);
        log.setOldVehicleType(oldType);
        log.setNewVehicleType(newType);
        log.setOldSlotCount(activeSlots.size());
        log.setNewSlotCount(newCount);
        log.setCapacityUnitsBefore(currentUnits);
        log.setCapacityUnitsAfter(newCount * newUnit);
        log.setDeactivatedCount(deactivatedCount);
        log.setCreatedCount(createdCount);
        log.setActorUsername(actorUsername != null ? actorUsername : "system");
        log.setConvertedAt(Instant.now());
        log.setNote("Quy đổi khu " + zone.getName() + " từ " + (oldType != null ? oldType.getDisplayName() : "N/A")
                + " sang " + newType.getDisplayName() + (remainderUnits > 0 ? (" (dư " + remainderUnits + " đv)") : ""));
        slotConversionLogRepository.save(log);

        // Phát sự kiện outbox
        try {
            outboxEventService.recordEvent("ZONE", String.valueOf(zone.getId()), "ZONE_CONVERTED",
                    Map.of("zoneId", zone.getId(), "oldType", oldType != null ? oldType.getCode() : "UNKNOWN",
                            "newType", newType.getCode(), "newCount", newCount, "remainderUnits", remainderUnits));
        } catch (Exception ex) {
            // Outbox failure should not fail conversion
        }

        ZoneConversionPreviewResponse response = new ZoneConversionPreviewResponse();
        response.setZoneId(zone.getId());
        response.setZoneName(zone.getName());
        response.setOldVehicleTypeName(oldType != null ? oldType.getDisplayName() : "Chưa xác định");
        response.setNewVehicleTypeName(newType.getDisplayName());
        response.setOldSlotCount(activeSlots.size());
        response.setNewSlotCount(newCount);
        response.setCapacityUnitsBefore(currentUnits);
        response.setCapacityUnitsAfter(newCount * newUnit);
        response.setRemainderUnits(remainderUnits);
        response.setWillLoseCapacity(willLoseCapacity);
        response.setWarningMessage(warningMsg);
        response.setCanConvert(true);
        return response;
    }
}
