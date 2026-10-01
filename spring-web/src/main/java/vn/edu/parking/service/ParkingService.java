package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.web.dto.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
            message = "Khách vãng lai; tính giá theo lượt";
        } else if (!cardMatches) {
            warning = true;
            message = "Xe cư dân nhưng thẻ không trùng khớp hoặc chưa quẹt thẻ; sẽ tính theo giá lượt";
        } else if (card != null && card.getPassType() == PassType.MONTHLY) {
            LocalDate today = LocalDate.now();
            if (card.isMonthlyValid(today)) {
                message = "Xe cư dân có gói tháng hợp lệ";
            } else {
                warning = true;
                message = "Gói tháng đã hết hạn; chuyển sang tính giá lượt";
            }
        } else {
            message = "Xe cư dân dùng vé lượt";
        }

        ParkingSession session = new ParkingSession();
        session.setEntryPlate(plate);
        session.setVehicle(vehicle);
        session.setParkingCard(card);
        session.setDetectedVehicleType(resolveVehicleType(request.vehicleType(), vehicle));
        session.setEntryMember(driver);
        session.setEntryTime(LocalDateTime.now());
        session.setStatus(SessionStatus.OPEN);
        session.setEntryFaceVerified(request.faceVerified());
        session.setEntryFaceSimilarity(request.faceSimilarity());
        if (request.realtimeFaceImageBase64() != null && !request.realtimeFaceImageBase64().isBlank()) {
            session.setEntryFaceImagePath(imageStorage.saveCaptured(request.realtimeFaceImageBase64()));
        }
        session = sessions.save(session);
        assignSlot(session);
        return toResponse(session, message, warning);
    }

    @Transactional(readOnly = true)
    public ParkingResponse previewExit(ExitRequest request) {
        ParkingSession session = findOpenSession(request.plateNumber(), request.cardCode());
        verifyGuestExit(session, request.faceVerified(), request.manualOverride());
        LocalDateTime exitTime = LocalDateTime.now();
        BigDecimal fee = calculateFee(session, exitTime);
        ParkingResponse r = toResponse(session, "Xem trước phí gửi xe", false);
        return new ParkingResponse(r.sessionId(), r.plateNumber(), r.ownerName(), r.vehicleType(),
            r.cardCode(), "PREVIEW", r.entryTime(), exitTime, fee, "Xem trước phí gửi xe", false);
    }

    @Transactional
    public ParkingResponse confirmExit(ExitRequest request) {
        ParkingSession session = findOpenSession(request.plateNumber(), request.cardCode());
        verifyGuestExit(session, request.faceVerified(), request.manualOverride());
        session.setExitPlate(PlateNormalizer.normalize(request.plateNumber()));
        session.setExitTime(LocalDateTime.now());
        session.setFee(calculateFee(session, session.getExitTime()));
        session.setStatus(SessionStatus.COMPLETED);
        session.setExitFaceVerified(request.faceVerified());
        session.setExitFaceSimilarity(request.faceSimilarity());
        session = sessions.save(session);
        slots.findFirstByCurrentSessionId(session.getId()).ifPresent(slot -> {
            slot.setCurrentSession(null);
            slots.save(slot);
        });
        String message = session.getFee().signum() == 0 && hasValidMonthlyPass(session)
            ? "Đã xác nhận xe ra; gói tháng còn hiệu lực nên không thu thêm"
            : "Đã xác nhận xe ra; tổng thu: " + String.format("%,d", session.getFee().longValue()) + " đ";
        return toResponse(session, message, false);
    }

    @Transactional
    public void assignVehicleToSlot(Long slotId, Long vehicleId) {
        ParkingSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ ID " + slotId));
        if (vehicleId == null) {
            slot.setAssignedVehicle(null);
            slots.save(slot);
            return;
        }
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phương tiện ID " + vehicleId));

        // Ràng buộc 1 xe chỉ được gán 1 ô
        slots.findFirstByAssignedVehicleIdAndActiveTrue(vehicleId).ifPresent(existingSlot -> {
            if (!existingSlot.getId().equals(slotId)) {
                throw new IllegalArgumentException("Xe biển số " + vehicle.getPlateNumber()
                        + " đã được gán tại ô " + existingSlot.getSlotCode() + ". Không thể gán thêm ô khác!");
            }
        });

        slot.setAssignedVehicle(vehicle);
        slot.setSlotType(SlotType.RESIDENT_RESERVED);
        slots.save(slot);
    }

    @Transactional
    public void setupBorrowing(Long slotId, String borrowedPlate, int durationHours, String notes) {
        ParkingSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ ID " + slotId));
        String normalized = PlateNormalizer.normalize(borrowedPlate);
        if (normalized.length() < 5) {
            throw new IllegalArgumentException("Biển số xe đỗ nhờ không hợp lệ");
        }
        int hours = Math.max(1, durationHours);
        slot.setBorrowedPlate(normalized);
        slot.setBorrowedUntil(LocalDateTime.now().plusHours(hours));
        slot.setBorrowNotes(notes != null ? notes.trim() : "");

        sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(normalized, SessionStatus.OPEN)
                .ifPresent(session -> {
                    slots.findFirstByCurrentSessionId(session.getId()).ifPresent(oldSlot -> {
                        if (!oldSlot.getId().equals(slotId)) {
                            oldSlot.setCurrentSession(null);
                            slots.save(oldSlot);
                        }
                    });
                    slot.setCurrentSession(session);
                });

        slots.save(slot);
    }

    @Transactional
    public void cancelBorrowing(Long slotId) {
        ParkingSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ ID " + slotId));
        slot.setBorrowedPlate(null);
        slot.setBorrowedUntil(null);
        slot.setBorrowNotes(null);
        slots.save(slot);
    }

    @Transactional
    public void setSlotStatusOverride(Long slotId, SlotStatusOverride statusOverride) {
        ParkingSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ ID " + slotId));
        slot.setStatusOverride(statusOverride != null ? statusOverride : SlotStatusOverride.NORMAL);
        slots.save(slot);
    }

    @Transactional
    public void releaseSlot(Long slotId) {
        ParkingSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ ID " + slotId));
        slot.setAssignedVehicle(null);
        slot.setBorrowedPlate(null);
        slot.setBorrowedUntil(null);
        slot.setBorrowNotes(null);
        slot.setCurrentSession(null);
        slots.save(slot);
    }

    @Transactional
    public void dispatchSessionToSlot(Long slotId, Long sessionId) {
        ParkingSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ô đỗ ID " + slotId));
        ParkingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lượt gửi ID " + sessionId));

        slots.findFirstByCurrentSessionId(sessionId).ifPresent(oldSlot -> {
            if (!oldSlot.getId().equals(slotId)) {
                oldSlot.setCurrentSession(null);
                slots.save(oldSlot);
            }
        });

        slot.setCurrentSession(session);
        slots.save(slot);
    }

    @Transactional
    public int batchGenerateSlots(BatchSlotGenerateRequest req) {
        String floor = (req.floor() == null || req.floor().isBlank()) ? "Tầng hầm B1" : req.floor().trim();
        String zone = (req.zoneName() == null || req.zoneName().isBlank()) ? "Khu A" : req.zoneName().trim();
        String prefix = (req.prefix() == null || req.prefix().isBlank()) ? "A" : req.prefix().trim().toUpperCase();
        int start = Math.max(1, req.startNumber());
        int count = Math.min(100, Math.max(1, req.count()));
        SlotType slotType = "VISITOR".equalsIgnoreCase(req.slotType()) ? SlotType.VISITOR_FLEXIBLE : SlotType.RESIDENT_RESERVED;
        VehicleType vType = "MOTORBIKE".equalsIgnoreCase(req.vehicleType()) ? VehicleType.MOTORBIKE : VehicleType.CAR;

        int created = 0;
        for (int i = 0; i < count; i++) {
            String code = prefix + (start + i);
            if (!slots.existsBySlotCodeIgnoreCase(code)) {
                ParkingSlot slot = new ParkingSlot();
                slot.setSlotCode(code);
                slot.setFloor(floor);
                slot.setZoneName(zone);
                slot.setSlotType(slotType);
                slot.setAllowedVehicleType(vType);
                slot.setActive(true);
                slots.save(slot);
                created++;
            }
        }
        return created;
    }

    public ParkingSlotResponse toSlotResponse(ParkingSlot slot) {
        LocalDateTime now = LocalDateTime.now();
        String assignedPlate = slot.getAssignedVehicle() == null ? "" : slot.getAssignedVehicle().getPlateNumber();
        String ownerName = slot.getAssignedVehicle() == null ? "" : slot.getAssignedVehicle().getEffectiveOwnerName();
        String ownerPhone = slot.getAssignedVehicle() == null ? "" : slot.getAssignedVehicle().getOwnerPhone();
        String apartment = slot.getAssignedVehicle() == null ? "" : slot.getAssignedVehicle().getEffectiveApartmentNumber();

        String occupiedPlate = slot.getCurrentSession() == null ? "" : slot.getCurrentSession().getEntryPlate();
        LocalDateTime entryTime = slot.getCurrentSession() == null ? null : slot.getCurrentSession().getEntryTime();

        boolean overdue = slot.isOverdue(now);
        String status;
        String desc;

        if (slot.getStatusOverride() == SlotStatusOverride.BLOCKED) {
            status = "BLOCKED";
            desc = "Cấm đỗ / Đang bảo trì";
        } else if (overdue) {
            status = "OVERDUE_ALERT";
            desc = "Đỗ nhờ quá hạn quy định!";
        } else if (!occupiedPlate.isBlank()) {
            if (slot.isCurrentlyBorrowed()) {
                status = "OCCUPIED_BORROWED";
                desc = "Đang đỗ nhờ (" + slot.getBorrowedPlate() + ")";
            } else if (!assignedPlate.isBlank() && assignedPlate.equalsIgnoreCase(occupiedPlate)) {
                status = "OCCUPIED_VALID";
                desc = "Đang đỗ đúng xe";
            } else if (!assignedPlate.isBlank()) {
                status = "OCCUPIED_MISMATCH";
                desc = "Đỗ sai vị trí (Xe đỗ: " + occupiedPlate + ")";
            } else {
                status = "OCCUPIED_VALID";
                desc = "Đang có xe đỗ (" + occupiedPlate + ")";
            }
        } else {
            if (slot.getBorrowedPlate() != null && !slot.getBorrowedPlate().isBlank()) {
                status = "RESERVED_BORROWED";
                desc = "Đã hẹn giờ đỗ nhờ: " + slot.getBorrowedPlate();
            } else if (!assignedPlate.isBlank()) {
                status = "RESERVED_EMPTY";
                desc = "Đã cấp: " + assignedPlate + " (xe chưa về)";
            } else {
                status = "AVAILABLE";
                desc = "Ô trống sẵn sàng";
            }
        }

        return new ParkingSlotResponse(
            slot.getId(),
            slot.getSlotCode(),
            slot.getZoneName(),
            slot.getFloor(),
            slot.getSlotType() == null ? "RESIDENT_RESERVED" : slot.getSlotType().name(),
            slot.getAllowedVehicleType() == null ? "CAR" : slot.getAllowedVehicleType().name(),
            slot.getStatusOverride() == null ? "NORMAL" : slot.getStatusOverride().name(),
            assignedPlate,
            ownerName,
            ownerPhone,
            apartment,
            occupiedPlate,
            entryTime,
            slot.getBorrowedPlate() == null ? "" : slot.getBorrowedPlate(),
            slot.getBorrowedUntil(),
            slot.getBorrowNotes() == null ? "" : slot.getBorrowNotes(),
            overdue,
            status,
            desc
        );
    }

    private void assignSlot(ParkingSession session) {
        String plate = session.getEntryPlate();

        // 1. Kiểm tra ô đỗ nhờ khớp biển số và còn hạn
        if (plate != null && !plate.isBlank()) {
            var borrowedSlot = slots.findFirstByBorrowedPlateIgnoreCaseAndCurrentSessionIsNullAndActiveTrue(plate);
            if (borrowedSlot.isPresent() && borrowedSlot.get().getStatusOverride() != SlotStatusOverride.BLOCKED) {
                var bSlot = borrowedSlot.get();
                bSlot.setCurrentSession(session);
                slots.save(bSlot);
                return;
            }
        }

        // 2. Kiểm tra ô cấp cố định cho xe cư dân
        if (session.getVehicle() != null) {
            var assignedSlot = slots.findFirstByAssignedVehicleIdAndCurrentSessionIsNullAndActiveTrue(session.getVehicle().getId());
            if (assignedSlot.isPresent() && assignedSlot.get().getStatusOverride() != SlotStatusOverride.BLOCKED) {
                var s = assignedSlot.get();
                s.setCurrentSession(session);
                slots.save(s);
                return;
            }
        }

        // 3. Tự động tìm ô trống phù hợp loại xe và đối tượng
        boolean isCar = session.getDetectedVehicleType() != null && session.getDetectedVehicleType().isCar();
        var candidate = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getCurrentSession() == null)
            .filter(s -> s.getStatusOverride() != SlotStatusOverride.BLOCKED)
            .filter(s -> {
                if (session.getVehicle() == null) {
                    return s.getSlotType() == SlotType.VISITOR_FLEXIBLE || s.getAssignedVehicle() == null;
                }
                return s.getAssignedVehicle() == null;
            })
            .filter(s -> s.getAllowedVehicleType() == null || s.getAllowedVehicleType().isCar() == isCar)
            .findFirst();

        candidate.or(() -> slots.findFirstByCurrentSessionIsNullAndActiveTrueOrderBySlotCodeAsc())
            .ifPresent(item -> {
                item.setCurrentSession(session);
                slots.save(item);
            });
    }

    private ParkingSession findOpenSession(String plate, String cardCode) {
        String normalizedPlate = PlateNormalizer.normalize(plate);
        var byPlate = normalizedPlate.isBlank() ? java.util.Optional.<ParkingSession>empty()
            : sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(normalizedPlate, SessionStatus.OPEN);
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
                                      Double faceSimilarity, boolean manualOverride) {
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
            && !exitTime.toLocalTime().isAfter(nightStart)
            && session.getEntryTime().toLocalDate().isEqual(exitTime.toLocalDate());
        return entirelyDaytime ? rule.getBasePrice() : rule.getNightPrice();
    }

    private static long ceilDiv(long x, long y) {
        return (x + y - 1) / y;
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
