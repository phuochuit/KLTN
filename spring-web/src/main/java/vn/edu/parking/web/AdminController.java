package vn.edu.parking.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.PlateNormalizer;
import vn.edu.parking.service.ResidentImageStorage;
import vn.edu.parking.service.ParkingService;
import vn.edu.parking.service.SecurityAuditService;
import vn.edu.parking.service.SlotManagementService;
import vn.edu.parking.service.UiOptionService;
import vn.edu.parking.service.ManagementCoreWriteService;
import vn.edu.parking.service.CardManagementWriteService;
import vn.edu.parking.web.dto.*;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class AdminController {
    private static final Comparator<ParkingSlotResponse> SLOT_CODE_NATURAL_ORDER =
        Comparator.comparing(ParkingSlotResponse::slotCode, AdminController::compareNaturalSlotCodes)
            .thenComparing(ParkingSlotResponse::slotCode, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ParkingSlotResponse::slotCode)
            .thenComparing(ParkingSlotResponse::id);

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
    private final ParkingService parkingService;
    private final SecurityAuditService audit;
    private final ParkingFloorRepository floors;
    private final ParkingZoneRepository zones;
    private final VehicleTypeRepository vehicleTypes;
    private final UiOptionService uiOptionService;
    private final SlotManagementService slotManagementService;
    private final ManagementCoreWriteService managementWrites;
    private final CardManagementWriteService cardWrites;

    private static int compareNaturalSlotCodes(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = leftIndex;
                int rightEnd = rightIndex;
                while (leftEnd < left.length() && Character.isDigit(left.charAt(leftEnd))) leftEnd++;
                while (rightEnd < right.length() && Character.isDigit(right.charAt(rightEnd))) rightEnd++;
                int leftSignificant = leftIndex;
                int rightSignificant = rightIndex;
                while (leftSignificant < leftEnd - 1 && left.charAt(leftSignificant) == '0') leftSignificant++;
                while (rightSignificant < rightEnd - 1 && right.charAt(rightSignificant) == '0') rightSignificant++;
                int lengthOrder = Integer.compare(leftEnd - leftSignificant, rightEnd - rightSignificant);
                if (lengthOrder != 0) return lengthOrder;
                int digitOrder = left.regionMatches(leftSignificant, right, rightSignificant,
                    leftEnd - leftSignificant) ? 0 : left.substring(leftSignificant, leftEnd)
                        .compareTo(right.substring(rightSignificant, rightEnd));
                if (digitOrder != 0) return digitOrder;
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            int characterOrder = Character.compare(Character.toUpperCase(leftChar), Character.toUpperCase(rightChar));
            if (characterOrder != 0) return characterOrder;
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }

    public AdminController(VehicleRepository vehicles, ParkingCardRepository cards,
            PricingRuleRepository prices, ParkingSessionRepository sessions,
            SubscriptionPaymentRepository payments, HouseholdRepository households,
            FamilyMemberRepository members, ParkingPolicyRepository policies,
            ResidentImageStorage imageStorage, ParkingSlotRepository slots,
            ParkingService parkingService, SecurityAuditService audit, ParkingFloorRepository floors,
            ParkingZoneRepository zones, VehicleTypeRepository vehicleTypes,
            UiOptionService uiOptionService, SlotManagementService slotManagementService,
            ManagementCoreWriteService managementWrites, CardManagementWriteService cardWrites) {
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
        this.parkingService = parkingService;
        this.audit = audit;
        this.floors = floors;
        this.zones = zones;
        this.vehicleTypes = vehicleTypes;
        this.uiOptionService = uiOptionService;
        this.slotManagementService = slotManagementService;
        this.managementWrites = managementWrites;
        this.cardWrites = cardWrites;
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
    String vehicles(Model model, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") String page, @RequestParam(required = false) Long vehicleId) {
        String searchQuery = q == null ? "" : q.trim().substring(0, Math.min(q.trim().length(), 80));
        String searchPattern = literalContainsPattern(searchQuery);
        int pageNumber = parsePageNumber(page);
        Sort vehicleSort = Sort.by(Sort.Order.asc("plateNumber"), Sort.Order.asc("id"));
        Page<Vehicle> vehiclePage = vehicles.searchManagement(searchPattern, PageRequest.of(pageNumber, 10, vehicleSort));
        int availablePage = lastPage(vehiclePage, pageNumber);
        if (availablePage != pageNumber) {
            pageNumber = availablePage;
            vehiclePage = vehicles.searchManagement(searchPattern, PageRequest.of(pageNumber, 10, vehicleSort));
        }
        Vehicle selectedVehicle = vehicleId == null ? null : vehicles.findById(vehicleId).orElse(null);
        model.addAttribute("types", VehicleType.values());
        model.addAttribute("fuelTypes", FuelType.values());
        model.addAttribute("households", households.findAll());
        model.addAttribute("members", members.findAllByOrderByFullNameAsc());
        model.addAttribute("policy", getPolicy());
        model.addAttribute("vehiclePage", vehiclePage);
        model.addAttribute("vehicles", vehiclePage.getContent());
        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("pageNumber", pageNumber);
        model.addAttribute("pageSize", 10);
        model.addAttribute("selectedVehicle", selectedVehicle);
        model.addAttribute("selectedVehicleQuota", selectedVehicle == null || selectedVehicle.getHousehold() == null
                ? null : managementWrites.previewVehicleQuota(selectedVehicle.getHousehold().getId(),
                        selectedVehicle.getVehicleType(), selectedVehicle.getId()));
        return "vehicles";
    }

    @GetMapping("/registrations")
    String registrations(Model model) {
        model.addAttribute("types", VehicleType.values());
        model.addAttribute("fuelTypes", FuelType.values());
        model.addAttribute("packageTypes", List.of(PassType.DAILY, PassType.MONTHLY));
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("dailyPrices", prices.findAll().stream()
            .collect(Collectors.toMap(rule -> rule.getVehicleType().name(), PricingRule::getDailyPassPrice)));
        model.addAttribute("monthlyPrices", prices.findAll().stream()
            .collect(Collectors.toMap(rule -> rule.getVehicleType().name(), PricingRule::getMonthlyPrice)));
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
            @RequestParam(required = false) MultipartFile registrationImage,
            @RequestParam(required = false) PassType passType,
            @RequestParam(required = false) LocalDate packageStartDate,
            @RequestParam(defaultValue = "1") int packageDurationDays,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String paymentReference,
            RedirectAttributes redirect,
            Authentication authentication) {
        boolean canManageCards = authentication.getAuthorities().stream()
                .anyMatch(authority -> Set.of("CARD_MANAGE", "ROLE_MANAGEMENT")
                        .contains(authority.getAuthority()));
        Vehicle vehicle = managementWrites.saveRegistration(
                new ManagementCoreWriteService.HouseholdInput(householdCode, apartmentNumber, buildingName,
                        contactPhone, maxTwoWheelers, maxCars),
                new ManagementCoreWriteService.MemberInput(null, null, fullName, citizenId, oldCitizenId,
                        dateOfBirth, gender, relationshipToHead, phone, email, permanentAddress,
                        cccdIssueDate, cccdIssuePlace, cccdQrRaw, registrationFaceImage,
                        registrationFaceCapture),
                new ManagementCoreWriteService.VehicleInput(null, null, null, null, plateNumber, vehicleType,
                        fuelType, registrationNumber, brand, modelName, color, manufactureYear, chassisNumber,
                        engineNumber, seatCount, cylinderCapacityCc, registrationImage, null));

        if (passType != null) {
            CardManagementWriteService.SaveResult activation = cardWrites.saveCard(vehicle.getId(), CardStatus.ACTIVE,
                    passType, packageStartDate, packageDurationDays, paymentMethod, paymentReference);
            redirect.addFlashAttribute("message", "Đã lưu hồ sơ và kích hoạt " + passType.getDisplayName()
                    + " đến " + activation.validUntil() + ". Đã thu " + activation.amount()
                    + " đ. Biên nhận: " + activation.receiptNumber());
        } else {
            redirect.addFlashAttribute("message",
                    "Đã lưu hồ sơ hộ, cư dân và xe " + vehicle.getPlateNumber() + ".");
        }
        String destination = canManageCards ? "/cards" : "/vehicles";
        return "redirect:" + destination + "?vehicleId=" + vehicle.getId();
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
            @RequestParam(required = false) MultipartFile registrationImage,
            @RequestParam(required = false, defaultValue = "") String registrationImagePath,
            RedirectAttributes redirect) {
        Vehicle savedVehicle = managementWrites.saveVehicle(new ManagementCoreWriteService.VehicleInput(
                vehicleId, householdId, registeredOwnerId, authorizedMemberIds, plateNumber, vehicleType,
                fuelType, registrationNumber, brand, modelName, color, manufactureYear, chassisNumber,
                engineNumber, seatCount, cylinderCapacityCc, registrationImage, registrationImagePath));
        redirect.addFlashAttribute("message",
                "Đã lưu xe " + savedVehicle.getPlateNumber() + " và "
                        + savedVehicle.getAuthorizedMembers().size() + " người được phép sử dụng");
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
        data.put("registrationImageAvailable", v.getRegistrationImagePath() != null
                && !v.getRegistrationImagePath().isBlank());
        data.put("authorizedMemberIds", v.getAuthorizedMembers().stream().map(FamilyMember::getId).toList());
        return data;
    }

    @GetMapping("/admin-data/vehicles/households/{householdId}/quota-preview")
    @ResponseBody
    Map<String, Object> vehicleQuotaPreview(@PathVariable Long householdId, @RequestParam VehicleType vehicleType,
            @RequestParam(required = false) Long vehicleId) {
        ManagementCoreWriteService.VehicleQuotaPreview preview = managementWrites.previewVehicleQuota(
                householdId, vehicleType, vehicleId);
        return Map.of("activeCount", preview.activeCount(), "limit", preview.limit(),
                "householdSpecific", preview.householdSpecific());
    }

    @GetMapping("/households")
    String households(Model model,
            @RequestParam(defaultValue = "households") String mode,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "0") String residentPage,
            @RequestParam(required = false) Long householdId,
            @RequestParam(required = false) Long memberId) {
        String selectedMode = "residents".equals(mode) ? "residents" : "households";
        String searchQuery = q == null ? "" : q.trim().substring(0, Math.min(q.trim().length(), 80));
        String searchPattern = literalContainsPattern(searchQuery);
        int pageNumber = parsePageNumber(page);
        Sort householdSort = Sort.by(Sort.Order.asc("householdCode"), Sort.Order.asc("id"));
        PageRequest householdRequest = PageRequest.of(pageNumber, 10, householdSort);
        Page<Household> householdPage = selectedMode.equals("households")
                ? households.searchManagement(searchPattern, householdRequest)
                : households.searchManagement("%%", PageRequest.of(0, 10, householdSort));
        if (selectedMode.equals("households")) {
            int availablePage = lastPage(householdPage, pageNumber);
            if (availablePage != pageNumber) {
                pageNumber = availablePage;
                householdPage = households.searchManagement(searchPattern,
                        PageRequest.of(pageNumber, 10, householdSort));
            }
        }

        int residentPageNumber = parsePageNumber(residentPage);
        Sort residentSort = Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id"));
        PageRequest residentRequest = PageRequest.of(residentPageNumber, 10, residentSort);
        Page<FamilyMember> memberPage = selectedMode.equals("residents")
                ? members.searchManagement(searchPattern, PageRequest.of(pageNumber, 10, residentSort))
                : Page.empty(PageRequest.of(0, 10, residentSort));
        if (selectedMode.equals("residents")) {
            int availablePage = lastPage(memberPage, pageNumber);
            if (availablePage != pageNumber) {
                pageNumber = availablePage;
                memberPage = members.searchManagement(searchPattern, PageRequest.of(pageNumber, 10, residentSort));
            }
        }

        Household selectedHousehold = selectedMode.equals("households") && householdId != null
                ? households.findById(householdId).orElse(null) : null;
        Page<FamilyMember> householdResidents = selectedHousehold == null
                ? Page.empty(residentRequest)
                : members.findByHouseholdId(selectedHousehold.getId(), residentRequest);
        if (selectedHousehold != null) {
            int availablePage = lastPage(householdResidents, residentPageNumber);
            if (availablePage != residentPageNumber) {
                residentPageNumber = availablePage;
                householdResidents = members.findByHouseholdId(selectedHousehold.getId(),
                        PageRequest.of(residentPageNumber, 10, residentSort));
            }
        }

        FamilyMember selectedMember = selectedMode.equals("residents") && memberId != null
                ? members.findWithHouseholdById(memberId).orElse(null) : null;
        ParkingPolicy policy = getPolicy();
        List<Vehicle> activeHouseholdVehicles = selectedHousehold == null ? List.of()
                : vehicles.findByHouseholdIdAndActiveTrue(selectedHousehold.getId());
        long activeTwoWheelerCount = activeHouseholdVehicles.stream()
                .filter(vehicle -> vehicle.getVehicleType().isTwoWheeler()).count();
        long activeCarCount = activeHouseholdVehicles.stream()
                .filter(vehicle -> vehicle.getVehicleType().isCar()).count();
        int effectiveMaxTwoWheelers = selectedHousehold == null ? 0
                : selectedHousehold.getMaxTwoWheelers() == null
                        ? policy.getDefaultMaxTwoWheelers() : selectedHousehold.getMaxTwoWheelers();
        int effectiveMaxCars = selectedHousehold == null ? 0
                : selectedHousehold.getMaxCars() == null
                        ? policy.getDefaultMaxCars() : selectedHousehold.getMaxCars();
        List<Household> householdOptions = new java.util.ArrayList<>(householdPage.getContent());
        if (selectedHousehold != null && householdOptions.stream()
                .noneMatch(option -> option.getId().equals(selectedHousehold.getId()))) {
            householdOptions.add(selectedHousehold);
        }
        if (selectedMember != null && householdOptions.stream()
                .noneMatch(option -> option.getId().equals(selectedMember.getHousehold().getId()))) {
            householdOptions.add(selectedMember.getHousehold());
        }
        model.addAttribute("mode", selectedMode);
        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("pageNumber", pageNumber);
        model.addAttribute("pageSize", 10);
        model.addAttribute("householdPage", householdPage);
        model.addAttribute("memberPage", memberPage);
        model.addAttribute("householdTotal", households.count());
        model.addAttribute("memberTotal", members.count());
        model.addAttribute("householdResidents", householdResidents);
        model.addAttribute("residentPageNumber", residentPageNumber);
        model.addAttribute("selectedHousehold", selectedHousehold);
        model.addAttribute("selectedMember", selectedMember);
        model.addAttribute("householdOptions", householdOptions);
        model.addAttribute("activeTwoWheelerCount", activeTwoWheelerCount);
        model.addAttribute("activeCarCount", activeCarCount);
        model.addAttribute("effectiveMaxTwoWheelers", effectiveMaxTwoWheelers);
        model.addAttribute("effectiveMaxCars", effectiveMaxCars);
        model.addAttribute("households", householdPage.getContent());
        model.addAttribute("members", selectedMode.equals("residents")
                ? memberPage.getContent() : householdResidents.getContent());
        model.addAttribute("policy", policy);
        return "households";
    }

    private static String literalContainsPattern(String query) {
        String escaped = query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static int lastPage(Page<?> result, int requestedPage) {
        return result.getTotalPages() == 0 ? 0 : Math.min(requestedPage, result.getTotalPages() - 1);
    }

    @PostMapping("/households")
    String saveHousehold(@RequestParam String householdCode,
            @RequestParam String apartmentNumber,
            @RequestParam(required = false, defaultValue = "") String buildingName,
            @RequestParam(required = false, defaultValue = "") String contactPhone,
            @RequestParam(required = false) Integer maxTwoWheelers,
            @RequestParam(required = false) Integer maxCars,
            RedirectAttributes redirect) {
        Household household = managementWrites.saveHousehold(new ManagementCoreWriteService.HouseholdInput(
                householdCode, apartmentNumber, buildingName, contactPhone, maxTwoWheelers, maxCars));
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
        FamilyMember savedMember = managementWrites.saveMember(new ManagementCoreWriteService.MemberInput(
                memberId, householdId, fullName, citizenId, oldCitizenId, dateOfBirth, gender,
                relationshipToHead, phone, email, permanentAddress, cccdIssueDate, cccdIssuePlace,
                cccdQrRaw, registrationFaceImage, registrationFaceCapture));
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
        boolean faceAvailable = m.getRegistrationFaceImagePath() != null
            && !m.getRegistrationFaceImagePath().isBlank();
        data.put("faceImageAvailable", faceAvailable);
        data.put("faceImageUrl", faceAvailable ? "/admin-data/members/" + m.getId() + "/face-image" : null);
        return data;
    }

    @GetMapping("/admin-data/members/{id}/face-image")
    @ResponseBody
    ResponseEntity<byte[]> memberFaceImage(@PathVariable Long id) {
        FamilyMember member = members.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên"));
        byte[] bytes = imageStorage.readStoredReference(member.getRegistrationFaceImagePath());
        audit.recordCurrent("RESIDENT_FACE_VIEW", "FAMILY_MEMBER", id.toString(),
            "SUCCESS", null, null);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(residentImageMediaType(bytes))
            .body(bytes);
    }

    private static MediaType residentImageMediaType(byte[] bytes) {
        boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
            && bytes[2] == 0x4e && bytes[3] == 0x47;
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
            && bytes[2] == (byte) 0xff;
        if (png) return MediaType.IMAGE_PNG;
        if (jpeg) return MediaType.IMAGE_JPEG;
        throw new IllegalStateException("Ảnh cư dân không có MIME hợp lệ");
    }

    @GetMapping("/settings")
    String settings(Model model) {
        model.addAttribute("policy", getPolicy());
        return "settings";
    }

    @GetMapping("/slots")
    String slots(Model model,
                 @RequestParam(required = false) String floor,
                 @RequestParam(required = false) String zone,
                 @RequestParam(required = false) String status,
                 @RequestParam(required = false) String q) {
        List<ParkingSlot> allSlots = new ArrayList<>(slots.findByActiveTrueOrderBySlotCodeAsc());
        allSlots.sort(SlotCodeComparator.SLOT_COMPARATOR);

        List<String> floorNames = allSlots.stream()
                .map(ParkingSlot::getFloor)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted((left, right) -> {
                    List<String> order = List.of("Tầng trệt", "Tầng hầm B1", "Tầng hầm B2");
                    int leftIndex = order.indexOf(left);
                    int rightIndex = order.indexOf(right);
                    if (leftIndex >= 0 && rightIndex >= 0) return Integer.compare(leftIndex, rightIndex);
                    if (leftIndex >= 0) return -1;
                    if (rightIndex >= 0) return 1;
                    return left.compareToIgnoreCase(right);
                })
                .toList();
        if (floorNames.isEmpty()) floorNames = slots.findDistinctFloors();

        List<String> zoneNames = allSlots.stream()
                .map(ParkingSlot::getZoneName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (zoneNames.isEmpty()) zoneNames = slots.findDistinctZones();

        String effectiveFloor = floor != null && floorNames.stream().anyMatch(name -> name.equalsIgnoreCase(floor))
                ? floor : "";
        String effectiveZone = zone != null && zoneNames.stream().anyMatch(name -> name.equalsIgnoreCase(zone))
                ? zone : "";

        String selectedStatus = switch (status == null ? "" : status.trim().toUpperCase(Locale.ROOT)) {
            case "AVAILABLE", "RESERVED_EMPTY", "OCCUPIED_VALID", "OCCUPIED_BORROWED",
                    "RESERVED_BORROWED", "OCCUPIED_MISMATCH", "OVERDUE_ALERT", "BLOCKED" ->
                status.trim().toUpperCase(Locale.ROOT);
            default -> "";
        };
        String searchQuery = q == null ? "" : q.trim().substring(0, Math.min(q.trim().length(), 80));
        String normalizedQuery = searchQuery.toLowerCase(Locale.ROOT);

        List<ParkingSlotResponse> slotResponses = allSlots.stream()
            .filter(s -> effectiveFloor.isBlank() || effectiveFloor.equalsIgnoreCase(s.getFloor()))
            .filter(s -> effectiveZone.isBlank() || effectiveZone.equalsIgnoreCase(s.getZoneName()))
            .map(parkingService::toSlotResponse)
            .filter(s -> selectedStatus.isBlank() || selectedStatus.equals(s.status()))
            .filter(s -> normalizedQuery.isBlank()
                || (s.slotCode() + " " + s.assignedPlate() + " " + s.occupiedPlate() + " " + s.borrowedPlate())
                    .toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .sorted(SLOT_CODE_NATURAL_ORDER)
            .toList();

        Set<Long> visibleSlotIds = slotResponses.stream().map(ParkingSlotResponse::id).collect(Collectors.toSet());
        List<ParkingSlot> visibleSlots = allSlots.stream().filter(s -> visibleSlotIds.contains(s.getId())).toList();
        long totalSlots = visibleSlots.size();
        long availableCount = slotResponses.stream().filter(s -> "AVAILABLE".equals(s.status())).count();
        long occupiedCount = slotResponses.stream().filter(s -> s.status().startsWith("OCCUPIED_") || "OVERDUE_ALERT".equals(s.status())).count();
        long reservedCount = visibleSlots.stream().filter(s -> s.getAssignedVehicle() != null).count();
        long borrowedCount = visibleSlots.stream().filter(s -> s.getBorrowedPlate() != null && !s.getBorrowedPlate().isBlank()).count();
        long mismatchCount = slotResponses.stream().filter(s -> "OCCUPIED_MISMATCH".equals(s.status())).count();
        long blockedCount = slotResponses.stream().filter(s -> "BLOCKED".equals(s.status())).count();
        long overdueCount = slotResponses.stream().filter(s -> "OVERDUE_ALERT".equals(s.status())).count();
        long attentionCount = mismatchCount + blockedCount + overdueCount;

        Set<Long> assignedVehicleIds = allSlots.stream()
            .filter(s -> s.getAssignedVehicle() != null)
            .map(s -> s.getAssignedVehicle().getId())
            .collect(Collectors.toSet());
        List<Vehicle> unassignedVehicles = vehicles.findAll().stream()
            .filter(v -> v.isActive() && !assignedVehicleIds.contains(v.getId()))
            .toList();

        List<ParkingSession> unassignedSessions = sessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN).stream()
            .limit(30)
            .toList();

        model.addAttribute("slots", slotResponses);
        model.addAttribute("floors", floorNames);
        model.addAttribute("zones", zoneNames);
        model.addAttribute("selectedFloor", effectiveFloor);
        model.addAttribute("selectedZone", effectiveZone);
        model.addAttribute("floors", floors);
        model.addAttribute("zones", zones);
        model.addAttribute("selectedFloor", floor == null ? "" : floor);
        model.addAttribute("selectedZone", zone == null ? "" : zone);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("lastUpdated", LocalDateTime.now());
        model.addAttribute("activePage", "slots");
        model.addAttribute("totalSlots", totalSlots);
        model.addAttribute("allSlotCount", allSlots.size());
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("occupiedCount", occupiedCount);
        model.addAttribute("reservedCount", reservedCount);
        model.addAttribute("borrowedCount", borrowedCount);
        model.addAttribute("blockedCount", blockedCount);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("mismatchCount", mismatchCount);
        model.addAttribute("attentionCount", attentionCount);
        model.addAttribute("vehicles", vehicles.findAll());
        model.addAttribute("unassignedVehicles", unassignedVehicles);
        model.addAttribute("floors", floorNames);
        model.addAttribute("zones", zoneNames);
        model.addAttribute("selectedFloor", effectiveFloor);
        model.addAttribute("selectedZone", effectiveZone);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("lastUpdated", LocalDateTime.now());
        model.addAttribute("activePage", "slots");
        return "slots";
    }

    @PostMapping("/slots")
    String saveSlot(@RequestParam String slotCode,
            @RequestParam(required = false) Long assignedVehicleId,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false, defaultValue = "false") boolean isBasement,
            @RequestParam(required = false, defaultValue = "Khu A") String zoneName,
            @RequestParam(required = false, defaultValue = "RESIDENT_RESERVED") String slotType,
            @RequestParam(required = false, defaultValue = "CAR") String vehicleType,
            RedirectAttributes redirect) {
        if (slotCode == null || slotCode.trim().isEmpty()) {
            redirect.addFlashAttribute("error", "Mã vị trí không được để trống.");
            return "redirect:/slots";
        }
        if (floor == null || floor.trim().isEmpty()) {
            redirect.addFlashAttribute("error", "Vui lòng nhập hoặc chọn tên tầng.");
            return "redirect:/slots";
        }
        String cleanFloor = floor.trim();
        String cleanZone = zoneName.trim();
        String cleanCode = slotCode.trim().toUpperCase();

        try {
            ParkingFloor pf = floors.findByNameIgnoreCase(cleanFloor).orElseGet(() -> {
                ParkingFloor f = new ParkingFloor(cleanFloor, isBasement, 0, 500);
                return floors.save(f);
            });

            VehicleTypeEntity vt = vehicleTypes.findByCodeIgnoreCase(vehicleType.trim())
                    .or(() -> vehicleTypes.findByDisplayNameIgnoreCase(vehicleType.trim()))
                    .orElseGet(() -> {
                        VehicleType cat = "MOTORBIKE".equalsIgnoreCase(vehicleType) ? VehicleType.MOTORBIKE : VehicleType.CAR;
                        return vehicleTypes.findFirstByCategoryAndActiveTrueOrderByIdAsc(cat).orElse(null);
                    });

            if (vt != null && !prices.existsByVehicleTypeRef_Id(vt.getId())) {
                throw new IllegalStateException("Loại phương tiện '" + vt.getDisplayName() + "' chưa được thiết lập bảng giá. Vui lòng tạo bảng giá trước khi tạo ô đỗ.");
            }

            VehicleTypeEntity finalVt = vt;
            String prefix = cleanCode.substring(0, 1);
            ParkingZone pz = zones.findByFloor_IdAndNameIgnoreCase(pf.getId(), cleanZone).orElseGet(() -> {
                int unit = finalVt != null ? finalVt.getCapacityUnits() : 5;
                ParkingZone z = new ParkingZone(pf, cleanZone, prefix, finalVt, unit, null);
                return zones.save(z);
            });

            ParkingSlot slot = slots.findBySlotCodeIgnoreCase(cleanCode).orElseGet(ParkingSlot::new);
            if (slot.getId() != null && slot.isActive()) {
                throw new IllegalStateException("Mã vị trí " + cleanCode
                    + " đã tồn tại. Vui lòng chọn ô trên sơ đồ để chỉnh sửa.");
            }
            slot.setSlotCode(cleanCode);
            slot.setFloor(pf.getName());
            slot.setZoneName(pz.getName());
            slot.setZone(pz);
            slot.setVehicleTypeRef(finalVt);
            slot.setSlotType("VISITOR_FLEXIBLE".equalsIgnoreCase(slotType) || "VISITOR".equalsIgnoreCase(slotType) ? SlotType.VISITOR_FLEXIBLE : SlotType.RESIDENT_RESERVED);
            slot.setAllowedVehicleType(finalVt != null ? finalVt.getCategory() : VehicleType.CAR);
            slot.setActive(true);
            slot.setDeactivatedReason(null);
            slot = slots.save(slot);

            if (assignedVehicleId != null) {
                parkingService.assignVehicleToSlot(slot.getId(), assignedVehicleId);
            }

            uiOptionService.recordUse("floor", cleanFloor);
            uiOptionService.recordUse("zone", cleanZone);
            uiOptionService.recordUse("prefix", prefix);
            uiOptionService.recordUse("slotType", slotType);
            if (finalVt != null) {
                uiOptionService.recordUse("vehicleType", finalVt.getDisplayName());
            }

            redirect.addFlashAttribute("message", "Đã lưu vị trí " + slot.getSlotCode());
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/assign")
    String assignSlot(@RequestParam Long slotId,
                      @RequestParam(required = false) Long vehicleId,
                      RedirectAttributes redirect) {
        try {
            parkingService.assignVehicleToSlot(slotId, vehicleId);
            redirect.addFlashAttribute("message", "Đã cập nhật gán xe cho ô đỗ thành công");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/manage/update")
    String updateSlots(@RequestParam List<Long> slotIds,
            @RequestParam(required = false) String slotCode,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String zoneName,
            @RequestParam(required = false) String slotType,
            @RequestParam(required = false) String vehicleType,
            RedirectAttributes redirect) {
        try {
            int updated = slotManagementService.updateSlots(slotIds, slotCode, floor, zoneName, slotType, vehicleType);
            redirect.addFlashAttribute("message", "Đã cập nhật " + updated + " ô đỗ.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/manage/deactivate")
    String deactivateSlots(@RequestParam List<Long> slotIds, RedirectAttributes redirect) {
        try {
            int deactivated = slotManagementService.deactivateSlots(slotIds);
            redirect.addFlashAttribute("message", "Đã ngừng sử dụng " + deactivated + " ô đỗ.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/borrow")
    String borrowSlot(@RequestParam Long slotId,
                      @RequestParam String borrowedPlate,
                      @RequestParam(defaultValue = "2") int hours,
                      @RequestParam(required = false, defaultValue = "") String borrowNotes,
                      RedirectAttributes redirect) {
        try {
            parkingService.setupBorrowing(slotId, borrowedPlate, hours, borrowNotes);
            redirect.addFlashAttribute("message", "Đã thiết lập xe đỗ nhờ thành công (thời hạn " + hours + " giờ)");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/cancel-borrow")
    String cancelBorrow(@RequestParam Long slotId, RedirectAttributes redirect) {
        try {
            parkingService.cancelBorrowing(slotId);
            redirect.addFlashAttribute("message", "Đã hủy đỗ nhờ cho ô đỗ");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/status")
    String changeSlotStatus(@RequestParam Long slotId,
                            @RequestParam String statusOverride,
                            RedirectAttributes redirect) {
        try {
            SlotStatusOverride override = SlotStatusOverride.valueOf(statusOverride.toUpperCase());
            parkingService.setSlotStatusOverride(slotId, override);
            redirect.addFlashAttribute("message", "Đã chuyển trạng thái ô sang: " + override.getDisplayName());
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/release")
    String releaseSlot(@RequestParam Long slotId, RedirectAttributes redirect) {
        try {
            parkingService.releaseSlot(slotId);
            redirect.addFlashAttribute("message", "Đã giải phóng ô đỗ về trạng thái trống");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/dispatch-session")
    String dispatchSession(@RequestParam Long slotId,
                           @RequestParam Long sessionId,
                           RedirectAttributes redirect) {
        try {
            parkingService.dispatchSessionToSlot(slotId, sessionId);
            redirect.addFlashAttribute("message", "Đã điều phối xe vào ô thành công");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/slots/batch-generate")
    String batchGenerateSlots(BatchSlotGenerateRequest req, RedirectAttributes redirect) {
        try {
            int count = parkingService.batchGenerateSlots(req);
            if (req.floor() != null && !req.floor().isBlank()) uiOptionService.recordUse("floor", req.floor().trim());
            if (req.zoneName() != null && !req.zoneName().isBlank()) uiOptionService.recordUse("zone", req.zoneName().trim());
            if (req.prefix() != null && !req.prefix().isBlank()) uiOptionService.recordUse("prefix", req.prefix().trim().toUpperCase());
            if (req.slotType() != null && !req.slotType().isBlank()) uiOptionService.recordUse("slotType", req.slotType().trim());
            if (req.vehicleType() != null && !req.vehicleType().isBlank()) uiOptionService.recordUse("vehicleType", req.vehicleType().trim());
            redirect.addFlashAttribute("message", "Đã tạo thành công " + count + " ô đỗ mới");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/settings")
    String saveSettings(@RequestParam int defaultMaxTwoWheelers,
            @RequestParam int defaultMaxCars,
            RedirectAttributes redirect) {
        if (defaultMaxTwoWheelers < 0 || defaultMaxCars < 0) {
            throw new IllegalArgumentException("Hạn mức xe không được âm");
        }
        managementWrites.savePolicy(defaultMaxTwoWheelers, defaultMaxCars);
        redirect.addFlashAttribute("message", "Đã cập nhật hạn mức xe mặc định");
        return "redirect:/settings";
    }

    @GetMapping("/cards")
    String cards(@RequestParam(required = false) Long vehicleId, Model model) {
        model.addAttribute("cards", cards.findAll());
        model.addAttribute("vehicles", vehicles.findAll());
        model.addAttribute("dailyPrices", prices.findAll().stream()
            .collect(Collectors.toMap(rule -> rule.getVehicleType().name(), PricingRule::getDailyPassPrice)));
        model.addAttribute("monthlyPrices", prices.findAll().stream()
            .collect(Collectors.toMap(rule -> rule.getVehicleType().name(), PricingRule::getMonthlyPrice)));
        model.addAttribute("statuses", List.of(CardStatus.ACTIVE));
        model.addAttribute("passTypes", List.of(PassType.DAILY, PassType.MONTHLY));
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("payments", payments.findAllByOrderByPaidAtDesc().stream().limit(20).toList());
        model.addAttribute("selectedVehicleId", vehicleId);
        return "cards";
    }

    @PostMapping("/cards")
    String saveCard(@RequestParam Long vehicleId,
            @RequestParam CardStatus status,
            @RequestParam PassType passType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(defaultValue = "30") int durationDays,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String paymentReference,
            RedirectAttributes redirect) {
        if (status != CardStatus.ACTIVE) throw new IllegalArgumentException("Chỉ được kích hoạt gói ở trạng thái hoạt động");
        if (passType == PassType.MONTHLY) durationDays = 30;
        if (durationDays < 1 || durationDays > 366) throw new IllegalArgumentException("Số ngày của gói phải từ 1 đến 366");
        CardManagementWriteService.SaveResult result = cardWrites.saveCard(
            vehicleId, status, passType, startDate, durationDays, paymentMethod, paymentReference);
        redirect.addFlashAttribute("message", "Đã kích hoạt " + passType.getDisplayName()
            + " đến " + result.validUntil() + ". Đã thu " + result.amount()
            + " đ. Biên nhận: " + result.receiptNumber());
        return "redirect:/cards";
    }

    @GetMapping("/pricing")
    String pricing(Model model) {
        model.addAttribute("prices", prices.findAll());
        model.addAttribute("types", VehicleType.values());
        model.addAttribute("vehicleTypes", vehicleTypes.findByActiveTrueOrderByDisplayNameAsc());
        return "pricing";
    }

    @PostMapping("/pricing")
    String savePrice(@RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long vehicleTypeId,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam BigDecimal basePrice,
            @RequestParam BigDecimal nightPrice,
            @RequestParam BigDecimal overnightFee,
            @RequestParam BigDecimal dailyPassPrice,
            @RequestParam BigDecimal monthlyPrice,
            @RequestParam(defaultValue = "0") int baseHours,
            @RequestParam(defaultValue = "0") int extraBlockHours,
            @RequestParam(defaultValue = "0") BigDecimal extraBlockPrice,
            RedirectAttributes redirect) {
        if (List.of(basePrice, nightPrice, overnightFee, dailyPassPrice, monthlyPrice, extraBlockPrice)
                .stream().anyMatch(value -> value.signum() < 0)) {
            throw new IllegalArgumentException("Giá không được âm");
        }
        PricingRule rule = null;
        if (ruleId != null) {
            rule = prices.findById(ruleId).orElse(null);
        }
        if (rule == null && vehicleTypeId != null) {
            rule = prices.findByVehicleTypeRef_Id(vehicleTypeId).orElse(null);
        }
        if (rule == null && vehicleType != null) {
            rule = prices.findByVehicleType(vehicleType).orElse(null);
        }
        if (rule == null) {
            rule = new PricingRule();
            if (vehicleTypeId != null) {
                vehicleTypes.findById(vehicleTypeId).ifPresent(rule::setVehicleTypeRef);
            }
            rule.setVehicleType(vehicleType != null ? vehicleType : (rule.getVehicleTypeRef() != null ? rule.getVehicleTypeRef().getCategory() : VehicleType.CAR));
        }

        boolean isCar = rule.isCarCategory();
        if (isCar && (baseHours < 1 || extraBlockHours < 1)) {
            throw new IllegalArgumentException("Ô tô phải có số giờ đầu và số giờ mỗi block lớn hơn 0");
        }
        rule.setBasePrice(basePrice);
        rule.setNightPrice(nightPrice);
        rule.setOvernightFee(overnightFee);
        rule.setDailyPassPrice(dailyPassPrice);
        rule.setMonthlyPrice(monthlyPrice);
        rule.setBaseHours(isCar ? baseHours : 0);
        rule.setExtraBlockHours(isCar ? extraBlockHours : 0);
        rule.setExtraBlockPrice(isCar ? extraBlockPrice : BigDecimal.ZERO);
        prices.save(rule);
        redirect.addFlashAttribute("message", "Đã cập nhật giá " + rule.getDisplayTitle());
        return "redirect:/pricing";
    }

    @GetMapping("/sessions")
    String sessions(Model model, @RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "10") String size) {
        String searchQuery = q.trim().substring(0, Math.min(q.trim().length(), 80));
        String selectedFrom = from.trim();
        String selectedTo = to.trim();
        String escapedQuery = searchQuery.toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String searchPattern = escapedQuery.isBlank() ? "" : "%" + escapedQuery + "%";
        String selectedSort = "oldest".equals(sort) ? "oldest" : "newest";
        int pageNumber = parsePageNumber(page);
        int pageSize = parsePageSize(size);
        Sort.Direction direction = "oldest".equals(selectedSort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort historySort = Sort.by(direction, "entryTime").and(Sort.by(direction, "id"));
        LocalDateTime fromTime = null;
        LocalDateTime toExclusive = null;
        String dateRangeError = null;
        try {
            LocalDate fromDate = selectedFrom.isBlank() ? null : LocalDate.parse(selectedFrom);
            LocalDate toDate = selectedTo.isBlank() ? null : LocalDate.parse(selectedTo);
            if ((fromDate != null && (fromDate.getYear() < 1000 || fromDate.getYear() > 9999))
                    || (toDate != null && (toDate.getYear() < 1000 || toDate.getYear() > 9999))
                    || (fromDate != null && toDate != null && fromDate.isAfter(toDate))) {
                dateRangeError = "Khoảng ngày không hợp lệ. Hãy chọn ngày từ 01/01/1000 đến 31/12/9999 và ngày bắt đầu không sau ngày kết thúc.";
            } else {
                fromTime = fromDate == null ? null : fromDate.atStartOfDay();
                toExclusive = toDate == null || toDate.equals(LocalDate.of(9999, 12, 31))
                    ? null : toDate.plusDays(1).atStartOfDay();
            }
        } catch (DateTimeException exception) {
            dateRangeError = "Khoảng ngày không hợp lệ. Hãy nhập ngày theo định dạng hợp lệ.";
        }
        Page<ParkingSession> sessionPage = dateRangeError == null
            ? sessions.findHistoryPage(status, searchPattern, fromTime, toExclusive,
                PageRequest.of(pageNumber, pageSize, historySort))
            : Page.empty(PageRequest.of(0, pageSize, historySort));
        if (dateRangeError != null) pageNumber = 0;
        if (sessionPage.getTotalPages() == 0) {
            if (pageNumber > 0) {
                pageNumber = 0;
                if (dateRangeError == null) {
                    sessionPage = sessions.findHistoryPage(status, searchPattern, fromTime, toExclusive,
                        PageRequest.of(pageNumber, pageSize, historySort));
                }
            }
        } else if (pageNumber >= sessionPage.getTotalPages()) {
            pageNumber = sessionPage.getTotalPages() - 1;
            sessionPage = sessions.findHistoryPage(status, searchPattern, fromTime, toExclusive,
                PageRequest.of(pageNumber, pageSize, historySort));
        }

        model.addAttribute("sessions", sessionPage.getContent());
        model.addAttribute("sessionPage", sessionPage);
        Set<Long> plateMismatchSessionIds = sessionPage.getContent().stream()
            .filter(session -> session.getExitPlate() != null && !session.getExitPlate().isBlank()
                && session.getEntryPlate() != null && !session.getEntryPlate().isBlank()
                && !PlateNormalizer.normalize(session.getEntryPlate())
                    .equals(PlateNormalizer.normalize(session.getExitPlate())))
            .map(ParkingSession::getId).collect(Collectors.toSet());
        model.addAttribute("plateMismatchSessionIds", plateMismatchSessionIds);
        model.addAttribute("selectedStatus", status == null ? "" : status.name());
        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("selectedFrom", selectedFrom);
        model.addAttribute("selectedTo", selectedTo);
        model.addAttribute("dateRangeError", dateRangeError);
        model.addAttribute("selectedSort", selectedSort);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("pageNumber", pageNumber);
        model.addAttribute("firstResult", sessionPage.hasContent() ? (long) pageNumber * pageSize + 1 : 0);
        model.addAttribute("lastResult", (long) pageNumber * pageSize + sessionPage.getNumberOfElements());
        model.addAttribute("activePage", "sessions");
        return "sessions";
    }

    private static int parsePageNumber(String value) {
        try {
            return Math.max(0, Math.min(Integer.parseInt(value), 10_000));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int parsePageSize(String value) {
        try {
            int requested = Integer.parseInt(value);
            return Set.of(10, 20, 50).contains(requested) ? requested : 10;
        } catch (NumberFormatException ex) {
            return 10;
        }
    }

    @GetMapping("/api/parking/revenue/monthly")
    @ResponseBody
    List<Map<String, Object>> monthlyRevenue() {
        var result = new java.util.ArrayList<Map<String, Object>>();
        var now = LocalDate.now();
        for (int i = 11; i >= 0; i--) {
            var month = now.minusMonths(i);
            var from = month.withDayOfMonth(1).atStartOfDay();
            var to = from.plusMonths(1);
            BigDecimal visit = sessions
                    .findByStatusAndExitTimeBetween(SessionStatus.COMPLETED, from, to).stream()
                    .map(ParkingSession::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal subscription = payments.findByPaidAtBetween(from, to).stream()
                    .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var data = new LinkedHashMap<String, Object>();
            data.put("year", month.getYear());
            data.put("month", month.getMonthValue());
            data.put("label", month.getMonthValue() + "/" + month.getYear());
            data.put("visitRevenue", visit);
            data.put("subscriptionRevenue", subscription);
            data.put("totalRevenue", visit.add(subscription));
            result.add(data);
        }
        return result;
    }

    @GetMapping("/api/parking/revenue/filter")
    @ResponseBody
    Map<String, Object> revenueFilter(@RequestParam String type,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        LocalDateTime fromTime;
        LocalDateTime toTime;
        String label;
        if ("month".equals(type) && year != null && month != null) {
            fromTime = LocalDate.of(year, month, 1).atStartOfDay();
            toTime = fromTime.plusMonths(1);
            label = "Tháng " + month + "/" + year;
        } else if ("year".equals(type) && year != null) {
            fromTime = LocalDate.of(year, 1, 1).atStartOfDay();
            toTime = fromTime.plusYears(1);
            label = "Năm " + year;
        } else if ("range".equals(type) && from != null && to != null) {
            fromTime = from.atStartOfDay();
            toTime = to.plusDays(1).atStartOfDay();
            label = "Từ " + from + " đến " + to;
        } else {
            fromTime = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            toTime = fromTime.plusMonths(1);
            label = "Tháng " + LocalDate.now().getMonthValue() + "/" + LocalDate.now().getYear();
        }
        var sessionList = sessions
                .findByStatusAndExitTimeBetween(SessionStatus.COMPLETED, fromTime, toTime).stream()
                .sorted((a, b) -> b.getExitTime().compareTo(a.getExitTime()))
                .toList();
        var paymentList = payments.findByPaidAtBetween(fromTime, toTime).stream()
                .sorted((a, b) -> b.getPaidAt().compareTo(a.getPaidAt()))
                .toList();
        BigDecimal visitRevenue = sessionList.stream()
                .map(ParkingSession::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subscriptionRevenue = paymentList.stream()
                .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var data = new LinkedHashMap<String, Object>();
        data.put("label", label);
        data.put("visitRevenue", visitRevenue);
        data.put("subscriptionRevenue", subscriptionRevenue);
        data.put("totalRevenue", visitRevenue.add(subscriptionRevenue));
        data.put("householdCount", households.countByCreatedAtBefore(toTime));
        data.put("vehicleCount", vehicles.countByCreatedAtBefore(toTime));
        data.put("openCount", sessions.countPresentAt(toTime));
        data.put("todayRevenue", visitRevenue.add(subscriptionRevenue));
        data.put("monthRevenue", visitRevenue.add(subscriptionRevenue));
        data.put("sessions", sessionList);
        data.put("payments", paymentList);
        return data;
    }

    @GetMapping(value = {"/api/parking/revenue/export-csv", "/revenue/export-csv"}, produces = "text/csv; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<byte[]> exportRevenueCsv(@RequestParam(defaultValue = "month") String type,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        LocalDateTime fromTime;
        LocalDateTime toTime;
        String periodLabel;
        if ("month".equals(type) && year != null && month != null) {
            fromTime = LocalDate.of(year, month, 1).atStartOfDay();
            toTime = fromTime.plusMonths(1);
            periodLabel = "thang_" + month + "_" + year;
        } else if ("year".equals(type) && year != null) {
            fromTime = LocalDate.of(year, 1, 1).atStartOfDay();
            toTime = fromTime.plusYears(1);
            periodLabel = "nam_" + year;
        } else if ("range".equals(type) && from != null && to != null) {
            fromTime = from.atStartOfDay();
            toTime = to.plusDays(1).atStartOfDay();
            periodLabel = "tu_" + from + "_den_" + to;
        } else {
            fromTime = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            toTime = fromTime.plusMonths(1);
            periodLabel = "thang_" + LocalDate.now().getMonthValue() + "_" + LocalDate.now().getYear();
        }
        var sessionList = sessions.findByStatusAndExitTimeBetween(SessionStatus.COMPLETED, fromTime, toTime).stream()
                .sorted((a, b) -> b.getExitTime().compareTo(a.getExitTime())).toList();
        var paymentList = payments.findByPaidAtBetween(fromTime, toTime).stream()
                .sorted((a, b) -> b.getPaidAt().compareTo(a.getPaidAt())).toList();
        StringBuilder csv = new StringBuilder(CsvUtils.UTF_8_BOM)
                .append("Mã giao dịch / Phiên,Phân loại doanh thu,Biển số xe,Thời gian bắt đầu / Vào,Thời gian kết thúc / Ra / Thu,Thời lượng / Kỳ hạn,Số tiền (VNĐ),Trạng thái,Phương thức / Loại xe\r\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        BigDecimal visitTotal = BigDecimal.ZERO;
        for (ParkingSession session : sessionList) {
            BigDecimal amount = session.getFee() == null ? BigDecimal.ZERO : session.getFee();
            visitTotal = visitTotal.add(amount);
            String duration = "";
            if (session.getEntryTime() != null && session.getExitTime() != null) {
                long minutes = java.time.Duration.between(session.getEntryTime(), session.getExitTime()).toMinutes();
                duration = minutes / 60 + "h " + minutes % 60 + "m";
            }
            csv.append(CsvUtils.escape(session.getId())).append(",Vé lượt,")
                .append(CsvUtils.escape(session.getEntryPlate())).append(",")
                .append(CsvUtils.escape(session.getEntryTime() == null ? "" : session.getEntryTime().format(formatter))).append(",")
                .append(CsvUtils.escape(session.getExitTime() == null ? "" : session.getExitTime().format(formatter))).append(",")
                .append(CsvUtils.escape(duration)).append(",").append(amount.toPlainString()).append(",")
                .append(CsvUtils.escape(session.getStatus().getDisplayName())).append(",")
                .append(CsvUtils.escape(session.getDetectedVehicleType() == null
                        ? "" : session.getDetectedVehicleType().name())).append("\r\n");
        }
        BigDecimal packageTotal = BigDecimal.ZERO;
        for (SubscriptionPayment payment : paymentList) {
            BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
            packageTotal = packageTotal.add(amount);
            String plate = payment.getParkingCard() != null && payment.getParkingCard().getVehicle() != null
                    ? payment.getParkingCard().getVehicle().getPlateNumber()
                    : payment.getParkingCard() == null ? "" : payment.getParkingCard().getCardCode();
            String period = (payment.getPeriodStart() == null ? "" : payment.getPeriodStart().toString()) + " -> "
                    + (payment.getPeriodEnd() == null ? "" : payment.getPeriodEnd().toString());
            csv.append(CsvUtils.escape(payment.getId())).append(",Gói cư dân,")
                .append(CsvUtils.escape(plate)).append(",")
                .append(CsvUtils.escape(payment.getPaidAt() == null ? "" : payment.getPaidAt().format(formatter))).append(",")
                .append(CsvUtils.escape(payment.getPeriodEnd() == null ? "" : payment.getPeriodEnd().toString())).append(",")
                .append(CsvUtils.escape(period)).append(",").append(amount.toPlainString()).append(",Đã ghi nhận,")
                .append(CsvUtils.escape("Gói thẻ " + (payment.getParkingCard() == null ? "" : payment.getParkingCard().getCardCode())))
                .append("\r\n");
        }
        BigDecimal grandTotal = visitTotal.add(packageTotal);
        csv.append("TỔNG CỘNG,Tất cả nguồn,,,,,").append(grandTotal.toPlainString()).append(",,Vé lượt: ")
            .append(visitTotal.toPlainString()).append(" | Gói: ").append(packageTotal.toPlainString()).append("\r\n");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"doanh_thu_" + periodLabel + ".csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/api/parking/revenue/monthly/{year}/{month}")
    @ResponseBody
    Map<String, Object> monthlyRevenueDetail(@PathVariable int year, @PathVariable int month) {
        var from = LocalDate.of(year, month, 1).atStartOfDay();
        var to = from.plusMonths(1);
        var sessionList = sessions
                .findByStatusAndExitTimeBetween(SessionStatus.COMPLETED, from, to).stream()
                .sorted((a, b) -> b.getExitTime().compareTo(a.getExitTime()))
                .toList();
        var paymentList = payments.findByPaidAtBetween(from, to).stream()
                .sorted((a, b) -> b.getPaidAt().compareTo(a.getPaidAt()))
                .toList();
        BigDecimal visitRevenue = sessionList.stream()
                .map(ParkingSession::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subscriptionRevenue = paymentList.stream()
                .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var data = new LinkedHashMap<String, Object>();
        data.put("year", year);
        data.put("month", month);
        data.put("visitRevenue", visitRevenue);
        data.put("subscriptionRevenue", subscriptionRevenue);
        data.put("totalRevenue", visitRevenue.add(subscriptionRevenue));
        data.put("sessions", sessionList);
        data.put("payments", paymentList);
        return data;
    }

    @GetMapping("/api/parking/revenue/stats/{year}/{month}")
    @ResponseBody
    Map<String, Object> monthlyStats(@PathVariable int year, @PathVariable int month) {
        var from = LocalDate.of(year, month, 1).atStartOfDay();
        var to = from.plusMonths(1);
        var monthSessions = sessions.findByStatusAndExitTimeBetween(SessionStatus.COMPLETED, from, to);
        var monthPayments = payments.findByPaidAtBetween(from, to);
        BigDecimal visitRevenue = monthSessions.stream()
                .map(ParkingSession::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subscriptionRevenue = monthPayments.stream()
                .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var data = new LinkedHashMap<String, Object>();
        data.put("householdCount", households.countByCreatedAtBefore(to));
        data.put("vehicleCount", vehicles.countByCreatedAtBefore(to));
        data.put("openCount", sessions.countPresentAt(to));
        data.put("todayRevenue", visitRevenue.add(subscriptionRevenue));
        data.put("monthRevenue", visitRevenue.add(subscriptionRevenue));
        data.put("visitRevenue", visitRevenue);
        data.put("subscriptionRevenue", subscriptionRevenue);
        data.put("totalRevenue", visitRevenue.add(subscriptionRevenue));
        return data;
    }

    private ParkingPolicy getPolicy() {
        return policies.findById(1L).orElseGet(ParkingPolicy::new);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " không được để trống");
        }
        return value.trim();
    }

}
