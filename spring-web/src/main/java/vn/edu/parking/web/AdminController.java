package vn.edu.parking.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.PlateNormalizer;
import vn.edu.parking.service.ResidentImageStorage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class AdminController {
    private final VehicleRepository vehicles;
    private final ParkingCardRepository cards;
    private final PricingRuleRepository prices;
    private final ParkingSessionRepository sessions;
    private final SubscriptionPaymentRepository payments;
    private final HouseholdRepository households;
    private final FamilyMemberRepository members;
    private final ParkingPolicyRepository policies;
    private final ResidentImageStorage imageStorage;
    private final ParkingSlotRepository slots;

    public AdminController(VehicleRepository vehicles, ParkingCardRepository cards,
            PricingRuleRepository prices, ParkingSessionRepository sessions,
            SubscriptionPaymentRepository payments, HouseholdRepository households,
            FamilyMemberRepository members, ParkingPolicyRepository policies,
            ResidentImageStorage imageStorage, ParkingSlotRepository slots) {
        this.vehicles = vehicles;
        this.cards = cards;
        this.prices = prices;
        this.sessions = sessions;
        this.payments = payments;
        this.households = households;
        this.members = members;
        this.policies = policies;
        this.imageStorage = imageStorage;
        this.slots = slots;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        var todayFrom = LocalDate.now().atStartOfDay();
        var todayTo = todayFrom.plusDays(1);
        BigDecimal visitRevenue = sessions
                .findByStatusAndExitTimeBetween(SessionStatus.COMPLETED, todayFrom, todayTo).stream()
                .map(ParkingSession::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subscriptionRevenue = payments.findByPaidAtBetween(todayFrom, todayTo).stream()
                .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        var monthFrom = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        var monthTo = monthFrom.plusMonths(1);
        BigDecimal monthVisitRevenue = sessions
                .findByStatusAndExitTimeBetween(SessionStatus.COMPLETED, monthFrom, monthTo).stream()
                .map(ParkingSession::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthSubscriptionRevenue = payments.findByPaidAtBetween(monthFrom, monthTo).stream()
                .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("vehicleCount", vehicles.count());
        model.addAttribute("householdCount", households.count());
        model.addAttribute("openCount", sessions.countByStatus(SessionStatus.OPEN));
        model.addAttribute("todayRevenue", visitRevenue.add(subscriptionRevenue));
        model.addAttribute("monthRevenue", monthVisitRevenue.add(monthSubscriptionRevenue));
        model.addAttribute("recentSessions", sessions.findAllByOrderByEntryTimeDesc().stream().limit(8).toList());
        return "dashboard";
    }

    @GetMapping("/vehicles")
    String vehicles(Model model) {
        model.addAttribute("vehicles", vehicles.findAll());
        model.addAttribute("types", VehicleType.values());
        model.addAttribute("fuelTypes", FuelType.values());
        model.addAttribute("households", households.findAll());
        model.addAttribute("members", members.findAllByOrderByFullNameAsc());
        model.addAttribute("policy", getPolicy());
        return "vehicles";
    }

    @GetMapping("/registrations")
    String registrations(Model model) {
        model.addAttribute("types", VehicleType.values());
        model.addAttribute("fuelTypes", FuelType.values());
        model.addAttribute("policy", getPolicy());
        return "registrations";
    }

    @PostMapping("/registrations")
    @Transactional
    String saveRegistration(@RequestParam String householdCode, @RequestParam String apartmentNumber,
            @RequestParam(required = false, defaultValue = "") String buildingName,
            @RequestParam(required = false, defaultValue = "") String contactPhone,
            @RequestParam(required = false) Integer maxTwoWheelers, @RequestParam(required = false) Integer maxCars,
            @RequestParam String fullName, @RequestParam String citizenId,
            @RequestParam(required = false, defaultValue = "") String oldCitizenId,
            @RequestParam(required = false) LocalDate dateOfBirth,
            @RequestParam(required = false, defaultValue = "") String gender,
            @RequestParam(required = false, defaultValue = "Chủ hộ") String relationshipToHead,
            @RequestParam(required = false, defaultValue = "") String phone,
            @RequestParam(required = false, defaultValue = "") String email,
            @RequestParam(required = false, defaultValue = "") String permanentAddress,
            @RequestParam(required = false) LocalDate cccdIssueDate,
            @RequestParam(required = false, defaultValue = "") String cccdIssuePlace,
            @RequestParam(required = false, defaultValue = "") String cccdQrRaw,
            @RequestParam(required = false) MultipartFile registrationFaceImage,
            @RequestParam(required = false, defaultValue = "") String registrationFaceCapture,
            @RequestParam String plateNumber, @RequestParam VehicleType vehicleType,
            @RequestParam FuelType fuelType, @RequestParam String registrationNumber,
            @RequestParam String brand, @RequestParam String modelName, @RequestParam String color,
            @RequestParam(required = false) Integer manufactureYear,
            @RequestParam String chassisNumber, @RequestParam String engineNumber,
            @RequestParam(required = false) Integer seatCount,
            @RequestParam(required = false) Integer cylinderCapacityCc,
            @RequestParam String registrationImagePath, RedirectAttributes redirect) {
        String normalizedCitizenId = citizenId.replaceAll("\\D", "");
        if (normalizedCitizenId.length() != 12)
            throw new IllegalArgumentException("Số CCCD phải gồm đúng 12 chữ số");
        String plate = PlateNormalizer.normalize(plateNumber);
        if (plate.length() < 5)
            throw new IllegalArgumentException("Biển số không hợp lệ");

        Household household = households.findByHouseholdCodeIgnoreCase(householdCode.trim()).orElseGet(Household::new);
        household.setHouseholdCode(householdCode.trim().toUpperCase());
        household.setApartmentNumber(requireText(apartmentNumber, "Căn hộ/phòng").toUpperCase());
        household.setBuildingName(buildingName.trim());
        household.setContactPhone(contactPhone.trim());
        household.setMaxTwoWheelers(normalizeOverride(maxTwoWheelers));
        household.setMaxCars(normalizeOverride(maxCars));
        household.setActive(true);
        household = households.save(household);

        FamilyMember member = members.findByCitizenId(normalizedCitizenId).orElseGet(FamilyMember::new);
        member.setHousehold(household);
        member.setFullName(requireText(fullName, "Họ tên"));
        member.setCitizenId(normalizedCitizenId);
        member.setOldCitizenId(oldCitizenId.replaceAll("\\D", ""));
        member.setDateOfBirth(dateOfBirth);
        member.setGender(gender.trim());
        member.setRelationshipToHead(relationshipToHead.trim());
        member.setPhone(phone.trim());
        member.setEmail(email.trim());
        member.setPermanentAddress(permanentAddress.trim());
        member.setCccdIssueDate(cccdIssueDate);
        member.setCccdIssuePlace(cccdIssuePlace.trim());
        member.setCccdQrRaw(cccdQrRaw.trim());
        member.setActive(true);
        String savedFace = imageStorage.save(registrationFaceImage, registrationFaceCapture);
        if (savedFace != null)
            member.setRegistrationFaceImagePath(savedFace);
        if (member.getRegistrationFaceImagePath() == null || member.getRegistrationFaceImagePath().isBlank())
            throw new IllegalArgumentException("Cần chụp hoặc tải ảnh khuôn mặt đăng ký");
        member = members.save(member);

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase(plate).orElseGet(Vehicle::new);
        enforceVehicleLimit(vehicle, household, vehicleType);
        vehicle.setPlateNumber(plate);
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(member);
        vehicle.setOwnerName(member.getFullName());
        vehicle.setOwnerPhone(member.getPhone());
        vehicle.setApartmentNumber(household.getApartmentNumber());
        vehicle.setVehicleType(vehicleType);
        vehicle.setFuelType(fuelType);
        vehicle.setRegistrationNumber(requireText(registrationNumber, "Số đăng ký xe"));
        vehicle.setBrand(requireText(brand, "Hãng xe"));
        vehicle.setModelName(requireText(modelName, "Dòng xe"));
        vehicle.setColor(requireText(color, "Màu xe"));
        vehicle.setManufactureYear(manufactureYear);
        vehicle.setChassisNumber(requireText(chassisNumber, "Số khung"));
        vehicle.setEngineNumber(requireText(engineNumber, "Số máy"));
        vehicle.setSeatCount(seatCount);
        vehicle.setCylinderCapacityCc(cylinderCapacityCc);
        vehicle.setRegistrationImagePath(requireText(registrationImagePath, "Ảnh/đường dẫn giấy đăng ký xe"));
        vehicle.setAuthorizedMembers(new LinkedHashSet<>(List.of(member)));
        vehicle.setActive(true);
        vehicle = vehicles.save(vehicle);

        redirect.addFlashAttribute("message",
                "Đã tạo hồ sơ hộ, cư dân và xe " + plate + ". Có thể đăng ký gói ngay bên dưới.");
        return "redirect:/cards?vehicleId=" + vehicle.getId();
    }

    @PostMapping("/vehicles")
    String saveVehicle(@RequestParam(required = false) Long vehicleId,
            @RequestParam String plateNumber,
            @RequestParam Long householdId,
            @RequestParam Long registeredOwnerId,
            @RequestParam(required = false) List<Long> authorizedMemberIds,
            @RequestParam VehicleType vehicleType,
            @RequestParam(required = false, defaultValue = "") String registrationNumber,
            @RequestParam(required = false, defaultValue = "") String brand,
            @RequestParam(required = false, defaultValue = "") String modelName,
            @RequestParam(required = false, defaultValue = "") String color,
            @RequestParam(required = false) Integer manufactureYear,
            @RequestParam(required = false, defaultValue = "") String chassisNumber,
            @RequestParam(required = false, defaultValue = "") String engineNumber,
            @RequestParam(required = false) Integer seatCount,
            @RequestParam(required = false) Integer cylinderCapacityCc,
            @RequestParam FuelType fuelType,
            @RequestParam(required = false, defaultValue = "") String registrationImagePath,
            RedirectAttributes redirect) {
        String plate = PlateNormalizer.normalize(plateNumber);
        if (plate.length() < 5)
            throw new IllegalArgumentException("Biển số không hợp lệ");
        Household household = households.findById(householdId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hộ gia đình"));
        FamilyMember owner = members.findById(registeredOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chủ đăng ký"));
        if (!owner.getHousehold().getId().equals(householdId)) {
            throw new IllegalArgumentException("Chủ đăng ký không thuộc hộ gia đình đã chọn");
        }
        Vehicle vehicle = vehicleId == null ? vehicles.findByPlateNumberIgnoreCase(plate).orElseGet(Vehicle::new)
                : vehicles.findById(vehicleId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phương tiện cần cập nhật"));
        vehicles.findByPlateNumberIgnoreCase(plate)
                .filter(other -> vehicle.getId() == null || !other.getId().equals(vehicle.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Biển số đã thuộc một phương tiện khác");
                });
        enforceVehicleLimit(vehicle, household, vehicleType);
        vehicle.setPlateNumber(plate);
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(owner);
        vehicle.setOwnerName(owner.getFullName());
        vehicle.setOwnerPhone(owner.getPhone());
        vehicle.setApartmentNumber(household.getApartmentNumber());
        vehicle.setVehicleType(vehicleType);
        vehicle.setRegistrationNumber(requireText(registrationNumber, "Số giấy đăng ký xe"));
        vehicle.setBrand(requireText(brand, "Hãng xe"));
        vehicle.setModelName(requireText(modelName, "Dòng xe"));
        vehicle.setColor(requireText(color, "Màu xe"));
        vehicle.setManufactureYear(manufactureYear);
        vehicle.setChassisNumber(requireText(chassisNumber, "Số khung"));
        vehicle.setEngineNumber(requireText(engineNumber, "Số máy"));
        vehicle.setSeatCount(seatCount);
        vehicle.setCylinderCapacityCc(cylinderCapacityCc);
        vehicle.setFuelType(fuelType);
        vehicle.setRegistrationImagePath(requireText(registrationImagePath, "Ảnh giấy đăng ký xe"));
        LinkedHashSet<FamilyMember> authorized = new LinkedHashSet<>();
        authorized.add(owner);
        if (authorizedMemberIds != null) {
            for (FamilyMember member : members.findAllById(authorizedMemberIds)) {
                if (!member.getHousehold().getId().equals(householdId)) {
                    throw new IllegalArgumentException("Người được phép sử dụng phải thuộc cùng hộ gia đình");
                }
                if (member.isActive())
                    authorized.add(member);
            }
        }
        vehicle.setAuthorizedMembers(authorized);
        vehicle.setActive(true);
        Vehicle savedVehicle = vehicles.save(vehicle);
        sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(plate, SessionStatus.OPEN)
                .ifPresent(openSession -> {
                    openSession.setVehicle(savedVehicle);
                    openSession.setDetectedVehicleType(savedVehicle.getVehicleType());
                    sessions.save(openSession);
                });
        redirect.addFlashAttribute("message",
                "Đã lưu xe " + plate + " và " + authorized.size() + " người được phép sử dụng");
        return "redirect:/vehicles";
    }

    @GetMapping("/admin-data/vehicles/{id}")
    @ResponseBody
    Map<String, Object> vehicleData(@PathVariable Long id) {
        Vehicle v = vehicles.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phương tiện"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", v.getId());
        data.put("householdId", v.getHousehold() == null ? null : v.getHousehold().getId());
        data.put("registeredOwnerId", v.getRegisteredOwner() == null ? null : v.getRegisteredOwner().getId());
        data.put("plateNumber", v.getPlateNumber());
        data.put("vehicleType", v.getVehicleType().name());
        data.put("fuelType", v.getFuelType().name());
        data.put("registrationNumber", v.getRegistrationNumber());
        data.put("brand", v.getBrand());
        data.put("modelName", v.getModelName());
        data.put("color", v.getColor());
        data.put("manufactureYear", v.getManufactureYear());
        data.put("chassisNumber", v.getChassisNumber());
        data.put("engineNumber", v.getEngineNumber());
        data.put("seatCount", v.getSeatCount());
        data.put("cylinderCapacityCc", v.getCylinderCapacityCc());
        data.put("registrationImagePath", v.getRegistrationImagePath());
        data.put("authorizedMemberIds", v.getAuthorizedMembers().stream().map(FamilyMember::getId).toList());
        return data;
    }

    @GetMapping("/households")
    String households(Model model) {
        model.addAttribute("households", households.findAll());
        model.addAttribute("members", members.findAllByOrderByFullNameAsc());
        model.addAttribute("policy", getPolicy());
        return "households";
    }

    @PostMapping("/households")
    String saveHousehold(@RequestParam String householdCode,
            @RequestParam String apartmentNumber,
            @RequestParam(required = false, defaultValue = "") String buildingName,
            @RequestParam(required = false, defaultValue = "") String contactPhone,
            @RequestParam(required = false) Integer maxTwoWheelers,
            @RequestParam(required = false) Integer maxCars,
            RedirectAttributes redirect) {
        Household household = households.findByHouseholdCodeIgnoreCase(householdCode.trim())
                .orElseGet(Household::new);
        household.setHouseholdCode(householdCode.trim().toUpperCase());
        household.setApartmentNumber(apartmentNumber.trim().toUpperCase());
        household.setBuildingName(buildingName.trim());
        household.setContactPhone(contactPhone.trim());
        household.setMaxTwoWheelers(normalizeOverride(maxTwoWheelers));
        household.setMaxCars(normalizeOverride(maxCars));
        household.setActive(true);
        households.save(household);
        redirect.addFlashAttribute("message", "Đã lưu hộ " + household.getHouseholdCode());
        return "redirect:/households";
    }

    @PostMapping("/members")
    String saveMember(@RequestParam(required = false) Long memberId,
            @RequestParam Long householdId,
            @RequestParam String fullName,
            @RequestParam(required = false, defaultValue = "") String citizenId,
            @RequestParam(required = false) LocalDate dateOfBirth,
            @RequestParam(required = false, defaultValue = "") String gender,
            @RequestParam(required = false, defaultValue = "") String relationshipToHead,
            @RequestParam(required = false, defaultValue = "") String phone,
            @RequestParam(required = false, defaultValue = "") String email,
            @RequestParam(required = false, defaultValue = "") String permanentAddress,
            @RequestParam(required = false) LocalDate cccdIssueDate,
            @RequestParam(required = false, defaultValue = "") String cccdIssuePlace,
            @RequestParam(required = false, defaultValue = "") String oldCitizenId,
            @RequestParam(required = false, defaultValue = "") String cccdQrRaw,
            @RequestParam(required = false) MultipartFile registrationFaceImage,
            @RequestParam(required = false, defaultValue = "") String registrationFaceCapture,
            RedirectAttributes redirect) {
        Household household = households.findById(householdId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hộ gia đình"));
        String normalizedCitizenId = citizenId.replaceAll("\\D", "");
        FamilyMember member = memberId == null
                ? (normalizedCitizenId.isBlank() ? new FamilyMember()
                        : members.findByCitizenId(normalizedCitizenId).orElseGet(FamilyMember::new))
                : members.findById(memberId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên cần cập nhật"));
        Long currentMemberId = member.getId();
        if (!normalizedCitizenId.isBlank()) {
            members.findByCitizenId(normalizedCitizenId)
                    .filter(other -> currentMemberId == null || !other.getId().equals(currentMemberId))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("Số CCCD đã thuộc một thành viên khác");
                    });
        }
        member.setHousehold(household);
        member.setFullName(fullName.trim());
        member.setCitizenId(normalizedCitizenId.isBlank() ? null : normalizedCitizenId);
        member.setDateOfBirth(dateOfBirth);
        member.setGender(gender.trim());
        member.setRelationshipToHead(relationshipToHead.trim());
        member.setPhone(phone.trim());
        member.setEmail(email.trim());
        member.setPermanentAddress(permanentAddress.trim());
        member.setCccdIssueDate(cccdIssueDate);
        member.setCccdIssuePlace(cccdIssuePlace.trim());
        member.setOldCitizenId(oldCitizenId.replaceAll("\\D", ""));
        member.setCccdQrRaw(cccdQrRaw.trim());
        String savedImage = imageStorage.save(registrationFaceImage, registrationFaceCapture);
        if (savedImage != null)
            member.setRegistrationFaceImagePath(savedImage);
        if (member.getRegistrationFaceImagePath() == null || member.getRegistrationFaceImagePath().isBlank())
            throw new IllegalArgumentException("Cần chụp hoặc tải lên ảnh khuôn mặt đăng ký");
        member.setActive(true);
        FamilyMember savedMember = members.save(member);
        // 1. Cập nhật thông tin cho các xe do thành viên này làm chủ
        List<Vehicle> ownedVehicles = vehicles.findByRegisteredOwnerId(savedMember.getId());
        for (Vehicle vehicle : ownedVehicles) {
            vehicle.setHousehold(household);
            vehicle.setOwnerName(savedMember.getFullName());
            vehicle.setOwnerPhone(savedMember.getPhone());
            vehicle.setApartmentNumber(household.getApartmentNumber());
            vehicle.getAuthorizedMembers().add(savedMember);
            vehicles.save(vehicle);
        }

        // 2. Gỡ quyền sử dụng ở những xe không còn chung hộ gia đình
        List<Vehicle> authorizedVehicles = vehicles.findByAuthorizedMembers_Id(savedMember.getId());
        for (Vehicle vehicle : authorizedVehicles) {
            if (vehicle.getHousehold() == null
                    || !vehicle.getHousehold().getId().equals(savedMember.getHousehold().getId())) {
                vehicle.getAuthorizedMembers().removeIf(item -> item.getId().equals(savedMember.getId()));
                vehicles.save(vehicle);
            }
        }
        redirect.addFlashAttribute("message", "Đã lưu thành viên " + savedMember.getFullName());
        return "redirect:/households";
    }

    @GetMapping("/admin-data/members/{id}")
    @ResponseBody
    Map<String, Object> memberData(@PathVariable Long id) {
        FamilyMember m = members.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.getId());
        data.put("householdId", m.getHousehold().getId());
        data.put("fullName", m.getFullName());
        data.put("citizenId", m.getCitizenId());
        data.put("oldCitizenId", m.getOldCitizenId());
        data.put("dateOfBirth", m.getDateOfBirth());
        data.put("gender", m.getGender());
        data.put("relationshipToHead", m.getRelationshipToHead());
        data.put("phone", m.getPhone());
        data.put("email", m.getEmail());
        data.put("permanentAddress", m.getPermanentAddress());
        data.put("cccdIssueDate", m.getCccdIssueDate());
        data.put("cccdIssuePlace", m.getCccdIssuePlace());
        data.put("cccdQrRaw", m.getCccdQrRaw());
        data.put("registrationFaceImagePath", m.getRegistrationFaceImagePath());
        return data;
    }

    @GetMapping("/settings")
    String settings(Model model) {
        model.addAttribute("policy", getPolicy());
        return "settings";
    }

    @GetMapping("/slots")
    String slots(Model model) {
        model.addAttribute("slots", slots.findAllByOrderBySlotCodeAsc());
        model.addAttribute("vehicles", vehicles.findAll());
        return "slots";
    }

    @PostMapping("/slots")
    String saveSlot(@RequestParam String slotCode,
            @RequestParam(required = false) Long assignedVehicleId,
            @RequestParam(required = false, defaultValue = "Khu A") String zoneName,
            RedirectAttributes redirect) {
        ParkingSlot slot = slots.findBySlotCodeIgnoreCase(slotCode.trim()).orElseGet(ParkingSlot::new);
        slot.setSlotCode(slotCode.trim().toUpperCase());
        slot.setZoneName(zoneName.trim());
        slot.setAssignedVehicle(assignedVehicleId == null ? null
                : vehicles.findById(assignedVehicleId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy xe được gán")));
        slot.setActive(true);
        slots.save(slot);
        redirect.addFlashAttribute("message", "Đã lưu vị trí " + slot.getSlotCode());
        return "redirect:/slots";
    }

    @PostMapping("/settings")
    String saveSettings(@RequestParam int defaultMaxTwoWheelers,
            @RequestParam int defaultMaxCars,
            RedirectAttributes redirect) {
        if (defaultMaxTwoWheelers < 0 || defaultMaxCars < 0) {
            throw new IllegalArgumentException("Hạn mức xe không được âm");
        }
        ParkingPolicy policy = getPolicy();
        policy.setDefaultMaxTwoWheelers(defaultMaxTwoWheelers);
        policy.setDefaultMaxCars(defaultMaxCars);
        policies.save(policy);
        redirect.addFlashAttribute("message", "Đã cập nhật hạn mức xe mặc định");
        return "redirect:/settings";
    }

    @GetMapping("/cards")
    String cards(@RequestParam(required = false) Long vehicleId, Model model) {
        model.addAttribute("cards", cards.findAll());
        model.addAttribute("vehicles", vehicles.findAll());
        model.addAttribute("statuses", CardStatus.values());
        model.addAttribute("passTypes", PassType.values());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("payments", payments.findAllByOrderByPaidAtDesc().stream().limit(20).toList());
        model.addAttribute("selectedVehicleId", vehicleId);
        return "cards";
    }

    @PostMapping("/cards")
    String saveCard(@RequestParam String cardCode,
            @RequestParam Long vehicleId,
            @RequestParam CardStatus status,
            @RequestParam PassType passType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(defaultValue = "30") int durationDays,
            RedirectAttributes redirect) {
        if (durationDays < 1 || durationDays > 366) {
            throw new IllegalArgumentException("Số ngày của gói phải từ 1 đến 366");
        }
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow();
        ParkingCard card = cards.findByCardCodeIgnoreCase(cardCode).orElseGet(ParkingCard::new);
        card.setCardCode(cardCode.trim().toUpperCase());
        card.setVehicle(vehicle);
        card.setStatus(status);
        card.setPassType(passType);

        if (passType == PassType.MONTHLY) {
            LocalDate from = startDate == null ? LocalDate.now() : startDate;
            LocalDate until = from.plusDays(durationDays - 1L);
            BigDecimal amount = prices.findByVehicleType(vehicle.getVehicleType())
                    .orElseThrow(
                            () -> new IllegalStateException("Chưa cấu hình bảng giá cho " + vehicle.getVehicleType()))
                    .getMonthlyPrice();
            if (amount.signum() <= 0) {
                throw new IllegalStateException(
                        "Chưa cấu hình giá gói tháng cho " + vehicle.getVehicleType().getDisplayName());
            }
            card.setValidFrom(from);
            card.setValidUntil(until);
            card.setSubscriptionFee(amount);
            card = cards.save(card);

            SubscriptionPayment payment = new SubscriptionPayment();
            payment.setParkingCard(card);
            payment.setPeriodStart(from);
            payment.setPeriodEnd(until);
            payment.setAmount(amount);
            payment.setPaidAt(LocalDateTime.now());
            payments.save(payment);
            redirect.addFlashAttribute("message", "Đã kích hoạt gói " + durationDays + " ngày đến " + until);
        } else {
            card.setValidFrom(null);
            card.setValidUntil(null);
            card.setSubscriptionFee(BigDecimal.ZERO);
            cards.save(card);
            redirect.addFlashAttribute("message", "Đã lưu thẻ vé lượt");
        }
        return "redirect:/cards";
    }

    @GetMapping("/pricing")
    String pricing(Model model) {
        model.addAttribute("prices", prices.findAll());
        model.addAttribute("types", VehicleType.values());
        return "pricing";
    }

    @PostMapping("/pricing")
    String savePrice(@RequestParam VehicleType vehicleType,
            @RequestParam BigDecimal basePrice,
            @RequestParam BigDecimal nightPrice,
            @RequestParam BigDecimal overnightFee,
            @RequestParam BigDecimal monthlyPrice,
            @RequestParam(defaultValue = "0") int baseHours,
            @RequestParam(defaultValue = "0") int extraBlockHours,
            @RequestParam(defaultValue = "0") BigDecimal extraBlockPrice,
            RedirectAttributes redirect) {
        if (List.of(basePrice, nightPrice, overnightFee, monthlyPrice, extraBlockPrice)
                .stream().anyMatch(value -> value.signum() < 0)) {
            throw new IllegalArgumentException("Giá không được âm");
        }
        if (vehicleType.isCar() && (baseHours < 1 || extraBlockHours < 1)) {
            throw new IllegalArgumentException("Ô tô phải có số giờ đầu và số giờ mỗi block lớn hơn 0");
        }
        PricingRule rule = prices.findByVehicleType(vehicleType).orElseGet(PricingRule::new);
        rule.setVehicleType(vehicleType);
        rule.setBasePrice(basePrice);
        rule.setNightPrice(nightPrice);
        rule.setOvernightFee(overnightFee);
        rule.setMonthlyPrice(monthlyPrice);
        rule.setBaseHours(vehicleType.isCar() ? baseHours : 0);
        rule.setExtraBlockHours(vehicleType.isCar() ? extraBlockHours : 0);
        rule.setExtraBlockPrice(vehicleType.isCar() ? extraBlockPrice : BigDecimal.ZERO);
        prices.save(rule);
        redirect.addFlashAttribute("message", "Đã cập nhật giá " + vehicleType.getDisplayName());
        return "redirect:/pricing";
    }

    @GetMapping("/sessions")
    String sessions(Model model) {
        model.addAttribute("sessions", sessions.findAllByOrderByEntryTimeDesc());
        return "sessions";
    }

    private ParkingPolicy getPolicy() {
        return policies.findById(1L).orElseGet(() -> policies.save(new ParkingPolicy()));
    }

    private Integer normalizeOverride(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " không được để trống");
        }
        return value.trim();
    }

    private void enforceVehicleLimit(Vehicle vehicle, Household household, VehicleType requestedType) {
        ParkingPolicy policy = getPolicy();
        int limit = requestedType.isCar()
                ? (household.getMaxCars() == null ? policy.getDefaultMaxCars() : household.getMaxCars())
                : (household.getMaxTwoWheelers() == null ? policy.getDefaultMaxTwoWheelers()
                        : household.getMaxTwoWheelers());
        long current = vehicles.findByHouseholdIdAndActiveTrue(household.getId()).stream()
                .filter(item -> item.getId() == null || vehicle.getId() == null
                        || !item.getId().equals(vehicle.getId()))
                .filter(item -> item.getVehicleType().isCar() == requestedType.isCar())
                .count();
        if (current >= limit) {
            throw new IllegalArgumentException("Hộ " + household.getHouseholdCode()
                    + " đã đạt hạn mức " + limit + (requestedType.isCar() ? " ô tô" : " xe hai bánh"));
        }
    }
}
