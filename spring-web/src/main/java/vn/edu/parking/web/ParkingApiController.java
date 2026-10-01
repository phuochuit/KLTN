package vn.edu.parking.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.domain.SlotStatusOverride;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.service.PlateNormalizer;
import vn.edu.parking.service.ParkingService;
import vn.edu.parking.web.dto.*;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;
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

    public ParkingApiController(ParkingService parkingService, ParkingSessionRepository sessions,
                                VehicleRepository vehicles, ParkingCardRepository cards,
                                DataSource dataSource, ParkingSlotRepository slots) {
        this.parkingService = parkingService;
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.cards = cards;
        this.dataSource = dataSource;
        this.slots = slots;
    }

    @PostMapping("/entry")
    public ParkingResponse entry(@Valid @RequestBody EntryRequest request) { return parkingService.enter(request); }

    @PostMapping("/exit-preview")
    public ParkingResponse preview(@Valid @RequestBody ExitRequest request) { return parkingService.previewExit(request); }

    @PostMapping("/exit-confirm")
    public ParkingResponse confirm(@Valid @RequestBody ExitRequest request) { return parkingService.confirmExit(request); }

    @GetMapping("/open")
    public Object openSessions() { return sessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN); }

    @GetMapping("/lookup")
    public ResidentLookupResponse lookup(@RequestParam(required = false, defaultValue = "") String plate,
                                         @RequestParam(required = false, defaultValue = "") String cardCode) {
        String normalizedPlate = PlateNormalizer.normalize(plate);
        var vehicle = normalizedPlate.isBlank() ? null
            : vehicles.findByPlateNumberIgnoreCase(normalizedPlate).orElse(null);
        var card = cardCode.isBlank() ? null
            : cards.findByCardCodeIgnoreCase(cardCode.trim()).orElse(null);

        if (vehicle == null && card != null) vehicle = card.getVehicle();
        if (vehicle == null) {
            String guestFace = normalizedPlate.isBlank() ? "" : sessions
                .findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(normalizedPlate, SessionStatus.OPEN)
                .map(s -> s.getEntryFaceImagePath() == null ? "" : s.getEntryFaceImagePath()).orElse("");
            return new ResidentLookupResponse(false, card == null, normalizedPlate,
                "Khách vãng lai", "", "", "", cardCode.trim().toUpperCase(),
                "", null, false, "Biển số chưa được đăng ký trong danh sách cư dân", java.util.List.of(), guestFace);
        }

        boolean cardMatched = card == null || (card.getVehicle() != null
            && card.getVehicle().getId().equals(vehicle.getId())
            && (normalizedPlate.isBlank() || vehicle.getPlateNumber().equalsIgnoreCase(normalizedPlate)));
        boolean monthlyValid = cardMatched && card != null && card.isMonthlyValid(LocalDate.now());
        String message;
        if (!cardMatched) message = "Thẻ không thuộc biển số đang nhận diện";
        else if (card == null) message = "Xe cư dân đã đăng ký; chưa nhập thẻ nên sẽ tính giá lượt";
        else if (card.getPassType() == PassType.MONTHLY && monthlyValid)
            message = "Gói tháng còn hiệu lực đến " + card.getValidUntil();
        else if (card.getPassType() == PassType.MONTHLY)
            message = "Gói tháng đã hết hạn hoặc chưa đến ngày hiệu lực";
        else message = "Thẻ vé lượt đang hoạt động";
        if (!vehicle.getAuthorizedMemberNames().isBlank()) {
            message += "; người được phép sử dụng: " + vehicle.getAuthorizedMemberNames();
        }

        var authorized = vehicle.getAuthorizedMembers().stream().filter(vn.edu.parking.domain.FamilyMember::isActive)
            .map(m -> new AuthorizedMemberResponse(m.getId(), m.getFullName(), m.getRelationshipToHead(),
                m.getRegistrationFaceImagePath(), m.getRegistrationFaceImagePath() != null && !m.getRegistrationFaceImagePath().isBlank()))
            .toList();
        return new ResidentLookupResponse(true, cardMatched, vehicle.getPlateNumber(),
            vehicle.getEffectiveOwnerName(), vehicle.getOwnerPhone(), vehicle.getEffectiveApartmentNumber(),
            vehicle.getVehicleType().name(), card == null ? "" : card.getCardCode(),
            card == null ? "" : card.getPassType().name(), card == null ? null : card.getValidUntil(),
            monthlyValid, message, authorized, "");
    }

    @GetMapping("/slots")
    public List<ParkingSlotResponse> slots() {
        return slots.findAllByOrderBySlotCodeAsc().stream()
            .map(parkingService::toSlotResponse)
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
        Set<Long> occupiedSessionIds = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getCurrentSession() != null)
            .map(s -> s.getCurrentSession().getId())
            .collect(Collectors.toSet());

        return sessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN).stream()
            .filter(s -> !occupiedSessionIds.contains(s.getId()))
            .map(s -> Map.<String, Object>of(
                "sessionId", s.getId(),
                "plateNumber", s.getEntryPlate(),
                "vehicleType", s.getDetectedVehicleType().name(),
                "entryTime", s.getEntryTime().toString(),
                "ownerName", s.getVehicle() == null ? "Khách vãng lai" : s.getVehicle().getEffectiveOwnerName()
            )).toList();
    }

    @GetMapping("/vehicles-unassigned")
    public List<Map<String, Object>> vehiclesUnassigned() {
        Set<Long> assignedVehicleIds = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getAssignedVehicle() != null)
            .map(s -> s.getAssignedVehicle().getId())
            .collect(Collectors.toSet());

        return vehicles.findAll().stream()
            .filter(v -> v.isActive() && !assignedVehicleIds.contains(v.getId()))
            .map(v -> Map.<String, Object>of(
                "id", v.getId(),
                "plateNumber", v.getPlateNumber(),
                "ownerName", v.getEffectiveOwnerName(),
                "apartmentNumber", v.getEffectiveApartmentNumber(),
                "vehicleType", v.getVehicleType().name()
            )).toList();
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
    ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
