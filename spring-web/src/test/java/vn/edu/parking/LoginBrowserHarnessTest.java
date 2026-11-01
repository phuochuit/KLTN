package vn.edu.parking;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.FamilyMember;
import vn.edu.parking.domain.FuelType;
import vn.edu.parking.domain.Household;
import vn.edu.parking.domain.GateShiftAssignment;
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
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.service.AiProxyService;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in synthetic browser fixture, not a live ANPR/production E2E test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "parking.test.login-browser.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginBrowserHarnessTest {
    static final String MANAGER_USERNAME = "lumipark-browser-manager";
    static final String FORCED_CHANGE_USERNAME = "lumipark-browser-change";
    static final String THROTTLE_USERNAME = "lumipark-browser-throttle";
    private static Path runDirectory;

    @LocalServerPort int port;
    @Autowired SystemAccountRepository accounts;
    @Autowired ParkingSessionRepository sessions;
    @Autowired ParkingSlotRepository slots;
    @Autowired SecurityAuditRepository auditEvents;
    @Autowired SecurityRoleRepository roles;
    @Autowired GateShiftAssignmentRepository shifts;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired VehicleRepository vehicles;
    @Autowired PasswordEncoder passwordEncoder;
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
        seedManagementRecords();
        seedParkingOperations();
        seedHistoryAndShift(manager);
        Files.writeString(runDirectory.resolve("server.port"), Integer.toString(port), StandardCharsets.US_ASCII,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        long deadline = System.nanoTime() + Duration.ofMinutes(15).toNanos();
        while (!Files.exists(runDirectory.resolve("stop")) && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
    }

    private void seedManagementRecords() {
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
}
