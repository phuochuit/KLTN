package vn.edu.parking.service;

import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

@Service
public class ParkingService {
    private final VehicleRepository vehicles;
    private final ParkingCardRepository cards;
    private final ParkingSessionRepository sessions;
    private final PricingRuleRepository pricingRules;
    private final FamilyMemberRepository members;
    private final ParkingSlotRepository slots;
    private final ResidentImageStorage imageStorage;
    private final GateEvidenceConsumptionService evidenceConsumption;
    private final ManualOverrideService manualOverrides;
    private final PaymentTransactionService paymentTransactions;
    private final ParkingFloorRepository parkingFloors;
    private final ParkingZoneRepository parkingZones;
    private final VehicleTypeRepository vehicleTypes;

    public ParkingService(VehicleRepository vehicles, ParkingCardRepository cards,
                          ParkingSessionRepository sessions, PricingRuleRepository pricingRules,
                          FamilyMemberRepository members, ParkingSlotRepository slots,
                          ResidentImageStorage imageStorage,
                          GateEvidenceConsumptionService evidenceConsumption,
                          ManualOverrideService manualOverrides,
                          PaymentTransactionService paymentTransactions) {
        this(vehicles, cards, sessions, pricingRules, members, slots, imageStorage,
             evidenceConsumption, manualOverrides, paymentTransactions, null, null, null);
    }

    @Autowired
    public ParkingService(VehicleRepository vehicles, ParkingCardRepository cards,
                          ParkingSessionRepository sessions, PricingRuleRepository pricingRules,
                          FamilyMemberRepository members, ParkingSlotRepository slots,
                          ResidentImageStorage imageStorage,
                          GateEvidenceConsumptionService evidenceConsumption,
                          ManualOverrideService manualOverrides,
                          PaymentTransactionService paymentTransactions,
                          ParkingFloorRepository parkingFloors,
                          ParkingZoneRepository parkingZones,
                          VehicleTypeRepository vehicleTypes) {
        this.vehicles = vehicles;
        this.cards = cards;
        this.sessions = sessions;
        this.pricingRules = pricingRules;
        this.members = members;
        this.slots = slots;
        this.imageStorage = imageStorage;
        this.evidenceConsumption = evidenceConsumption;
        this.manualOverrides = manualOverrides;
        this.paymentTransactions = paymentTransactions;
        this.parkingFloors = parkingFloors;
        this.parkingZones = parkingZones;
        this.vehicleTypes = vehicleTypes;
    }

    @Transactional
    public ParkingResponse enter(EntryRequest request) {
        if (request.noPlate() && (request.vehicleEvidenceId() == null
                || request.vehicleEvidenceId().isBlank()
                || request.faceEvidenceId() == null || request.faceEvidenceId().isBlank())) {
            throw new IllegalStateException(
                "Xe không biển số bắt buộc phải có evidence phương tiện và khuôn mặt");
        }
        var previewEvidence = request.manualOverride()
            ? evidenceConsumption.resolveForPreview(request.vehicleEvidenceId(),
                request.faceEvidenceId(), EvidenceOperation.ENTRY, request.familyMemberId())
            : null;
        var override = manualOverrides.authorize(request.manualOverride(), EvidenceOperation.ENTRY,
            request.manualOverrideReasonCode(), request.manualOverrideReasonText(), previewEvidence);
        var trusted = evidenceConsumption.resolveForConsumption(request.vehicleEvidenceId(),
            request.faceEvidenceId(), EvidenceOperation.ENTRY, request.familyMemberId());
        String originalPlate = trusted.legacy() ? request.plateNumber() : trusted.plateNumber();
        String originalType = trusted.legacy() ? request.vehicleType() : trusted.vehicleType();
        String plate = request.noPlate()
            ? generateNoPlateIdentifier()
            : requirePlate(override.requested() ? request.plateNumber() : originalPlate);
        String detectedType = request.noPlate() || override.requested()
            ? request.vehicleType() : originalType;
        Long familyMemberId = trusted.legacy() ? request.familyMemberId() : trusted.familyMemberId();
        boolean faceVerified = trusted.legacy() ? request.faceVerified() : trusted.faceVerified();
        Double faceSimilarity = trusted.legacy() ? request.faceSimilarity() : trusted.faceSimilarity();
        if (sessions.existsByEntryPlateIgnoreCaseAndStatus(plate, SessionStatus.OPEN)) {
            throw new IllegalStateException("Xe " + plate + " đã có lượt đang mở");
        }

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase(plate).orElse(null);
        boolean guestEntryFaceAvailable = request.realtimeFaceImageBase64() != null
            && !request.realtimeFaceImageBase64().isBlank();
        guestEntryFaceAvailable = guestEntryFaceAvailable
            || (!trusted.legacy() && trusted.faceEvidence() != null);
        if (vehicle == null && !guestEntryFaceAvailable && !override.requested()) {
            throw new IllegalStateException(
                "Khách vãng lai phải chụp ảnh khuôn mặt khi xe vào");
        }
        FamilyMember driver = verifyDriver(vehicle, familyMemberId, faceVerified,
            faceSimilarity, override.requested());
        ParkingCard card = cards.findByCardCodeIgnoreCase(plate).orElse(null);
        if (card == null && vehicle != null) {
            card = cards.findByVehicleId(vehicle.getId()).orElse(null);
        }
        if (card != null && (vehicle == null || card.getVehicle() == null
                || !card.getVehicle().getId().equals(vehicle.getId()))) {
            card = null;
        }
        boolean warning = false;
        String message;

        boolean cardMatches = card != null;

        if (request.noPlate()) {
            message = "Xe không biển số; mã lượt " + plate + " dùng để xác nhận khi xe ra";
        } else if (vehicle == null) {
            message = "Khách vãng lai; dùng biển số để vào/ra và tính giá theo lượt";
        } else if (card == null) {
            message = "Xe cư dân; dùng biển số để vào/ra; chưa có gói nên tính giá lượt";
        } else if (card.isPrepaidPackageValid(LocalDate.now())) {
            message = "Xe cư dân có " + card.getPassType().getDisplayName() + " hợp lệ";
        } else if (card.getPassType() == PassType.DAILY || card.getPassType() == PassType.MONTHLY) {
            warning = true;
            message = "Gói gửi xe đã hết hạn hoặc chưa đến ngày hiệu lực; tính giá khách vãng lai";
        } else {
            warning = true;
            message = "Xe cư dân chưa mua gói; tính giá khách vãng lai";
        }

        ParkingSession session = new ParkingSession();
        session.setEntryPlate(plate);
        session.setOpenPlate(plate);
        session.setVehicle(vehicle);
        session.setParkingCard(card);
        session.setDetectedVehicleType(resolveVehicleType(detectedType, vehicle));
        session.setDetectedVehicleTypeRef(resolveVehicleTypeRef(detectedType, vehicle));
        session.setEntryMember(driver);
        session.setEntryTime(LocalDateTime.now());
        capturePricingSnapshot(session, cardMatches && card.isPrepaidPackageValid(
            session.getEntryTime().toLocalDate()));
        session.setStatus(SessionStatus.OPEN);
        session.setEntryFaceVerified(faceVerified);
        session.setEntryFaceSimilarity(faceSimilarity);
        session.setManualOverride(override.requested());
        if (trusted.legacy() && request.realtimeFaceImageBase64() != null && !request.realtimeFaceImageBase64().isBlank()) {
            session.setEntryFaceImagePath(imageStorage.saveCaptured(request.realtimeFaceImageBase64()));
        } else if (!trusted.legacy() && trusted.faceEvidence() != null && vehicle == null) {
            session.setEntryFaceImagePath("evidence:" + trusted.faceEvidence().getId());
        }
        session = sessions.save(session);
        evidenceConsumption.consume(trusted, session);
        manualOverrides.recordApproved(override, session, originalPlate, plate, originalType, detectedType);
        assignSlot(session);
        return toResponse(session, message, warning);
    }

    @Transactional(readOnly = true)
    public ParkingResponse previewExit(ExitRequest request) {
        var trusted = evidenceConsumption.resolveForPreview(request.vehicleEvidenceId(),
            request.faceEvidenceId(), EvidenceOperation.EXIT, request.familyMemberId());
        String plate = requirePlate(isNoPlateIdentifier(request.plateNumber())
            ? request.plateNumber()
            : request.manualOverride() ? request.plateNumber()
            : trusted.legacy() ? request.plateNumber() : trusted.plateNumber());
        boolean faceVerified = resolveExitFaceVerified(trusted, request);
        Double faceSimilarity = trusted.legacy() ? request.faceSimilarity() : trusted.faceSimilarity();
        Long familyMemberId = trusted.legacy() ? request.familyMemberId() : trusted.familyMemberId();
        ParkingSession session = resolveExitSession(request.sessionId(), plate, false);
        var override = manualOverrides.authorize(request.manualOverride(), EvidenceOperation.EXIT,
            request.manualOverrideReasonCode(), request.manualOverrideReasonText(), trusted);
        verifyExitPerson(session, familyMemberId, faceVerified, faceSimilarity, override.requested());
        LocalDateTime exitTime = LocalDateTime.now();
        BigDecimal fee = calculateFee(session, exitTime);
        ParkingResponse r = toResponse(session, "Xem trước phí gửi xe", false);
        String displayPlate = hasSessionId(request.sessionId())
            ? PlateNormalizer.normalize(plate) : r.plateNumber();
        return new ParkingResponse(r.sessionId(), displayPlate, r.ownerName(), r.vehicleType(),
            r.cardCode(), "PREVIEW", r.entryTime(), exitTime, fee, "Xem trước phí gửi xe", false);
    }

    @Transactional
    public ParkingResponse confirmExit(ExitRequest request) {
        var previewEvidence = request.manualOverride()
            ? evidenceConsumption.resolveForPreview(request.vehicleEvidenceId(),
                request.faceEvidenceId(), EvidenceOperation.EXIT, request.familyMemberId())
            : null;
        var override = manualOverrides.authorize(request.manualOverride(), EvidenceOperation.EXIT,
            request.manualOverrideReasonCode(), request.manualOverrideReasonText(), previewEvidence);
        var trusted = evidenceConsumption.resolveForConsumption(request.vehicleEvidenceId(),
            request.faceEvidenceId(), EvidenceOperation.EXIT, request.familyMemberId());
        String originalPlate = isNoPlateIdentifier(request.plateNumber())
            ? request.plateNumber()
            : trusted.legacy() ? request.plateNumber() : trusted.plateNumber();
        String originalType = trusted.legacy() ? request.vehicleType() : trusted.vehicleType();
        String plate = requirePlate(override.requested() ? request.plateNumber() : originalPlate);
        String correctedType = override.requested() ? request.vehicleType() : originalType;
        boolean faceVerified = resolveExitFaceVerified(trusted, request);
        Double faceSimilarity = trusted.legacy() ? request.faceSimilarity() : trusted.faceSimilarity();
        Long familyMemberId = trusted.legacy() ? request.familyMemberId() : trusted.familyMemberId();
        ParkingSession session = resolveExitSession(request.sessionId(), plate, true);
        FamilyMember exitMember = verifyExitPerson(session, familyMemberId, faceVerified,
            faceSimilarity, override.requested());
        session.setExitPlate(PlateNormalizer.normalize(plate));
        session.setExitTime(LocalDateTime.now());
        session.setFee(calculateFee(session, session.getExitTime()));
        session.setStatus(SessionStatus.COMPLETED);
        session.setOpenPlate(null);
        session.setExitFaceVerified(faceVerified);
        session.setExitFaceSimilarity(faceSimilarity);
        session.setExitMember(exitMember);
        if (override.requested()) session.setManualOverride(true);
        session = sessions.save(session);

        PaymentTransaction payment = session.getFee().signum() > 0
            ? paymentTransactions.recordParkingExit(session, request.paymentMethod(), request.paymentReference())
            : null;

        evidenceConsumption.consume(trusted, session);
        manualOverrides.recordApproved(override, session, originalPlate, plate, originalType, correctedType);
        slots.findFirstByCurrentSessionId(session.getId()).ifPresent(slot -> {
            slot.setCurrentSession(null);
            slots.save(slot);
        });
        String message = session.getFee().signum() == 0 && packageAppliedAtEntry(session)
            ? "Đã xác nhận xe ra; gói trả trước còn hiệu lực nên không thu thêm"
            : payment == null
                ? "Đã xác nhận xe ra; không thu phí"
            : "Đã xác nhận xe ra; tổng thu: " + String.format("%,d", session.getFee().longValue())
                + " đ; biên nhận " + payment.getReceiptNumber();

        return toResponse(session, message, false,
            payment == null ? null : payment.getReceiptNumber(),
            payment == null ? null : (request.paymentMethod() != null ? request.paymentMethod().name() : PaymentMethod.CASH.name()),
            payment == null ? null : request.paymentReference());
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

        ensureSlotVehicleCompatible(slot, vehicle.getVehicleTypeRef(), vehicle.getVehicleType());

        // Ràng buộc 1 xe chỉ được gán 1 ô
        slots.findFirstByAssignedVehicleIdAndActiveTrue(vehicleId).ifPresent(existingSlot -> {
            if (!existingSlot.getId().equals(slotId)) {
                throw new IllegalArgumentException("Xe biển số " + vehicle.getPlateNumber()
                        + " đã được gán cố định tại ô " + existingSlot.getSlotCode() + ". Quy tắc 1 xe - 1 ô không cho phép gán thêm ô khác!");
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

        // Ràng buộc 1 xe chỉ đỗ nhờ 1 ô
        slots.findFirstByBorrowedPlateIgnoreCaseAndActiveTrue(normalized).ifPresent(otherSlot -> {
            if (!otherSlot.getId().equals(slotId)) {
                throw new IllegalArgumentException("Xe biển số " + normalized + " đang có lịch đỗ nhờ tại ô "
                        + otherSlot.getSlotCode() + ". Mỗi xe chỉ được giữ tối đa 1 ô!");
            }
        });

        int hours = Math.max(1, durationHours);
        vehicles.findByPlateNumberIgnoreCase(normalized)
            .ifPresent(vehicle -> ensureSlotVehicleCompatible(slot, vehicle.getVehicleTypeRef(), vehicle.getVehicleType()));
        slot.setBorrowedPlate(normalized);
        slot.setBorrowedUntil(LocalDateTime.now().plusHours(hours));
        slot.setBorrowNotes(notes != null ? notes.trim() : "");

        sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(normalized, SessionStatus.OPEN)
                .ifPresent(session -> {
                    ensureSlotVehicleCompatible(slot, session.getDetectedVehicleTypeRef(), session.getDetectedVehicleType());
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
        if (statusOverride == SlotStatusOverride.BLOCKED && slot.getCurrentSession() != null) {
            throw new IllegalStateException("Không thể cấm đỗ ô đang có xe đỗ. Vui lòng giải phóng xe ra trước.");
        }
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

        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Lượt xe không ở trạng thái mở.");
        }
        if (slot.getStatusOverride() == SlotStatusOverride.BLOCKED) {
            throw new IllegalStateException("Không thể điều phối xe vào ô đang cấm đỗ / bảo trì.");
        }
        if (!slot.isActive()) {
            throw new IllegalStateException("Không thể điều phối xe vào ô đã ngừng sử dụng.");
        }
        ensureSlotVehicleCompatible(slot, session.getDetectedVehicleTypeRef(), session.getDetectedVehicleType());

        if (slot.getCurrentSession() != null && !slot.getCurrentSession().getId().equals(sessionId)) {
            throw new IllegalStateException("Ô này đang có xe đỗ, không thể điều phối xe khác vào.");
        }

        if (slot.getAssignedVehicle() != null) {
            String assignedPlate = slot.getAssignedVehicle().getPlateNumber();
            String sessionPlate = session.getEntryPlate();
            boolean isAssignedCar = assignedPlate != null && assignedPlate.equalsIgnoreCase(sessionPlate);
            boolean isBorrowedCar = slot.getBorrowedPlate() != null && slot.getBorrowedPlate().equalsIgnoreCase(sessionPlate);
            if (!isAssignedCar && !isBorrowedCar) {
                throw new IllegalStateException("Ô " + slot.getSlotCode() + " đã cấp riêng cho xe cư dân " + assignedPlate + ".");
            }
        }

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
        String floorName = (req.floor() == null || req.floor().isBlank()) ? "Tầng 1" : req.floor().trim();
        String zoneName = (req.zoneName() == null || req.zoneName().isBlank()) ? "Khu A" : req.zoneName().trim();
        String prefix = (req.prefix() == null || req.prefix().isBlank()) ? "A" : req.prefix().trim().toUpperCase();
        int start = Math.max(1, req.startNumber());
        int count = Math.min(100, Math.max(1, req.count()));

        ParkingFloor floor = parkingFloors.findByNameIgnoreCase(floorName).orElseGet(() -> {
            boolean isBasement = floorName.toLowerCase().contains("hầm") || floorName.toUpperCase().startsWith("B");
            ParkingFloor f = new ParkingFloor(floorName, isBasement, 0, 500);
            return parkingFloors.save(f);
        });

        VehicleTypeEntity vTypeEntity = null;
        if (req.vehicleType() != null && !req.vehicleType().isBlank()) {
            vTypeEntity = vehicleTypes.findByCodeIgnoreCase(req.vehicleType().trim())
                    .or(() -> vehicleTypes.findByDisplayNameIgnoreCase(req.vehicleType().trim()))
                    .orElse(null);
        }
        if (vTypeEntity == null) {
            VehicleType vtEnum = "MOTORBIKE".equalsIgnoreCase(req.vehicleType()) ? VehicleType.MOTORBIKE : VehicleType.CAR;
            vTypeEntity = vehicleTypes.findFirstByCategoryAndActiveTrueOrderByIdAsc(vtEnum).orElse(null);
        }

        if (vTypeEntity != null && !pricingRules.existsByVehicleTypeRef_Id(vTypeEntity.getId())) {
            throw new IllegalStateException("Loại phương tiện '" + vTypeEntity.getDisplayName()
                    + "' chưa được thiết lập bảng giá. Vui lòng tạo bảng giá trước khi tạo ô đỗ.");
        }

        VehicleTypeEntity finalVType = vTypeEntity;
        ParkingZone zone = parkingZones.findByFloor_IdAndNameIgnoreCase(floor.getId(), zoneName).orElseGet(() -> {
            int unit = finalVType != null ? finalVType.getCapacityUnits() : 5;
            ParkingZone z = new ParkingZone(floor, zoneName, prefix, finalVType, count * unit, null);
            return parkingZones.save(z);
        });

        SlotType slotType = ("VISITOR".equalsIgnoreCase(req.slotType()) || "VISITOR_FLEXIBLE".equalsIgnoreCase(req.slotType()))
                ? SlotType.VISITOR_FLEXIBLE : SlotType.RESIDENT_RESERVED;
        VehicleType vTypeEnum = finalVType != null ? finalVType.getCategory() : VehicleType.CAR;

        int created = 0;
        for (int i = 0; i < count; i++) {
            int num = start + i;
            String code = prefix + (num < 10 ? ("0" + num) : String.valueOf(num));
            if (!slots.existsBySlotCodeIgnoreCase(code)) {
                ParkingSlot slot = new ParkingSlot();
                slot.setSlotCode(code);
                slot.setFloor(floor.getName());
                slot.setZoneName(zone.getName());
                slot.setZone(zone);
                slot.setVehicleTypeRef(finalVType);
                slot.setSlotType(slotType);
                slot.setAllowedVehicleType(vTypeEnum);
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

        String floorName = (slot.getZone() != null && slot.getZone().getFloor() != null)
                ? slot.getZone().getFloor().getName() : slot.getFloor();
        Boolean isBasement = (slot.getZone() != null && slot.getZone().getFloor() != null)
                ? slot.getZone().getFloor().isBasement()
                : (floorName != null && (floorName.toLowerCase().contains("hầm") || floorName.toUpperCase().startsWith("B")));
        Long floorId = (slot.getZone() != null && slot.getZone().getFloor() != null)
                ? slot.getZone().getFloor().getId() : null;

        String zoneName = (slot.getZone() != null) ? slot.getZone().getName() : slot.getZoneName();
        Long zoneId = (slot.getZone() != null) ? slot.getZone().getId() : null;
        String zonePrefix = (slot.getZone() != null && slot.getZone().getCodePrefix() != null)
                ? slot.getZone().getCodePrefix()
                : (slot.getSlotCode() != null && !slot.getSlotCode().isEmpty() ? slot.getSlotCode().substring(0, 1) : "A");

        Long vehicleTypeId = (slot.getVehicleTypeRef() != null)
                ? slot.getVehicleTypeRef().getId()
                : (slot.getZone() != null && slot.getZone().getVehicleType() != null ? slot.getZone().getVehicleType().getId() : null);

        String vehicleTypeCode = (slot.getVehicleTypeRef() != null)
                ? slot.getVehicleTypeRef().getCode()
                : (slot.getAllowedVehicleType() != null ? slot.getAllowedVehicleType().name() : "CAR");

        String vehicleTypeName = (slot.getVehicleTypeRef() != null)
                ? slot.getVehicleTypeRef().getDisplayName()
                : (slot.getAllowedVehicleType() != null ? slot.getAllowedVehicleType().getDisplayName() : "Chưa xác định");

        Integer capacityUnits = (slot.getVehicleTypeRef() != null)
                ? slot.getVehicleTypeRef().getCapacityUnits()
                : (slot.getAllowedVehicleType() != null && slot.getAllowedVehicleType().isCar() ? 5 : 1);

        String locationLabel = LocationLabelFormatter.format(floorName, zoneName, vehicleTypeName);

        return new ParkingSlotResponse(
            slot.getId(),
            slot.getSlotCode(),
            zoneName,
            floorName,
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
            desc,
            floorId,
            isBasement,
            zoneId,
            zonePrefix,
            vehicleTypeId,
            vehicleTypeCode,
            vehicleTypeName,
            capacityUnits,
            locationLabel
        );
    }

    private void assignSlot(ParkingSession session) {
        String plate = session.getEntryPlate();

        // 1. Kiểm tra ô đỗ nhờ khớp biển số và còn hạn
        if (plate != null && !plate.isBlank()) {
            var borrowedSlot = slots.findFirstByBorrowedPlateIgnoreCaseAndCurrentSessionIsNullAndActiveTrue(plate);
            if (borrowedSlot.isPresent() && borrowedSlot.get().getStatusOverride() != SlotStatusOverride.BLOCKED
                    && isSlotVehicleCompatible(borrowedSlot.get(), session.getDetectedVehicleTypeRef(), session.getDetectedVehicleType())) {
                var bSlot = borrowedSlot.get();
                bSlot.setCurrentSession(session);
                slots.save(bSlot);
                return;
            }
        }

        // 2. Kiểm tra ô đã gán xe cư dân
        if (session.getVehicle() != null) {
            var assignedSlot = slots.findFirstByAssignedVehicleIdAndCurrentSessionIsNullAndActiveTrue(session.getVehicle().getId());
            if (assignedSlot.isPresent() && assignedSlot.get().getStatusOverride() != SlotStatusOverride.BLOCKED
                    && isSlotVehicleCompatible(assignedSlot.get(), session.getDetectedVehicleTypeRef(), session.getDetectedVehicleType())) {
                var s = assignedSlot.get();
                s.setCurrentSession(session);
                slots.save(s);
                return;
            }
        }

        // 3. Tự động tìm ô trống phù hợp loại xe và đối tượng
        var candidate = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getCurrentSession() == null)
            .filter(s -> s.getStatusOverride() != SlotStatusOverride.BLOCKED)
            .filter(s -> s.getAssignedVehicle() == null)
            .filter(s -> {
                if (session.getVehicle() == null) {
                    return s.getSlotType() == null || s.getSlotType() == SlotType.VISITOR_FLEXIBLE;
                }
                return true;
            })
            .filter(s -> isSlotVehicleCompatible(s, session.getDetectedVehicleTypeRef(), session.getDetectedVehicleType()))
            .findFirst();

        candidate.ifPresent(item -> {
            item.setCurrentSession(session);
            slots.save(item);
        });
    }

    private ParkingSession findOpenSession(String plate, boolean forUpdate) {
        String normalizedPlate = PlateNormalizer.normalize(plate);
        if (normalizedPlate.isBlank()) {
            throw new IllegalStateException("Không tìm thấy lượt xe vào đang mở");
        }
        return findOpenByPlate(normalizedPlate, forUpdate)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy lượt xe vào đang mở"));
    }

    private static boolean hasSessionId(String sessionId) {
        return sessionId != null && !sessionId.isBlank();
    }

    /** Cho phép nối lượt ra có biển thật với lượt vào đã dùng mã tạm NPL. */
    private ParkingSession resolveExitSession(String sessionId, String plate, boolean forUpdate) {
        if (!hasSessionId(sessionId)) return findOpenSession(plate, forUpdate);
        long id;
        try { id = Long.parseLong(sessionId.trim()); }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Lượt gửi xe đã chọn không hợp lệ");
        }
        ParkingSession session = (forUpdate ? sessions.findByIdForUpdate(id) : sessions.findById(id))
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy lượt xe vào đang mở"));
        if (session.getStatus() != SessionStatus.OPEN)
            throw new IllegalStateException("Lượt gửi xe đã chọn không còn mở");
        return session;
    }

    private java.util.Optional<ParkingSession> findOpenByPlate(String plate, boolean forUpdate) {
        if (!forUpdate)
            return sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
                plate, SessionStatus.OPEN);
        return sessions.findByPlateAndStatusForUpdate(plate, SessionStatus.OPEN).stream().findFirst();
    }

    private FamilyMember verifyDriver(Vehicle vehicle, Long memberId, boolean faceVerified,
                                      Double faceSimilarity, boolean manualOverride) {
        if (vehicle == null) return null;
        if (memberId == null) {
            boolean hasFaceProfile = vehicle.getAuthorizedMembers().stream().anyMatch(m ->
                m.getRegistrationFaceImagePath() != null && !m.getRegistrationFaceImagePath().isBlank());
            if (!hasFaceProfile) return null;
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

    /**
     * Khách vãng lai: nếu hệ thống chưa tự kết luận khớp khuôn mặt nhưng nhân viên đã xem
     * đủ ảnh vào/ra và bấm xác nhận trong hộp đối chiếu thì dùng xác nhận đó, miễn là có
     * ảnh khuôn mặt lúc ra và hệ thống không kết luận đây là người khác.
     */
    private static boolean resolveExitFaceVerified(
            GateEvidenceConsumptionService.ResolvedEvidence trusted, ExitRequest request) {
        if (trusted.legacy()) return request.faceVerified();
        if (trusted.faceVerified()) return true;
        if (!request.faceVerified() || trusted.faceEvidence() == null) return false;
        return !"REJECT".equalsIgnoreCase(trusted.faceEvidence().getFaceDecision());
    }

    private FamilyMember verifyExitPerson(ParkingSession session, Long memberId,
            boolean faceVerified, Double faceSimilarity, boolean manualOverride) {
        if (session.getVehicle() == null) {
            if ((session.getEntryFaceImagePath() == null || session.getEntryFaceImagePath().isBlank())
                    && !manualOverride)
                throw new IllegalStateException(
                    "Lượt khách vãng lai thiếu ảnh khuôn mặt lúc vào; cần xử lý thủ công");
            if (!faceVerified && !manualOverride)
                throw new IllegalStateException(
                    "Khách vãng lai phải xác thực khuôn mặt ra với ảnh đã chụp lúc vào");
            return null;
        }
        Vehicle vehicle = session.getVehicle();
        if (memberId == null) {
            if (manualOverride) return null;
            throw new IllegalStateException("Hãy chọn người đang lấy xe");
        }
        FamilyMember member = members.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy người đang lấy xe"));
        boolean authorized = member.isActive() && vehicle.getAuthorizedMembers().stream()
            .anyMatch(item -> item.getId().equals(member.getId()));
        if (!authorized)
            throw new IllegalStateException("Người này không được đăng ký sử dụng xe");
        if (member.getRegistrationFaceImagePath() == null
                || member.getRegistrationFaceImagePath().isBlank()) {
            if (!manualOverride)
                throw new IllegalStateException("Người lấy xe chưa có ảnh khuôn mặt đăng ký");
        } else if (!faceVerified && !manualOverride) {
            throw new IllegalStateException("Khuôn mặt người lấy xe chưa khớp ảnh đăng ký");
        }
        return member;
    }

    BigDecimal calculateFee(ParkingSession session, LocalDateTime exitTime) {
        if (packageAppliedAtEntry(session)) return BigDecimal.ZERO;
        VehicleType type = session.getDetectedVehicleType();
        PricingTerms price = pricingTerms(session, type);
        long minutes = Math.max(1, Duration.between(session.getEntryTime(), exitTime).toMinutes());
        long midnightCount = Math.max(0,
            ChronoUnit.DAYS.between(session.getEntryTime().toLocalDate(), exitTime.toLocalDate()));

        if (type.isCar()) {
            long baseMinutes = Math.max(1, price.baseHours()) * 60L;
            BigDecimal timeFee = price.basePrice();
            if (minutes > baseMinutes) {
                long blockMinutes = Math.max(1, price.extraBlockHours()) * 60L;
                long extraBlocks = ceilDiv(minutes - baseMinutes, blockMinutes);
                timeFee = timeFee.add(price.extraBlockPrice().multiply(BigDecimal.valueOf(extraBlocks)));
            }
            BigDecimal overnightMinimum = price.overnightPrice().multiply(BigDecimal.valueOf(midnightCount));
            return timeFee.max(overnightMinimum);
        }

        if (midnightCount > 0 || minutes >= 24L * 60L) {
            long cycles = ceilDiv(minutes, 24L * 60L);
            return price.overnightPrice().multiply(BigDecimal.valueOf(cycles));
        }
        LocalTime dayStart = LocalTime.of(6, 0);
        LocalTime nightStart = LocalTime.of(18, 0);
        boolean entirelyDaytime = !session.getEntryTime().toLocalTime().isBefore(dayStart)
            && exitTime.toLocalTime().isBefore(nightStart)
            && session.getEntryTime().toLocalDate().isEqual(exitTime.toLocalDate());
        return entirelyDaytime ? price.basePrice() : price.nightPrice();
    }

    private static long ceilDiv(long x, long y) {
        return (x + y - 1) / y;
    }

    private boolean packageAppliedAtEntry(ParkingSession session) {
        if (session.getPrepaidPackageApplied() != null)
            return session.getPrepaidPackageApplied();
        // Tương thích các lượt OPEN tạo trước khi có snapshot.
        ParkingCard card = session.getParkingCard();
        if (card == null || session.getVehicle() == null || card.getVehicle() == null) return false;
        boolean sameVehicle = card.getVehicle().getId().equals(session.getVehicle().getId());
        return sameVehicle && card.isPrepaidPackageValid(session.getEntryTime().toLocalDate());
    }

    private void capturePricingSnapshot(ParkingSession session, boolean prepaidPackageApplied) {
        PricingRule rule = resolvePricingRule(session, session.getDetectedVehicleType());
        session.setPrepaidPackageApplied(prepaidPackageApplied);
        session.setPriceBaseSnapshot(rule.getBasePrice());
        session.setPriceNightSnapshot(rule.getNightPrice());
        session.setPriceOvernightSnapshot(rule.getOvernightFee());
        session.setPriceExtraBlockSnapshot(rule.getExtraBlockPrice());
        session.setBaseHoursSnapshot(rule.getBaseHours());
        session.setExtraBlockHoursSnapshot(rule.getExtraBlockHours());
    }

    private PricingTerms pricingTerms(ParkingSession session, VehicleType type) {
        if (session.getPriceBaseSnapshot() != null && session.getPriceNightSnapshot() != null
                && session.getPriceOvernightSnapshot() != null
                && session.getPriceExtraBlockSnapshot() != null
                && session.getBaseHoursSnapshot() != null
                && session.getExtraBlockHoursSnapshot() != null) {
            return new PricingTerms(session.getPriceBaseSnapshot(), session.getPriceNightSnapshot(),
                session.getPriceOvernightSnapshot(), session.getPriceExtraBlockSnapshot(),
                session.getBaseHoursSnapshot(), session.getExtraBlockHoursSnapshot());
        }
        PricingRule rule = resolvePricingRule(session, type);
        return new PricingTerms(rule.getBasePrice(), rule.getNightPrice(), rule.getOvernightFee(),
            rule.getExtraBlockPrice(), rule.getBaseHours(), rule.getExtraBlockHours());
    }

    private PricingRule resolvePricingRule(ParkingSession session, VehicleType type) {
        PricingRule rule = null;
        if (session.getDetectedVehicleTypeRef() != null) {
            rule = pricingRules.findByVehicleTypeRef_Id(session.getDetectedVehicleTypeRef().getId())
                .orElse(null);
        }
        if (rule == null && session.getVehicle() != null
                && session.getVehicle().getVehicleTypeRef() != null) {
            rule = pricingRules.findByVehicleTypeRef_Id(
                session.getVehicle().getVehicleTypeRef().getId()).orElse(null);
        }
        if (rule == null) {
            rule = pricingRules.findFirstByVehicleTypeOrderByIdAsc(type)
                .or(() -> pricingRules.findByVehicleType(type))
                .orElseThrow(() -> new IllegalStateException(
                    "Chưa cấu hình bảng giá cho loại phương tiện"));
        }
        return rule;
    }

    private record PricingTerms(BigDecimal basePrice, BigDecimal nightPrice,
            BigDecimal overnightPrice, BigDecimal extraBlockPrice,
            int baseHours, int extraBlockHours) { }

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

    private String generateNoPlateIdentifier() {
        return "NPL" + LocalDate.now().toString().replace("-", "")
            + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private boolean isNoPlateIdentifier(String value) {
        return PlateNormalizer.normalize(value).matches("NPL\\d{8}[A-F0-9]{6}");
    }

    private VehicleType resolveVehicleType(String detectedType, Vehicle vehicle) {
        if (vehicle != null) return vehicle.getVehicleType();
        VehicleType detected = parseDetectedType(detectedType);
        return detected == null ? VehicleType.MOTORBIKE : detected;
    }

    private VehicleTypeEntity resolveVehicleTypeRef(String detectedType, Vehicle vehicle) {
        if (vehicle != null && vehicle.getVehicleTypeRef() != null) {
            return vehicle.getVehicleTypeRef();
        }
        if (detectedType != null && !detectedType.isBlank()) {
            var byCode = vehicleTypes.findByCodeIgnoreCase(detectedType.trim());
            if (byCode.isPresent()) return byCode.get();
            var byName = vehicleTypes.findByDisplayNameIgnoreCase(detectedType.trim());
            if (byName.isPresent()) return byName.get();
        }
        VehicleType vt = resolveVehicleType(detectedType, vehicle);
        return vehicleTypes.findFirstByCategoryAndActiveTrueOrderByIdAsc(vt).orElse(null);
    }

    private VehicleType parseDetectedType(String detectedType) {
        if (detectedType == null || detectedType.isBlank()) return null;
        try { return VehicleType.valueOf(detectedType.toUpperCase()); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private ParkingResponse toResponse(ParkingSession s, String message, boolean warning) {
        return toResponse(s, message, warning, null, null, null);
    }

    private ParkingResponse toResponse(ParkingSession s, String message, boolean warning,
            String receiptNumber, String paymentMethod, String paymentReference) {
        return new ParkingResponse(s.getId(), s.getStatus() == SessionStatus.OPEN ? s.getEntryPlate() : s.getExitPlate(),
            owner(s), type(s), cardCode(s), s.getStatus().name(), s.getEntryTime(), s.getExitTime(), s.getFee(),
            message, warning, receiptNumber, paymentMethod, paymentReference);
    }

    private String owner(ParkingSession s) {
        return s.getVehicle() == null ? "Khách vãng lai" : s.getVehicle().getEffectiveOwnerName();
    }
    private String type(ParkingSession s) { return s.getDetectedVehicleType().name(); }
    private String cardCode(ParkingSession s) {
        return s.getEntryPlate();
    }

    public boolean isSlotVehicleCompatible(ParkingSlot slot, VehicleTypeEntity vehicleTypeRef, VehicleType fallbackCategory) {
        if (slot == null || !slot.isActive()) return false;
        if (slot.getVehicleTypeRef() != null && vehicleTypeRef != null) {
            VehicleType slotCategory = slot.getVehicleTypeRef().getCategory();
            VehicleType targetCategory = vehicleTypeRef.getCategory();
            return slotCategory != null && targetCategory != null
                && slotCategory.isCar() == targetCategory.isCar()
                && slot.getVehicleTypeRef().getCapacityUnits() == vehicleTypeRef.getCapacityUnits();
        }
        VehicleType slotCat = slot.getVehicleTypeRef() != null ? slot.getVehicleTypeRef().getCategory() : slot.getAllowedVehicleType();
        VehicleType targetCat = vehicleTypeRef != null ? vehicleTypeRef.getCategory() : fallbackCategory;
        if (slotCat == null || targetCat == null) return true;
        return slotCat.isCar() == targetCat.isCar();
    }

    public void ensureSlotVehicleCompatible(ParkingSlot slot, VehicleTypeEntity vehicleTypeRef, VehicleType fallbackCategory) {
        if (!slot.isActive()) {
            throw new IllegalStateException("Ô " + slot.getSlotCode() + " đã ngừng sử dụng.");
        }
        VehicleType slotCat = slot.getVehicleTypeRef() != null ? slot.getVehicleTypeRef().getCategory() : slot.getAllowedVehicleType();
        VehicleType targetCat = vehicleTypeRef != null ? vehicleTypeRef.getCategory() : fallbackCategory;
        if (slotCat != null && targetCat != null && slotCat.isCar() != targetCat.isCar()) {
            String expected = slotCat.isCar() ? "ô tô" : "xe hai bánh";
            throw new IllegalArgumentException("Ô " + slot.getSlotCode() + " chỉ dành cho " + expected + ".");
        }
        if (slot.getVehicleTypeRef() != null && vehicleTypeRef != null
                && slot.getVehicleTypeRef().getCapacityUnits() != vehicleTypeRef.getCapacityUnits()) {
            throw new IllegalArgumentException("Ô " + slot.getSlotCode() + " sử dụng "
                + slot.getVehicleTypeRef().getCapacityUnits() + " đơn vị sức chứa, không phù hợp với phương tiện cần "
                + vehicleTypeRef.getCapacityUnits() + " đơn vị.");
        }
    }
}
