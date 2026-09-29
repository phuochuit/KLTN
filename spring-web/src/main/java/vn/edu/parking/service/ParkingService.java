package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.web.dto.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class ParkingService {
    private final VehicleRepository vehicles;
    private final ParkingCardRepository cards;
    private final ParkingSessionRepository sessions;
    private final PricingRuleRepository pricingRules;
    private final FamilyMemberRepository members;
    private final ParkingSlotRepository slots;
    private final ResidentImageStorage imageStorage;

    public ParkingService(VehicleRepository vehicles, ParkingCardRepository cards,
                          ParkingSessionRepository sessions, PricingRuleRepository pricingRules,
                          FamilyMemberRepository members, ParkingSlotRepository slots,
                          ResidentImageStorage imageStorage) {
        this.vehicles = vehicles;
        this.cards = cards;
        this.sessions = sessions;
        this.pricingRules = pricingRules;
        this.members = members;
        this.slots = slots;
        this.imageStorage = imageStorage;
    }

    @Transactional
    public ParkingResponse enter(EntryRequest request) {
        String plate = requirePlate(request.plateNumber());
        if (sessions.existsByEntryPlateIgnoreCaseAndStatus(plate, SessionStatus.OPEN)) {
            throw new IllegalStateException("Xe " + plate + " đã có lượt đang mở");
        }

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase(plate).orElse(null);
        FamilyMember driver = verifyDriver(vehicle, request.familyMemberId(), request.faceVerified(),
            request.faceSimilarity(), request.manualOverride());
        ParkingCard card = resolveCard(request.cardCode());
        VehicleType cameraType = parseDetectedType(request.vehicleType());
        boolean warning = false;
        String message;

        if (card != null && card.getStatus() != CardStatus.ACTIVE) {
            throw new IllegalStateException("Thẻ không hoạt động");
        }

        boolean cardMatches = card != null && card.getVehicle() != null
            && card.getVehicle().getPlateNumber().equalsIgnoreCase(plate);

        if (vehicle == null) {
            warning = request.realtimeFaceImageBase64() == null || request.realtimeFaceImageBase64().isBlank();
            message = warning ? "Xe chưa đăng ký; cần chụp khuôn mặt khách vãng lai"
                : "Khách vãng lai đã chụp khuôn mặt lúc vào";
        } else if (card == null) {
            message = "Đã nhận diện xe đăng ký của " + vehicle.getEffectiveOwnerName()
                + "; không quét thẻ nên áp dụng giá lượt";
        } else if (!cardMatches) {
            warning = true;
            message = "Thẻ " + card.getCardCode() + " thuộc xe "
                + card.getVehicle().getPlateNumber() + ", không khớp biển số " + plate;
        } else if (card.getPassType() == PassType.MONTHLY
                   && !card.isMonthlyValid(LocalDateTime.now().toLocalDate())) {
            warning = true;
            message = "Gói tháng đã hết hạn; lượt này sẽ tính theo bảng giá lượt";
        } else if (card.getPassType() == PassType.MONTHLY) {
            message = "Xe cư dân hợp lệ; gói tháng còn hạn đến " + card.getValidUntil();
        } else {
            message = "Xe đăng ký hợp lệ; thẻ đang sử dụng vé lượt";
        }
        if (vehicle != null && cameraType != null
            && cameraType.isCar() != vehicle.getVehicleType().isCar()) {
            warning = true;
            message += "; CẢNH BÁO: loại xe camera nhận diện không khớp hồ sơ đăng ký";
        }

        ParkingSession session = new ParkingSession();
        session.setVehicle(vehicle);
        session.setParkingCard(card);
        session.setEntryPlate(plate);
        session.setEntryTime(LocalDateTime.now());
        session.setStatus(SessionStatus.OPEN);
        session.setDetectedVehicleType(resolveVehicleType(request.vehicleType(), vehicle));
        session.setManualOverride(request.manualOverride());
        session.setEntryMember(driver);
        session.setEntryFaceVerified(request.faceVerified());
        session.setEntryFaceSimilarity(request.faceSimilarity());
        if (vehicle == null) {
            String guestFace = imageStorage.saveCaptured(request.realtimeFaceImageBase64());
            if (guestFace == null && !request.manualOverride())
                throw new IllegalStateException("Khách vãng lai phải chụp khuôn mặt realtime lúc vào");
            session.setEntryFaceImagePath(guestFace);
        }
        session = sessions.save(session);
        assignSlot(session);
        return toResponse(session, message, warning);
    }

    @Transactional(readOnly = true)
    public ParkingResponse previewExit(ExitRequest request) {
        String plate = requirePlate(request.plateNumber());
        ParkingSession session = findOpenSession(plate, request.cardCode());
        verifyDriver(session.getVehicle(), request.familyMemberId(), request.faceVerified(),
            request.faceSimilarity(), request.manualOverride());
        verifyGuestExit(session, request.faceVerified(), request.manualOverride());
        BigDecimal fee = calculateFee(session, LocalDateTime.now());
        boolean warning = !session.getEntryPlate().equalsIgnoreCase(plate);
        String message = fee.signum() == 0 && hasValidMonthlyPass(session)
            ? "Gói tháng còn hiệu lực, phí lượt bằng 0"
            : (warning ? "Biển số ra không khớp biển số vào" : "Tìm thấy lượt xe, có thể xác nhận ra");
        return new ParkingResponse(session.getId(), plate, owner(session), type(session), cardCode(session),
            "PREVIEW", session.getEntryTime(), null, fee, message, warning);
    }

    @Transactional
    public ParkingResponse confirmExit(ExitRequest request) {
        String plate = requirePlate(request.plateNumber());
        ParkingSession session = findOpenSession(plate, request.cardCode());
        FamilyMember driver = verifyDriver(session.getVehicle(), request.familyMemberId(), request.faceVerified(),
            request.faceSimilarity(), request.manualOverride());
        verifyGuestExit(session, request.faceVerified(), request.manualOverride());
        LocalDateTime now = LocalDateTime.now();
        boolean warning = !session.getEntryPlate().equalsIgnoreCase(plate);
        if (warning && !request.manualOverride()) {
            throw new IllegalStateException("Biển số không khớp; cần bật xác nhận thủ công");
        }
        session.setExitPlate(plate);
        session.setExitTime(now);
        session.setFee(calculateFee(session, now));
        session.setStatus(SessionStatus.COMPLETED);
        session.setManualOverride(request.manualOverride());
        session.setExitMember(driver);
        session.setExitFaceVerified(request.faceVerified());
        session.setExitFaceSimilarity(request.faceSimilarity());
        session = sessions.save(session);
        slots.findFirstByCurrentSessionId(session.getId()).ifPresent(slot -> {
            slot.setCurrentSession(null);
            slots.save(slot);
        });
        String message = session.getFee().signum() == 0 && hasValidMonthlyPass(session)
            ? "Đã xác nhận xe ra; gói tháng còn hiệu lực nên không thu thêm"
            : "Đã xác nhận xe ra và tính phí theo lượt";
        return toResponse(session, message, warning);
    }

    private ParkingSession findOpenSession(String plate, String cardCode) {
        var byPlate = sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(plate, SessionStatus.OPEN);
        if (byPlate.isPresent()) return byPlate.get();
        ParkingCard card = resolveCard(cardCode);
        if (card != null && card.getVehicle() != null) {
            return sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
                card.getVehicle().getPlateNumber(), SessionStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lượt xe vào đang mở"));
        }
        throw new IllegalStateException("Không tìm thấy lượt xe vào đang mở");
    }

    private FamilyMember verifyDriver(Vehicle vehicle, Long memberId, boolean faceVerified,
                                      Double similarity, boolean manualOverride) {
        if (vehicle == null) return null; // Khách vãng lai chỉ đối chiếu ảnh vào/ra ở nghiệp vụ riêng.
        if (memberId == null) {
            boolean hasFaceProfile = vehicle.getAuthorizedMembers().stream().anyMatch(m ->
                m.getRegistrationFaceImagePath() != null && !m.getRegistrationFaceImagePath().isBlank());
            if (!hasFaceProfile) return null; // dữ liệu cũ được phép đi qua để quản trị viên bổ sung ảnh sau
            if (manualOverride) return null;
            throw new IllegalStateException("Hãy chọn thành viên gia đình đang điều khiển xe");
        }
        FamilyMember member = members.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy thành viên đã chọn"));
        boolean authorized = member.isActive() && vehicle.getAuthorizedMembers().stream()
            .anyMatch(item -> item.getId().equals(member.getId()));
        if (!authorized) throw new IllegalStateException("Thành viên này không được đăng ký sử dụng xe");
        if (member.getRegistrationFaceImagePath() == null || member.getRegistrationFaceImagePath().isBlank()) {
            if (!manualOverride) throw new IllegalStateException("Thành viên chưa có ảnh khuôn mặt đăng ký");
        } else if (!faceVerified && !manualOverride) {
            throw new IllegalStateException("Khuôn mặt realtime chưa khớp ảnh đăng ký");
        }
        return member;
    }

    private void verifyGuestExit(ParkingSession session, boolean faceVerified, boolean manualOverride) {
        if (session.getVehicle() == null && session.getEntryFaceImagePath() != null
            && !session.getEntryFaceImagePath().isBlank() && !faceVerified && !manualOverride)
            throw new IllegalStateException("Khách vãng lai phải xác thực khuôn mặt ra với ảnh đã chụp lúc vào");
    }

    private void assignSlot(ParkingSession session) {
        var preferred = session.getVehicle() == null ? java.util.Optional.<ParkingSlot>empty()
            : slots.findFirstByAssignedVehicleIdAndCurrentSessionIsNullAndActiveTrue(session.getVehicle().getId());
        var slot = preferred.or(() -> slots.findFirstByCurrentSessionIsNullAndActiveTrueOrderBySlotCodeAsc());
        slot.ifPresent(item -> { item.setCurrentSession(session); slots.save(item); });
    }

    private BigDecimal calculateFee(ParkingSession session, LocalDateTime exitTime) {
        if (hasValidMonthlyPass(session)) return BigDecimal.ZERO;
        VehicleType type = session.getDetectedVehicleType();
        PricingRule rule = pricingRules.findByVehicleType(type)
            .orElseThrow(() -> new IllegalStateException("Chưa cấu hình bảng giá cho " + type));
        long minutes = Math.max(1, Duration.between(session.getEntryTime(), exitTime).toMinutes());
        long midnightCount = Math.max(0,
            ChronoUnit.DAYS.between(session.getEntryTime().toLocalDate(), exitTime.toLocalDate()));

        if (type.isCar()) {
            long baseMinutes = Math.max(1, rule.getBaseHours()) * 60L;
            BigDecimal timeFee = rule.getBasePrice();
            if (minutes > baseMinutes) {
                long blockMinutes = Math.max(1, rule.getExtraBlockHours()) * 60L;
                long extraBlocks = ceilDiv(minutes - baseMinutes, blockMinutes);
                timeFee = timeFee.add(rule.getExtraBlockPrice().multiply(BigDecimal.valueOf(extraBlocks)));
            }
            BigDecimal overnightMinimum = rule.getOvernightFee().multiply(BigDecimal.valueOf(midnightCount));
            return timeFee.max(overnightMinimum);
        }

        if (midnightCount > 0 || minutes >= 24L * 60L) {
            long cycles = ceilDiv(minutes, 24L * 60L);
            return rule.getOvernightFee().multiply(BigDecimal.valueOf(cycles));
        }
        LocalTime dayStart = LocalTime.of(6, 0);
        LocalTime nightStart = LocalTime.of(18, 0);
        boolean entirelyDaytime = !session.getEntryTime().toLocalTime().isBefore(dayStart)
            && exitTime.toLocalTime().isBefore(nightStart);
        return entirelyDaytime ? rule.getBasePrice() : rule.getNightPrice();
    }

    private long ceilDiv(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    private boolean hasValidMonthlyPass(ParkingSession session) {
        ParkingCard card = session.getParkingCard();
        if (card == null || session.getVehicle() == null || card.getVehicle() == null) return false;
        boolean sameVehicle = card.getVehicle().getId().equals(session.getVehicle().getId());
        return sameVehicle && card.isMonthlyValid(session.getEntryTime().toLocalDate());
    }

    private ParkingCard resolveCard(String cardCode) {
        if (cardCode == null || cardCode.isBlank()) return null;
        return cards.findByCardCodeIgnoreCase(cardCode.trim())
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy thẻ " + cardCode));
    }

    private String requirePlate(String value) {
        String plate = PlateNormalizer.normalize(value);
        if (plate.length() < 5) throw new IllegalArgumentException("Biển số không hợp lệ");
        return plate;
    }

    private VehicleType resolveVehicleType(String detectedType, Vehicle vehicle) {
        if (vehicle != null) return vehicle.getVehicleType();
        VehicleType detected = parseDetectedType(detectedType);
        return detected == null ? VehicleType.MOTORBIKE : detected;
    }

    private VehicleType parseDetectedType(String detectedType) {
        if (detectedType == null || detectedType.isBlank()) return null;
        try { return VehicleType.valueOf(detectedType.toUpperCase()); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private ParkingResponse toResponse(ParkingSession s, String message, boolean warning) {
        return new ParkingResponse(s.getId(), s.getStatus() == SessionStatus.OPEN ? s.getEntryPlate() : s.getExitPlate(),
            owner(s), type(s), cardCode(s), s.getStatus().name(), s.getEntryTime(), s.getExitTime(), s.getFee(), message, warning);
    }

    private String owner(ParkingSession s) {
        return s.getVehicle() == null ? "Khách vãng lai" : s.getVehicle().getEffectiveOwnerName();
    }
    private String type(ParkingSession s) { return s.getDetectedVehicleType().name(); }
    private String cardCode(ParkingSession s) {
        return s.getParkingCard() == null ? "" : s.getParkingCard().getCardCode();
    }
}
