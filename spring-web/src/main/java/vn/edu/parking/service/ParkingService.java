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
    private final SecurityAuditService audit;

    public ParkingService(VehicleRepository vehicles, ParkingCardRepository cards,
                          ParkingSessionRepository sessions, PricingRuleRepository pricingRules,
                          FamilyMemberRepository members, ParkingSlotRepository slots,
                          SecurityAuditService audit) {
        this.vehicles = vehicles;
        this.cards = cards;
        this.sessions = sessions;
        this.pricingRules = pricingRules;
        this.members = members;
        this.slots = slots;
        this.audit = audit;
    }

    @Transactional
    public ParkingResponse enter(EntryRequest request) {
        return enter(request, null);
    }

    @Transactional
    public ParkingResponse enter(EntryRequest request, GateEvidenceService.FaceProof faceProof) {
        return enter(request, faceProof, null, null);
    }

    @Transactional
    public ParkingResponse enter(EntryRequest request, GateEvidenceService.FaceProof faceProof,
            GateEvidenceService.Recognition recognition,
            org.springframework.security.core.Authentication authentication) {
        rejectClientFaceVerificationClaims(request.faceVerified(), request.faceSimilarity());
        String overrideCode = validateEntryOverride(request, faceProof, recognition, authentication);
        boolean faceVerified = faceProof != null && "PASS".equals(faceProof.decision());
        if (faceProof != null && !java.util.Objects.equals(faceProof.familyMemberId(), request.familyMemberId()))
            throw new IllegalArgumentException("Face evidence does not match the selected family member");
        String plate = requirePlate(request.plateNumber());
        if (sessions.existsByEntryPlateIgnoreCaseAndStatus(plate, SessionStatus.OPEN)) {
            throw new IllegalStateException("Xe " + plate + " đã có lượt đang mở");
        }

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase(plate).orElse(null);
        FamilyMember driver = verifyDriver(vehicle, request.familyMemberId(), overrideCode,
            faceVerified, faceProof, recognition);
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
        session.setManualOverride(overrideCode != null);
        session.setEntryFaceVerified(faceVerified);
        session.setEntryFaceSimilarity(faceVerified ? faceProof.similarity() : null);
        if (vehicle == null && faceProof != null && "CAPTURED".equals(faceProof.decision()))
            session.setEntryFaceImagePath("gate-evidence:" + faceProof.evidenceId());
        session = sessions.save(session);
        assignSlot(session);
        if (overrideCode != null) {
            audit.record(authentication, "OVERRIDE_ENTRY", "PARKING_SESSION", session.getId().toString(),
                "SUCCESS", overrideAuditReason(overrideCode, request.override().reason()), recognition.evidenceId());
        }
        return toResponse(session, message, warning);
    }

    @Transactional(readOnly = true)
    public ParkingResponse previewExit(ExitRequest request) {
        return previewExit(request, null);
    }

    @Transactional(readOnly = true)
    public ParkingResponse previewExit(ExitRequest request, GateEvidenceService.FaceProof faceProof) {
        return previewExit(request, faceProof, null, null);
    }

    @Transactional(readOnly = true)
    public ParkingResponse previewExit(ExitRequest request, GateEvidenceService.FaceProof faceProof,
            GateEvidenceService.Recognition recognition,
            org.springframework.security.core.Authentication authentication) {
        rejectClientVerificationClaims(request.faceVerified(), request.faceSimilarity(), request.manualOverride());
        String overrideCode = validateExitOverride(request, faceProof, recognition, authentication);
        ParkingSession session = findOpenSession(request.plateNumber(), request.cardCode());
        validateExitFaceProof(request, faceProof);
        verifyGuestExit(session, faceProof, overrideCode, recognition);
        LocalDateTime exitTime = LocalDateTime.now();
        BigDecimal fee = calculateFee(session, exitTime);
        ParkingResponse r = toResponse(session, "Xem trước phí gửi xe", false);
        return new ParkingResponse(r.sessionId(), r.plateNumber(), r.ownerName(), r.vehicleType(),
            r.cardCode(), "PREVIEW", r.entryTime(), exitTime, fee, "Xem trước phí gửi xe", false);
    }

    @Transactional
    public ParkingResponse confirmExit(ExitRequest request) {
        return confirmExit(request, null);
    }

    @Transactional
    public ParkingResponse confirmExit(ExitRequest request, GateEvidenceService.FaceProof faceProof) {
        return confirmExit(request, faceProof, null, null);
    }

    @Transactional
    public ParkingResponse confirmExit(ExitRequest request, GateEvidenceService.FaceProof faceProof,
            GateEvidenceService.Recognition recognition,
            org.springframework.security.core.Authentication authentication) {
        rejectClientVerificationClaims(request.faceVerified(), request.faceSimilarity(), request.manualOverride());
        String overrideCode = validateExitOverride(request, faceProof, recognition, authentication);
        ParkingSession session = findOpenSession(request.plateNumber(), request.cardCode());
        validateExitFaceProof(request, faceProof);
        verifyGuestExit(session, faceProof, overrideCode, recognition);
        session.setExitPlate(PlateNormalizer.normalize(request.plateNumber()));
        session.setExitTime(LocalDateTime.now());
        session.setFee(calculateFee(session, session.getExitTime()));
        session.setStatus(SessionStatus.COMPLETED);
        session.setManualOverride(overrideCode != null);
        boolean faceVerified = faceProof != null && "PASS".equals(faceProof.decision());
        session.setExitFaceVerified(faceVerified);
        session.setExitFaceSimilarity(faceVerified ? faceProof.similarity() : null);
        session = sessions.save(session);
        if (overrideCode != null) {
            audit.record(authentication, "OVERRIDE_EXIT", "PARKING_SESSION", session.getId().toString(),
                "SUCCESS", overrideAuditReason(overrideCode, request.override().reason()), recognition.evidenceId());
        }
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

        if (slot.getStatusOverride() == SlotStatusOverride.BLOCKED) {
            throw new IllegalStateException("Không thể điều phối xe vào ô đang cấm đỗ / bảo trì.");
        }
        if (slot.getCurrentSession() != null && !slot.getCurrentSession().getId().equals(sessionId)) {
            throw new IllegalStateException("Ô này đang có xe đỗ, không thể điều phối xe khác vào.");
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
        return toSlotResponse(slot, true);
    }

    public ParkingSlotResponse toOperationalSlotResponse(ParkingSlot slot) {
        return toSlotResponse(slot, false);
    }

    private ParkingSlotResponse toSlotResponse(ParkingSlot slot, boolean includeResidentContacts) {
        LocalDateTime now = LocalDateTime.now();
        String assignedPlate = slot.getAssignedVehicle() == null ? "" : slot.getAssignedVehicle().getPlateNumber();
        String ownerName = !includeResidentContacts || slot.getAssignedVehicle() == null ? null
            : slot.getAssignedVehicle().getEffectiveOwnerName();
        String ownerPhone = !includeResidentContacts || slot.getAssignedVehicle() == null ? null
            : slot.getAssignedVehicle().getOwnerPhone();
        String apartment = !includeResidentContacts || slot.getAssignedVehicle() == null ? null
            : slot.getAssignedVehicle().getEffectiveApartmentNumber();

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
            .filter(s -> s.getAssignedVehicle() == null) // Đảm bảo ô chưa gán cho cư dân
            .filter(s -> {
                if (session.getVehicle() == null) {
                    return s.getSlotType() == SlotType.VISITOR_FLEXIBLE;
                }
                return true;
            })
            .filter(s -> s.getAllowedVehicleType() == null || s.getAllowedVehicleType().isCar() == isCar)
            .findFirst();

        candidate.ifPresent(item -> {
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

    private FamilyMember verifyDriver(Vehicle vehicle, Long memberId, String overrideCode, boolean faceVerified,
            GateEvidenceService.FaceProof faceProof, GateEvidenceService.Recognition recognition) {
        boolean manualOverride = overrideCode != null;
        if (vehicle == null) {
            if (manualOverride) throw new IllegalStateException("Manual override requires an authorized resident member");
            return null; // Khách vãng lai chỉ đối chiếu ảnh vào/ra ở nghiệp vụ riêng.
        }
        if (memberId == null) {
            throw new IllegalStateException("Hãy chọn thành viên gia đình đang điều khiển xe");
        }
        FamilyMember member = members.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy thành viên đã chọn"));
        boolean authorized = member.isActive() && vehicle.getAuthorizedMembers().stream()
            .anyMatch(item -> item.getId().equals(member.getId()));
        if (!authorized) throw new IllegalStateException("Thành viên này không được đăng ký sử dụng xe");
        if (member.getRegistrationFaceImagePath() == null || member.getRegistrationFaceImagePath().isBlank()) {
            if (!"MISSING_REFERENCE_IMAGE".equals(overrideCode))
                throw new IllegalStateException("Thành viên chưa có ảnh khuôn mặt đăng ký");
        } else if ("MISSING_REFERENCE_IMAGE".equals(overrideCode)) {
            throw new IllegalStateException("Manual override is only allowed when the registration face image is missing");
        } else if ("AI_REVIEW".equals(overrideCode)
                && faceProof != null && "REVIEW".equals(faceProof.decision())) {
            // A backend-linked REVIEW result requires the audited manual decision.
        } else if ("RECOGNITION_SERVICE_UNAVAILABLE".equals(overrideCode)
                && (recognition.recognitionUnavailable()
                    || "UNAVAILABLE".equals(recognition.faceVerificationStatus()))) {
            // Spring recorded an upstream outage for this operation; the reasoned override is auditable.
        } else if (!faceVerified) {
            throw new IllegalStateException("Khuôn mặt realtime chưa khớp ảnh đăng ký");
        }
        return member;
    }

    private void verifyGuestExit(ParkingSession session, GateEvidenceService.FaceProof faceProof,
            String overrideCode, GateEvidenceService.Recognition recognition) {
        boolean faceVerified = faceProof != null && "PASS".equals(faceProof.decision());
        boolean reviewed = "AI_REVIEW".equals(overrideCode) && faceProof != null
            && "REVIEW".equals(faceProof.decision());
        boolean faceServiceUnavailable = "RECOGNITION_SERVICE_UNAVAILABLE".equals(overrideCode)
            && recognition != null && "UNAVAILABLE".equals(recognition.faceVerificationStatus());
        if (session.getVehicle() == null && session.getEntryFaceImagePath() != null
            && !session.getEntryFaceImagePath().isBlank() && !faceVerified && !reviewed && !faceServiceUnavailable)
            throw new IllegalStateException("Khách vãng lai phải xác thực khuôn mặt ra với ảnh đã chụp lúc vào");
    }

    private static void validateExitFaceProof(ExitRequest request, GateEvidenceService.FaceProof faceProof) {
        if (faceProof != null && (!"PASS".equals(faceProof.decision()) && !"REVIEW".equals(faceProof.decision())
                || !java.util.Objects.equals(faceProof.familyMemberId(), request.familyMemberId())))
            throw new IllegalArgumentException("Face evidence does not match the selected exit operation");
    }

    private String validateEntryOverride(EntryRequest request, GateEvidenceService.FaceProof faceProof,
            GateEvidenceService.Recognition recognition,
            org.springframework.security.core.Authentication authentication) {
        if (request.manualOverride())
            throw new IllegalArgumentException("Client manualOverride claims are not accepted");
        if (faceProof != null && "REJECT".equals(faceProof.decision()))
            throw new IllegalStateException("Backend face verification rejected the operation");
        String exception = null;
        if (recognition != null) {
            if (recognition.recognitionUnavailable()) {
                if (faceProof == null || (!"PASS".equals(faceProof.decision())
                        && !"REVIEW".equals(faceProof.decision()))
                        || !java.util.Objects.equals(recognition.faceEvidenceId(), faceProof.evidenceId()))
                    throw new IllegalStateException("Backend face evidence is required when recognition is unavailable");
                exception = "REVIEW".equals(faceProof.decision()) ? "AI_REVIEW"
                    : "RECOGNITION_SERVICE_UNAVAILABLE";
            } else if ("UNAVAILABLE".equals(recognition.faceVerificationStatus())) {
                exception = "RECOGNITION_SERVICE_UNAVAILABLE";
            } else if ("REVIEW".equals(recognition.faceVerificationStatus())) {
                if (faceProof == null || !"REVIEW".equals(faceProof.decision())
                        || !java.util.Objects.equals(recognition.faceEvidenceId(), faceProof.evidenceId()))
                    throw new IllegalStateException("A linked backend REVIEW result is required");
                exception = "AI_REVIEW";
            }
        }
        if (exception == null && isMissingAuthorizedMemberImage(request, recognition))
            exception = "MISSING_REFERENCE_IMAGE";
        if (request.override() == null) {
            if (exception != null) throw new IllegalStateException("A reasoned manual override is required");
            return null;
        }
        authorizeOverride(authentication, request.override().reason());
        if (exception == null) throw new IllegalStateException("No supported override exception is present");
        return exception;
    }

    private String validateExitOverride(ExitRequest request, GateEvidenceService.FaceProof faceProof,
            GateEvidenceService.Recognition recognition,
            org.springframework.security.core.Authentication authentication) {
        if (request.manualOverride())
            throw new IllegalArgumentException("Client manualOverride claims are not accepted");
        if (faceProof != null && "REJECT".equals(faceProof.decision()))
            throw new IllegalStateException("Backend face verification rejected the operation");
        String exception = null;
        if (recognition != null) {
            if (recognition.recognitionUnavailable()) {
                if (faceProof == null || (!"PASS".equals(faceProof.decision())
                        && !"REVIEW".equals(faceProof.decision()))
                        || !java.util.Objects.equals(recognition.faceEvidenceId(), faceProof.evidenceId()))
                    throw new IllegalStateException("Backend face evidence is required when recognition is unavailable");
                exception = "REVIEW".equals(faceProof.decision()) ? "AI_REVIEW"
                    : "RECOGNITION_SERVICE_UNAVAILABLE";
            } else if ("UNAVAILABLE".equals(recognition.faceVerificationStatus())) {
                exception = "RECOGNITION_SERVICE_UNAVAILABLE";
            } else if ("REVIEW".equals(recognition.faceVerificationStatus())) {
                if (faceProof == null || !"REVIEW".equals(faceProof.decision())
                        || !java.util.Objects.equals(recognition.faceEvidenceId(), faceProof.evidenceId()))
                    throw new IllegalStateException("A linked backend REVIEW result is required");
                exception = "AI_REVIEW";
            }
        }
        if (request.override() == null) {
            if (exception != null) throw new IllegalStateException("A reasoned manual override is required");
            return null;
        }
        authorizeOverride(authentication, request.override().reason());
        if (exception == null) throw new IllegalStateException("No supported override exception is present");
        return exception;
    }

    private boolean isMissingAuthorizedMemberImage(EntryRequest request,
            GateEvidenceService.Recognition recognition) {
        if (recognition == null || request.familyMemberId() == null) return false;
        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase(recognition.plateNumber()).orElse(null);
        if (vehicle == null) return false;
        return members.findById(request.familyMemberId())
            .filter(FamilyMember::isActive)
            .filter(member -> vehicle.getAuthorizedMembers().stream()
                .anyMatch(authorized -> authorized.getId().equals(member.getId())))
            .map(member -> member.getRegistrationFaceImagePath() == null
                || member.getRegistrationFaceImagePath().isBlank())
            .orElse(false);
    }

    private static void authorizeOverride(org.springframework.security.core.Authentication authentication,
            String reason) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "OVERRIDE_CREATE".equals(authority.getAuthority())))
            throw new org.springframework.security.access.AccessDeniedException("Override permission is required");
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 450)
            throw new IllegalArgumentException("A specific override reason between 10 and 450 characters is required");
    }

    private static String overrideAuditReason(String exception, String reason) {
        return exception + ": " + reason.trim();
    }

    private void rejectClientVerificationClaims(boolean faceVerified, Double faceSimilarity, boolean manualOverride) {
        if (faceVerified || faceSimilarity != null || manualOverride) {
            throw new IllegalArgumentException("Client-supplied verification and override claims are not accepted");
        }
    }

    private void rejectClientFaceVerificationClaims(boolean faceVerified, Double faceSimilarity) {
        if (faceVerified || faceSimilarity != null) {
            throw new IllegalArgumentException("Client-supplied face verification claims are not accepted");
        }
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
