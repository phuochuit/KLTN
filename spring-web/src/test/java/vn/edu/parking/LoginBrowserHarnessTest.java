package vn.edu.parking;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.FamilyMember;
import vn.edu.parking.domain.FuelType;
import vn.edu.parking.domain.Household;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.DesktopSession;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.domain.SecurityRole;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.ParkingSlot;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.domain.SlotStatusOverride;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.DesktopSessionRepository;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.service.AiProxyService;
import vn.edu.parking.service.ResidentImageStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in synthetic browser fixture, not a live ANPR/production E2E test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LoginBrowserHarnessTest.FixtureOnlyWebHooks.class)
@EnabledIfSystemProperty(named = "parking.test.login-browser.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginBrowserHarnessTest {
    static final String MANAGER_USERNAME = "lumipark-browser-manager";
    static final String FORCED_CHANGE_USERNAME = "lumipark-browser-change";
    static final String THROTTLE_USERNAME = "lumipark-browser-throttle";
    static final String ACCOUNT_ONLY_USERNAME = "lumipark-browser-account-only";
    static final String SHIFT_ONLY_USERNAME = "lumipark-browser-shift-only";
    static final String AUDIT_ONLY_USERNAME = "lumipark-browser-audit-only";
    static final String ACCOUNT_SHIFT_USERNAME = "lumipark-browser-account-shift";
    static final String GATE_ONLY_USERNAME = "lumipark-browser-gate-only";
    static final String TEMP_SECRET_SENTINEL = "B12-NONSECRET-FIXTURE-SENTINEL";
    private static Path runDirectory;

    @LocalServerPort int port;
    @Autowired SystemAccountRepository accounts;
    @Autowired ParkingSessionRepository sessions;
    @Autowired ParkingSlotRepository slots;
    @Autowired SecurityAuditRepository auditEvents;
    @Autowired SecurityRoleRepository roles;
    @Autowired GateShiftAssignmentRepository shifts;
    @Autowired DesktopSessionRepository desktopSessions;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired VehicleRepository vehicles;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ResidentImageStorage residentImageStorage;
    @MockitoBean AiProxyService aiProxy;

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry registry) throws IOException {
        String supplied = System.getProperty("parking.test.login-browser.run-directory");
        if (supplied == null || supplied.isBlank())
            throw new IllegalStateException("Set parking.test.login-browser.run-directory to a fresh existing run directory");
        Path root = LoginTestIsolation.workspaceRoot();
        runDirectory = Path.of(supplied).toRealPath();
        if (!(isChild(runDirectory, root.resolve(".artifacts/test-runs"))
                || isChild(runDirectory, root.resolve(".cache/test-temp"))))
            throw new IllegalStateException("Browser run directory must be below .artifacts/test-runs or .cache/test-temp");
        if (!Files.isDirectory(runDirectory) || Files.exists(runDirectory.resolve("server.port"))
                || Files.exists(runDirectory.resolve("stop")))
            throw new IllegalStateException("Browser harness requires a fresh run directory without server.port or stop");
        LoginTestIsolation.register(registry, "login-browser-test");
    }

    @Test
    @Timeout(value = 16, unit = TimeUnit.MINUTES)
    void servesSyntheticLoginUntilStoppedOrFifteenMinutesElapse() throws Exception {
        assertEquals(0L, accounts.count(), "No bootstrap or runtime accounts may exist in this test database");
        String hash = passwordEncoder.encode(LoginTestIsolation.PASSWORD);
        SystemAccount manager = accounts.saveAndFlush(
            new SystemAccount(MANAGER_USERNAME, hash, AccountRole.MANAGEMENT, false));
        manager.setSecurityRole(roles.findByCode(AccountRole.MANAGEMENT.name()).orElseThrow());
        manager = accounts.saveAndFlush(manager);
        accounts.save(new SystemAccount(FORCED_CHANGE_USERNAME, hash, AccountRole.MANAGEMENT, true));
        accounts.saveAndFlush(new SystemAccount(THROTTLE_USERNAME, hash, AccountRole.MANAGEMENT, false));
        boolean b12Fixture = Boolean.getBoolean("parking.test.login-browser.b12-fixture");
        if (b12Fixture) seedLimitedGrantAccounts(hash);
        seedManagementRecords();
        seedParkingOperations();
        seedHistoryAndShift(manager);
        if (b12Fixture) Files.writeString(runDirectory.resolve("b12.fixture.ready"), "ready", StandardCharsets.US_ASCII,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.writeString(runDirectory.resolve("server.port"), Integer.toString(port), StandardCharsets.US_ASCII,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        long deadline = System.nanoTime() + Duration.ofMinutes(15).toNanos();
        while (!Files.exists(runDirectory.resolve("stop")) && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
    }

    private void seedLimitedGrantAccounts(String hash) {
        seedLimitedGrantAccount(ACCOUNT_ONLY_USERNAME, hash, "B12_ACCOUNT_ONLY", "BACA",
            EnumSet.of(SecurityPermission.ACCOUNT_MANAGE));
        seedLimitedGrantAccount(SHIFT_ONLY_USERNAME, hash, "B12_SHIFT_ONLY", "BACS",
            EnumSet.of(SecurityPermission.SHIFT_MANAGE, SecurityPermission.GATE_OPERATE));
        seedLimitedGrantAccount(AUDIT_ONLY_USERNAME, hash, "B12_AUDIT_ONLY", "BACU",
            EnumSet.of(SecurityPermission.SECURITY_AUDIT_VIEW));
        seedLimitedGrantAccount(ACCOUNT_SHIFT_USERNAME, hash, "B12_ACCOUNT_SHIFT", "BACC",
            EnumSet.of(SecurityPermission.ACCOUNT_MANAGE, SecurityPermission.SHIFT_MANAGE,
                SecurityPermission.GATE_OPERATE));
        seedLimitedGrantAccount(GATE_ONLY_USERNAME, hash, "B12_GATE_ONLY", "BACG",
            EnumSet.of(SecurityPermission.GATE_OPERATE));
    }

    private void seedLimitedGrantAccount(String username, String hash, String roleCode, String prefix,
            Set<SecurityPermission> permissions) {
        SecurityRole role = roles.saveAndFlush(new SecurityRole(roleCode, roleCode, prefix,
            "Synthetic browser grant fixture", false, permissions));
        SystemAccount account = accounts.saveAndFlush(new SystemAccount(username,
            "B12 synthetic " + roleCode, hash, AccountRole.GATE_STAFF, false));
        account.setSecurityRole(role);
        account.setEmployeeCode(prefix + "-001");
        account.setPhone("+849" + String.format("%08d", Math.abs((long) username.hashCode()) % 100_000_000));
        account.setEmail(username + "@example.test");
        account.setCitizenId(String.format("%012d", Math.abs((long) username.hashCode()) % 1_000_000_000_000L));
        account.setAddress("Synthetic B12 fixture");
        account.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
        accounts.saveAndFlush(account);
    }

    private void seedManagementRecords() {
        String residentImageReference = Boolean.getBoolean("parking.test.login-browser.b12-fixture")
            ? seedSyntheticResidentImage() : null;
        Household household = new Household();
        household.setHouseholdCode("UXQA-HOUSEHOLD");
        household.setApartmentNumber("UXQA-101");
        household.setBuildingName("Synthetic browser fixture");
        household.setContactPhone("0000000000");
        household.setMaxTwoWheelers(2);
        household.setMaxCars(1);
        household = households.saveAndFlush(household);

        FamilyMember resident = new FamilyMember();
        resident.setHousehold(household);
        resident.setFullName("Synthetic browser resident");
        resident.setRelationshipToHead("Synthetic resident");
        if (residentImageReference != null) resident.setRegistrationFaceImagePath(residentImageReference);
        resident = members.saveAndFlush(resident);

        Household secondHousehold = new Household();
        secondHousehold.setHouseholdCode("UXQA-HOUSEHOLD-2");
        secondHousehold.setApartmentNumber("UXQA-202");
        secondHousehold = households.saveAndFlush(secondHousehold);
        FamilyMember secondResident = new FamilyMember();
        secondResident.setHousehold(secondHousehold);
        secondResident.setFullName("Synthetic browser resident two");
        secondResident = members.saveAndFlush(secondResident);

        seedVehicle(household, resident, "UXQA-MOTO-01", "01");
        seedVehicle(household, resident, "UXQA-MOTO-02", "02");
    }

    private String seedSyntheticResidentImage() {
        try {
            Path image = residentImageStorage.getPrivateRoot().resolve("00000000-0000-4000-8000-000000000001.jpg");
            BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            try {
                if (!ImageIO.write(pixel, "jpg", image.toFile()))
                    throw new IllegalStateException("No JPEG writer available for synthetic browser fixture");
            } finally {
                pixel.flush();
            }
            return "resident:00000000-0000-4000-8000-000000000001.jpg";
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create synthetic resident fixture image", exception);
        }
    }

    private void seedVehicle(Household household, FamilyMember owner, String plate, String suffix) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(owner);
        vehicle.setAuthorizedMembers(Set.of(owner));
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(owner.getFullName());
        vehicle.setOwnerPhone(owner.getPhone());
        vehicle.setApartmentNumber(household.getApartmentNumber());
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicle.setFuelType(FuelType.GASOLINE);
        vehicle.setRegistrationNumber("UXQA-REG-" + suffix);
        vehicle.setBrand("Synthetic");
        vehicle.setModelName("Browser fixture");
        vehicle.setColor("Blue");
        vehicle.setChassisNumber("SYNTHETIC-CHASSIS-" + suffix);
        vehicle.setEngineNumber("SYNTHETIC-ENGINE-" + suffix);
        vehicle.setRegistrationImagePath("private/synthetic-registration-reference-" + suffix + ".jpg");
        vehicles.saveAndFlush(vehicle);
    }

    private void seedParkingOperations() {
        LocalDateTime now = LocalDateTime.now();
        for (String code : List.of("UXQA-A39", "UXQA-A4", "UXQA-A10", "UXQA-A2")) {
            ParkingSlot naturalOrder = new ParkingSlot();
            naturalOrder.setSlotCode(code);
            naturalOrder.setFloor("QA synthetic");
            naturalOrder.setZoneName("Sort review");
            slots.saveAndFlush(naturalOrder);
        }
        ParkingSlot assigned = slots.findBySlotCodeIgnoreCase("A01").orElseThrow();
        attachOpenSession(assigned, "51H00001", now.minusMinutes(28));

        ParkingSlot overdue = slots.findBySlotCodeIgnoreCase("A02").orElseThrow();
        overdue.setBorrowedUntil(now.minusMinutes(15));
        attachOpenSession(overdue, "59X199999", now.minusHours(2));
        slots.saveAndFlush(overdue);

        attachOpenSession(slots.findBySlotCodeIgnoreCase("B01").orElseThrow(), "51H00003", now.minusMinutes(17));

        ParkingSlot blocked = slots.findBySlotCodeIgnoreCase("B02").orElseThrow();
        blocked.setStatusOverride(SlotStatusOverride.BLOCKED);
        slots.saveAndFlush(blocked);

        ParkingSlot reservedBorrowed = slots.findBySlotCodeIgnoreCase("B03").orElseThrow();
        reservedBorrowed.setBorrowedPlate("59X199996");
        reservedBorrowed.setBorrowedUntil(now.plusHours(2));
        reservedBorrowed.setBorrowNotes("Lịch hẹn synthetic chưa có xe vào");
        slots.saveAndFlush(reservedBorrowed);

        ParkingSlot occupiedBorrowed = slots.findBySlotCodeIgnoreCase("B04").orElseThrow();
        occupiedBorrowed.setBorrowedPlate("59X199997");
        occupiedBorrowed.setBorrowedUntil(now.plusHours(2));
        occupiedBorrowed.setBorrowNotes("Lượt synthetic đang đỗ nhờ");
        attachOpenSession(occupiedBorrowed, "59X199997", now.minusMinutes(8));
        slots.saveAndFlush(occupiedBorrowed);
    }

    private void seedHistoryAndShift(SystemAccount manager) {
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < 24; index++) {
            LocalDateTime entry = now.minusDays(24 - index).withSecond(0).withNano(0);
            ParkingSession session = new ParkingSession();
            session.setEntryPlate("QA-HISTORY-" + String.format("%02d", index));
            session.setExitPlate("QA-EXIT-" + String.format("%02d", index));
            session.setEntryTime(entry);
            session.setExitTime(entry.plusHours(1));
            session.setStatus(SessionStatus.COMPLETED);
            session.setDetectedVehicleType(VehicleType.CAR);
            session.setFee(BigDecimal.valueOf((index + 1) * 1000L));
            sessions.saveAndFlush(session);
        }
        String longTarget = "synthetic-audit-target-" + "x".repeat(72);
        String longReason = "Synthetic audit explanation for visual review. ".repeat(4);
        List<String> auditActions = List.of("LOGIN_FAILURE", "LOGIN_SUCCESS", "ACCOUNT_PROFILE_UPDATE",
            "ACCOUNT_PASSWORD_RESET");
        for (int index = 0; index < 26; index++) {
            String action = auditActions.get(index % auditActions.size());
            String target = index == 25 ? longTarget : "UXQA-AUDIT-" + String.format("%02d", index);
            String outcome = index % 2 == 0 ? "FAILURE" : "SUCCESS";
            String reason = index == 25 ? longReason : ("FAILURE".equals(outcome) ? "INVALID_CREDENTIALS" : null);
            auditEvents.saveAndFlush(new SecurityAuditEvent(null, "synthetic-audit-actor-" + index,
                action, "SYSTEM_ACCOUNT", target, outcome, reason, null, Instant.now().minusSeconds(26 - index)));
        }
        Instant start = Instant.now().minusSeconds(3600);
        shifts.saveAndFlush(new GateShiftAssignment(manager, "QA shift", start,
            start.plusSeconds(8 * 3600), "Synthetic browser fixture", "Local-only fixture"));
        if (Boolean.getBoolean("parking.test.login-browser.b12-fixture")) {
            shifts.saveAndFlush(new GateShiftAssignment(manager, "B12 upcoming shift", Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(5 * 3600), "Synthetic upcoming", "B12 synthetic fixture"));
            shifts.saveAndFlush(new GateShiftAssignment(manager, "B12 ended shift", Instant.now().minusSeconds(8 * 3600),
                Instant.now().minusSeconds(4 * 3600), "Synthetic ended", "B12 synthetic fixture"));
            Instant desktopCreated = Instant.now().minusSeconds(300);
            desktopSessions.saveAndFlush(new DesktopSession("b1200000-0000-4000-8000-000000000001", manager,
                desktopCreated, desktopCreated.plusSeconds(7200), "B12 synthetic desktop"));
        }
    }

    private void attachOpenSession(ParkingSlot slot, String plate, LocalDateTime entryTime) {
        ParkingSession session = new ParkingSession();
        session.setEntryPlate(plate);
        session.setEntryTime(entryTime);
        session.setStatus(SessionStatus.OPEN);
        session.setDetectedVehicleType(VehicleType.CAR);
        slot.setCurrentSession(sessions.saveAndFlush(session));
        slots.saveAndFlush(slot);
    }

    private static boolean isChild(Path path, Path root) {
        return path.startsWith(root) && !path.equals(root);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "parking.test.login-browser.b12-fixture", havingValue = "true")
    @Import(FixtureOnlyController.class)
    static class FixtureOnlyWebHooks { }

    @Controller
    @TestComponent
    static class FixtureOnlyController {
        @GetMapping(value = "/account-management", params = "fixture-temporary-secret")
        String temporarySecret(RedirectAttributes redirect) {
            redirect.addFlashAttribute("temporaryPassword", TEMP_SECRET_SENTINEL);
            return "redirect:/account-management";
        }

        @GetMapping(value = "/account/password", params = "fixture-error=current")
        String currentPasswordError(Model model) {
            model.addAttribute("error", "Mật khẩu hiện tại không chính xác.");
            return "account-password";
        }

        @GetMapping(value = "/account/password", params = "fixture-error=ambiguous")
        String ambiguousPasswordError(Model model) {
            model.addAttribute("error", "Không thể xác nhận kết quả đổi mật khẩu. Hãy thử đăng nhập lại trước khi gửi lại.");
            return "account-password";
        }

        @GetMapping(value = "/account-management", params = "fixture-upload-error")
        void uploadError() {
            throw new MaxUploadSizeExceededException(1L);
        }
    }
}
