package vn.edu.parking.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.service.PlateNormalizer;
import vn.edu.parking.service.ParkingService;
import vn.edu.parking.web.dto.*;

import java.util.Map;
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
    public Object slots() {
        return slots.findAllByOrderBySlotCodeAsc().stream().map(slot -> {
            String assigned = slot.getAssignedVehicle() == null ? "" : slot.getAssignedVehicle().getPlateNumber();
            String occupied = slot.getCurrentSession() == null ? "" : slot.getCurrentSession().getEntryPlate();
            String status = occupied.isBlank() ? (assigned.isBlank() ? "AVAILABLE" : "RESERVED_EMPTY")
                : (!assigned.isBlank() && assigned.equalsIgnoreCase(occupied) ? "OCCUPIED_VALID" : "OCCUPIED_MISMATCH");
            return new ParkingSlotResponse(slot.getId(), slot.getSlotCode(), slot.getZoneName(), assigned, occupied, status);
        }).toList();
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
