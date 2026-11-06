package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SlotManagementService {
    private final ParkingSlotRepository slots;
    private final ParkingFloorRepository floors;
    private final ParkingZoneRepository zones;
    private final VehicleTypeRepository vehicleTypes;
    private final PricingRuleRepository prices;

    public SlotManagementService(ParkingSlotRepository slots, ParkingFloorRepository floors,
            ParkingZoneRepository zones, VehicleTypeRepository vehicleTypes,
            PricingRuleRepository prices) {
        this.slots = slots;
        this.floors = floors;
        this.zones = zones;
        this.vehicleTypes = vehicleTypes;
        this.prices = prices;
    }

    @Transactional
    public int updateSlots(List<Long> requestedIds, String slotCode, String floor,
            String zoneName, String slotType, String vehicleType) {
        List<ParkingSlot> selected = loadActiveSlots(requestedIds);
        if (selected.size() > 1 && hasText(slotCode)) {
            throw new IllegalArgumentException("Chỉ có thể đổi mã khi chọn một ô đỗ.");
        }
        ensureNotOccupied(selected, "sửa");

        String cleanCode = hasText(slotCode) ? slotCode.trim().toUpperCase() : null;
        if (cleanCode != null) {
            slots.findBySlotCodeIgnoreCase(cleanCode)
                .filter(existing -> !existing.getId().equals(selected.get(0).getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("Mã vị trí " + cleanCode + " đã được sử dụng."); });
        }

        VehicleTypeEntity targetType = null;
        if (hasText(vehicleType)) {
            targetType = vehicleTypes.findByCodeIgnoreCase(vehicleType.trim())
                .or(() -> vehicleTypes.findByDisplayNameIgnoreCase(vehicleType.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Loại phương tiện đã chọn không còn sử dụng."));
            if (!prices.existsByVehicleTypeRef_Id(targetType.getId())) {
                throw new IllegalStateException("Loại phương tiện '" + targetType.getDisplayName()
                    + "' chưa được thiết lập bảng giá. Vui lòng tạo bảng giá trước.");
            }
        }

        SlotType targetSlotType = null;
        if (hasText(slotType)) {
            targetSlotType = "VISITOR_FLEXIBLE".equalsIgnoreCase(slotType)
                || "VISITOR".equalsIgnoreCase(slotType)
                ? SlotType.VISITOR_FLEXIBLE : SlotType.RESIDENT_RESERVED;
        }

        for (ParkingSlot slot : selected) {
            if (cleanCode != null) slot.setSlotCode(cleanCode);
            VehicleTypeEntity effectiveType = targetType != null ? targetType : slot.getVehicleTypeRef();
            String effectiveFloorName = hasText(floor) ? floor.trim() : slot.getFloor();
            String effectiveZoneName = hasText(zoneName) ? zoneName.trim() : slot.getZoneName();
            if (hasText(floor) || hasText(zoneName)) {
                ParkingFloor parkingFloor = floors.findByNameIgnoreCase(effectiveFloorName).orElseGet(() ->
                    floors.save(new ParkingFloor(effectiveFloorName, false, 0, 500)));
                VehicleTypeEntity zoneType = effectiveType;
                String prefix = slot.getSlotCode().substring(0, 1);
                ParkingZone parkingZone = zones.findByFloor_IdAndNameIgnoreCase(parkingFloor.getId(), effectiveZoneName)
                    .orElseGet(() -> zones.save(new ParkingZone(parkingFloor, effectiveZoneName, prefix,
                        zoneType, zoneType != null ? zoneType.getCapacityUnits() : 1, null)));
                slot.setZone(parkingZone);
            }
            if (targetType != null) {
                slot.setVehicleTypeRef(targetType);
                slot.setAllowedVehicleType(targetType.getCategory());
            }
            if (targetSlotType != null) slot.setSlotType(targetSlotType);
        }
        slots.saveAll(selected);
        return selected.size();
    }

    @Transactional
    public int deactivateSlots(List<Long> requestedIds) {
        List<ParkingSlot> selected = loadActiveSlots(requestedIds);
        List<String> blocked = new ArrayList<>();
        for (ParkingSlot slot : selected) {
            List<String> reasons = new ArrayList<>();
            if (slot.getCurrentSession() != null) reasons.add("đang có xe");
            if (slot.getAssignedVehicle() != null) reasons.add("đã cấp cho cư dân");
            if (hasText(slot.getBorrowedPlate())) reasons.add("đang có lịch đỗ nhờ");
            if (!reasons.isEmpty()) blocked.add(slot.getSlotCode() + " (" + String.join(", ", reasons) + ")");
        }
        if (!blocked.isEmpty()) {
            throw new IllegalStateException("Chưa thể ngừng sử dụng: " + String.join("; ", blocked)
                + ". Vui lòng giải phóng hoặc bỏ gán các ô này trước.");
        }
        for (ParkingSlot slot : selected) {
            slot.setActive(false);
            slot.setDeactivatedReason("ADMIN_DEACTIVATED");
        }
        slots.saveAll(selected);
        return selected.size();
    }

    private List<ParkingSlot> loadActiveSlots(List<Long> requestedIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(requestedIds == null ? List.of() : requestedIds);
        if (ids.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn ít nhất một ô đỗ.");
        List<ParkingSlot> selected = new ArrayList<>();
        for (Long id : ids) {
            ParkingSlot slot = slots.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ đã chọn."));
            if (!slot.isActive()) throw new IllegalStateException("Ô " + slot.getSlotCode() + " đã ngừng sử dụng.");
            selected.add(slot);
        }
        return selected;
    }

    private void ensureNotOccupied(List<ParkingSlot> selected, String action) {
        List<String> occupied = selected.stream().filter(slot -> slot.getCurrentSession() != null)
            .map(ParkingSlot::getSlotCode).toList();
        if (!occupied.isEmpty()) {
            throw new IllegalStateException("Không thể " + action + " ô đang có xe: " + String.join(", ", occupied) + ".");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
