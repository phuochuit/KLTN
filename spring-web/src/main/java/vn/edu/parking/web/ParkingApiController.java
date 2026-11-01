package vn.edu.parking.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.ParkingIdempotencyService;
import vn.edu.parking.service.ParkingService;
import vn.edu.parking.service.PlateNormalizer;
import vn.edu.parking.service.ZoneCapacityService;
import vn.edu.parking.web.dto.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import javax.sql.DataSource;

@RestController
@RequestMapping("/api/parking")
public class ParkingApiController {
    private final ParkingService parkingService;
    private final ParkingSessionRepository sessions;
    private final VehicleRepository vehicles;
    private final ParkingCardRepository cards;
    private final DataSource dataSource;
    private final ParkingSlotRepository slots;
    private final ParkingIdempotencyService idempotency;
    private final ParkingZoneRepository parkingZoneRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneCapacityService zoneCapacityService;

    public ParkingApiController(ParkingService parkingService, ParkingSessionRepository sessions,
                                VehicleRepository vehicles, ParkingCardRepository cards,
                                DataSource dataSource, ParkingSlotRepository slots,
                                ParkingIdempotencyService idempotency,
                                ParkingZoneRepository parkingZoneRepository,
                                VehicleTypeRepository vehicleTypeRepository,
                                PricingRuleRepository pricingRuleRepository,
                                ZoneCapacityService zoneCapacityService) {
        this.parkingService = parkingService;
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.cards = cards;
        this.dataSource = dataSource;
        this.slots = slots;
        this.idempotency = idempotency;
        this.parkingZoneRepository = parkingZoneRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.zoneCapacityService = zoneCapacityService;
    }

    @PostMapping("/entry")
    public ParkingResponse entry(@Valid @RequestBody EntryRequest request) {
        return idempotency.execute(request.operationId(), "ENTRY", request,
            () -> parkingService.enter(request));
    }

    @PostMapping("/exit-preview")
    public ParkingResponse preview(@Valid @RequestBody ExitRequest request) {
        return parkingService.previewExit(request);
    }

    @PostMapping("/exit-confirm")
    public ParkingResponse confirm(@Valid @RequestBody ExitRequest request) {
        return idempotency.execute(request.operationId(), "EXIT", request,
            () -> parkingService.confirmExit(request));
    }

    @GetMapping("/open")
    public List<ParkingResponse> openSessions() {
        return sessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN).stream()
            .map(s -> new ParkingResponse(s.getId(), s.getEntryPlate(),
                s.getVehicle() == null ? "Khách vãng lai" : s.getVehicle().getEffectiveOwnerName(),
                s.getDetectedVehicleType().name(), s.getEntryPlate(), s.getStatus().name(),
                s.getEntryTime(), s.getExitTime(), s.getFee(), "Đang đỗ xe", false))
            .toList();
    }

    @GetMapping("/lookup")
    public ResidentLookupResponse lookup(@RequestParam(required = false) String plate,
                                         @RequestParam(required = false) String cardCode) {
        String normalizedPlate = PlateNormalizer.normalize(plate);
        String normalizedCard = PlateNormalizer.normalize(cardCode);
        Vehicle vehicle = !normalizedPlate.isBlank()
            ? vehicles.findByPlateNumberIgnoreCase(normalizedPlate).orElse(null)
            : null;
        ParkingCard card = !normalizedCard.isBlank()
            ? cards.findByCardCodeIgnoreCase(normalizedCard).orElse(null)
            : (!normalizedPlate.isBlank() ? cards.findByCardCodeIgnoreCase(normalizedPlate).orElse(null) : null);

        if (vehicle == null && card != null && card.getVehicle() != null) {
            vehicle = card.getVehicle();
        }

        if (vehicle == null) {
            return new ResidentLookupResponse(false, false, normalizedPlate, "", "", "", "",
                card == null ? "" : card.getCardCode(), card == null ? "" : card.getPassType().name(),
                card == null ? null : card.getValidUntil(), false, "Khách vãng lai; chưa đăng ký thông tin cư dân",
                List.of(), false);
        }

        boolean packageValid = card != null && card.isPrepaidPackageValid(LocalDate.now());
        boolean cardMatched = card != null && card.getVehicle() != null
            && card.getVehicle().getId().equals(vehicle.getId());
        String message;
        if (!cardMatched) {
            message = "Xe cư dân nhưng thẻ chưa trùng khớp hoặc chưa kích hoạt gói";
        } else if (packageValid) {
            message = "Xe cư dân có " + card.getPassType().getDisplayName() + " hợp lệ";
        } else if (card.getPassType() == PassType.DAILY || card.getPassType() == PassType.MONTHLY) {
            message = "Gói gửi xe của cư dân đã hết hạn hoặc chưa đến ngày bắt đầu";
        } else {
            message = "Xe cư dân chưa mua gói; tính giá theo lượt";
        }

        List<AuthorizedMemberResponse> authorized = vehicle.getAuthorizedMembers().stream()
            .filter(FamilyMember::isActive)
            .map(member -> new AuthorizedMemberResponse(member.getId(), member.getFullName(),
                member.getRelationshipToHead() == null ? "" : member.getRelationshipToHead(),
                member.getRegistrationFaceImagePath() != null && !member.getRegistrationFaceImagePath().isBlank()))
            .toList();
        return new ResidentLookupResponse(true, cardMatched, vehicle.getPlateNumber(),
            vehicle.getEffectiveOwnerName(), vehicle.getOwnerPhone(), vehicle.getEffectiveApartmentNumber(),
            vehicle.getVehicleType().name(), card == null ? "" : card.getCardCode(),
            card == null ? "" : card.getPassType().name(), card == null ? null : card.getValidUntil(),
            packageValid, message, authorized, false);
    }

    @GetMapping("/slots")
    public List<ParkingSlotResponse> slots() {
        List<ParkingSlot> list = new ArrayList<>(slots.findByActiveTrueOrderBySlotCodeAsc());
        list.sort(SlotCodeComparator.SLOT_COMPARATOR);
        return list.stream()
            .map(parkingService::toSlotResponse)
            .toList();
    }

    @GetMapping("/slots/{id}")
    public ParkingSlotResponse getSlot(@PathVariable Long id) {
        ParkingSlot slot = slots.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ ID " + id));
        return parkingService.toSlotResponse(slot);
    }

    @GetMapping("/zones")
    public List<ParkingZoneDto> listZones() {
        List<ParkingZone> zones = parkingZoneRepository.findByActiveTrueOrderByNameAsc();
        List<ParkingZoneDto> dtoList = new ArrayList<>();
        for (ParkingZone z : zones) {
            ParkingZoneDto dto = new ParkingZoneDto();
            dto.setId(z.getId());
            if (z.getFloor() != null) {
                dto.setFloorId(z.getFloor().getId());
                dto.setFloorName(z.getFloor().getName());
                dto.setBasement(z.getFloor().isBasement());
            }
            dto.setName(z.getName());
            dto.setCodePrefix(z.getCodePrefix());
            if (z.getVehicleType() != null) {
                dto.setVehicleTypeId(z.getVehicleType().getId());
                dto.setVehicleTypeCode(z.getVehicleType().getCode());
                dto.setVehicleTypeName(z.getVehicleType().getDisplayName());
            } else {
                dto.setVehicleTypeName("Chưa xác định");
            }
            dto.setCapacityUnits(z.getCapacityUnits());
            dto.setSide(z.getSide());
            dto.setActive(z.isActive());

            List<ParkingSlot> activeSlots = slots.findByZone_IdAndActiveTrueOrderBySlotCodeAsc(z.getId());
            dto.setActiveSlotCount(activeSlots.size());
            dto.setOccupiedSlotCodes(zoneCapacityService.findBusySlotCodes(z.getId()));
            dtoList.add(dto);
        }
        return dtoList;
    }

    @GetMapping("/vehicle-types")
    public List<VehicleTypeDto> listVehicleTypes() {
        return vehicleTypeRepository.findByActiveTrueOrderByDisplayNameAsc().stream()
                .map(vt -> new VehicleTypeDto(
                        vt.getId(),
                        vt.getCode(),
                        vt.getDisplayName(),
                        vt.getCategory().name(),
                        vt.getCapacityUnits(),
                        pricingRuleRepository.existsByVehicleTypeRef_Id(vt.getId()),
                        vt.isActive()
                ))
                .toList();
    }

    @PostMapping("/slots/{id}/assign")
    public Map<String, Object> assignVehicle(@PathVariable Long id, @RequestBody SlotAssignRequest req) {
        parkingService.assignVehicleToSlot(id, req.vehicleId());
        return Map.of("success", true, "message", "Đã cập nhật gán xe cho ô đỗ");
    }

    @PostMapping("/slots/{id}/borrow")
    public Map<String, Object> borrowSlot(@PathVariable Long id, @RequestBody SlotBorrowRequest req) {
        parkingService.setupBorrowing(id, req.borrowedPlate(), req.hours() == null ? 2 : req.hours(), req.borrowNotes());
        return Map.of("success", true, "message", "Đã thiết lập xe đỗ nhờ thành công");
    }

    @PostMapping("/slots/{id}/cancel-borrow")
    public Map<String, Object> cancelBorrow(@PathVariable Long id) {
        parkingService.cancelBorrowing(id);
        return Map.of("success", true, "message", "Đã hủy đỗ nhờ");
    }

    @PostMapping("/slots/{id}/status")
    public Map<String, Object> setStatusOverride(@PathVariable Long id, @RequestBody SlotStatusRequest req) {
        SlotStatusOverride override = SlotStatusOverride.valueOf(req.statusOverride().toUpperCase());
        parkingService.setSlotStatusOverride(id, override);
        return Map.of("success", true, "message", "Đã đổi trạng thái ô thành " + override.getDisplayName());
    }

    @PostMapping("/slots/{id}/release")
    public Map<String, Object> releaseSlot(@PathVariable Long id) {
        parkingService.releaseSlot(id);
        return Map.of("success", true, "message", "Đã giải phóng ô đỗ");
    }

    @PostMapping("/slots/{id}/dispatch-session")
    public Map<String, Object> dispatchSession(@PathVariable Long id, @RequestBody DispatchSessionRequest req) {
        parkingService.dispatchSessionToSlot(id, req.sessionId());
        return Map.of("success", true, "message", "Đã điều phối xe vào ô");
    }

    @GetMapping("/recent-unassigned")
    public List<Map<String, Object>> recentUnassigned() {
        Map<Long, String> sessionSlotMap = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getCurrentSession() != null)
            .collect(Collectors.toMap(
                s -> s.getCurrentSession().getId(),
                ParkingSlot::getSlotCode,
                (oldVal, newVal) -> oldVal
            ));

        return sessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN).stream()
            .limit(30)
            .map(s -> {
                String currentSlot = sessionSlotMap.get(s.getId());
                String slotInfo = currentSlot != null ? " (Đang ở ô " + currentSlot + ")" : " (Chưa có ô)";
                return Map.<String, Object>of(
                    "sessionId", s.getId(),
                    "plateNumber", s.getEntryPlate(),
                    "vehicleType", s.getDetectedVehicleType().name(),
                    "entryTime", s.getEntryTime().toString(),
                    "ownerName", (s.getVehicle() == null ? "Khách vãng lai" : s.getVehicle().getEffectiveOwnerName()) + slotInfo
                );
            }).toList();
    }

    @GetMapping("/vehicles-unassigned")
    public List<Map<String, Object>> vehiclesUnassigned() {
        Set<Long> assignedVehicleIds = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getAssignedVehicle() != null)
            .map(s -> s.getAssignedVehicle().getId())
            .collect(Collectors.toSet());

        return vehicles.findAll().stream()
            .filter(v -> v.isActive() && !assignedVehicleIds.contains(v.getId()))
            .map(v -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", v.getId());
                map.put("plateNumber", v.getPlateNumber());
                map.put("ownerName", v.getEffectiveOwnerName());
                map.put("apartmentNumber", v.getEffectiveApartmentNumber());
                map.put("vehicleType", v.getVehicleType().name());
                map.put("vehicleTypeName", v.getVehicleTypeRef() != null ? v.getVehicleTypeRef().getDisplayName() : v.getVehicleType().getDisplayName());
                return map;
            }).toList();
    }

    @PostMapping("/slots/batch-generate")
    public Map<String, Object> batchGenerate(@RequestBody BatchSlotGenerateRequest req) {
        int count = parkingService.batchGenerateSlots(req);
        return Map.of("success", true, "createdCount", count, "message", "Đã tạo thành công " + count + " ô đỗ");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        String database = "Unknown";
        try (var connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName();
        } catch (Exception ignored) { }
        return Map.of("status", "UP", "service", "parking-web",
            "version", "4.0-face-slots", "database", database);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
