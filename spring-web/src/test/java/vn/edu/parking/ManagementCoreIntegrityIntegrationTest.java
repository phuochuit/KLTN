package vn.edu.parking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.AiProxyService;
import vn.edu.parking.service.ManagementCoreWriteService;
import vn.edu.parking.service.ManagementCoreWriteService.*;
import vn.edu.parking.service.ResidentImageStorage;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ManagementCoreIntegrityIntegrationTest {
    @Autowired ManagementCoreWriteService writes;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired VehicleRepository vehicles;
    @Autowired ResidentImageStorage images;
    @MockitoSpyBean SecurityAuditRepository audit;
    @Autowired MockMvc mvc;
    @MockitoSpyBean ParkingSessionRepository sessions;
    @MockitoBean AiProxyService ai;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "group4a-integrity");
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile(name, "synthetic.png", "image/png", Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
    }

    private Set<String> files() throws IOException {
        try (var paths = Files.list(images.getPrivateRoot())) {
            return paths.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private Household household(String code, int quota) {
        Household h = new Household(); h.setHouseholdCode(code); h.setApartmentNumber(code);
        h.setMaxTwoWheelers(quota); h.setMaxCars(quota); return households.saveAndFlush(h);
    }

    private FamilyMember member(Household h, String name, String face) {
        FamilyMember m = new FamilyMember(); m.setHousehold(h); m.setFullName(name);
        m.setRegistrationFaceImagePath(face); return members.saveAndFlush(m);
    }

    private Vehicle vehicle(Household h, FamilyMember owner, String plate, FamilyMember... authorized) {
        Vehicle v = new Vehicle(); v.setHousehold(h); v.setRegisteredOwner(owner); v.setOwnerName(owner.getFullName());
        v.setPlateNumber(plate); v.setVehicleType(VehicleType.MOTORBIKE);
        v.setRegistrationImagePath("legacy-test-image"); v.setAuthorizedMembers(new LinkedHashSet<>(List.of(authorized)));
        return vehicles.saveAndFlush(v);
    }

    private MemberInput memberInput(FamilyMember m, Household h) {
        return new MemberInput(m.getId(), h.getId(), "updated owner", "", "", null, "", "", "", "", "", null, "", "", null, "");
    }

    @Test
    void transferMovesOwnedVehiclesAndRemovesCrossHouseholdPermissions() {
        Household from = household("TRANSFER-A", 5), to = household("TRANSFER-B", 5);
        FamilyMember owner = member(from, "owner", "existing-face"), former = member(from, "former", "existing-face");
        FamilyMember resident = member(to, "destination", "existing-face");
        Vehicle owned = vehicle(from, owner, "TESTTRANS01", owner, former, resident);
        Vehicle shared = vehicle(from, former, "TESTTRANS02", former, owner);

        writes.saveMember(memberInput(owner, to));

        assertThat(members.findById(owner.getId()).orElseThrow().getHousehold().getId()).isEqualTo(to.getId());
        Vehicle moved = vehicles.findById(owned.getId()).orElseThrow();
        assertThat(moved.getHousehold().getId()).isEqualTo(to.getId());
        assertThat(moved.getAuthorizedMembers()).extracting(FamilyMember::getId)
            .containsExactlyInAnyOrder(owner.getId(), resident.getId());
        assertThat(vehicles.findById(shared.getId()).orElseThrow().getAuthorizedMembers())
            .extracting(FamilyMember::getId).containsExactly(former.getId());
    }

    @Test
    void transferOverQuotaRollsBackMemberVehicleAndPermissions() {
        Household from = household("QUOTA-A", 3), to = household("QUOTA-B", 0);
        FamilyMember owner = member(from, "before", "existing-face"), former = member(from, "former", "existing-face");
        Vehicle owned = vehicle(from, owner, "TESTQUOTA1", owner, former);
        assertThatThrownBy(() -> writes.saveMember(memberInput(owner, to))).isInstanceOf(IllegalArgumentException.class);
        FamilyMember after = members.findById(owner.getId()).orElseThrow();
        assertThat(after.getHousehold().getId()).isEqualTo(from.getId());
        assertThat(after.getFullName()).isEqualTo("before");
        Vehicle unchanged = vehicles.findById(owned.getId()).orElseThrow();
        assertThat(unchanged.getHousehold().getId()).isEqualTo(from.getId());
        assertThat(unchanged.getAuthorizedMembers()).extracting(FamilyMember::getId)
            .containsExactlyInAnyOrder(owner.getId(), former.getId());
    }

    @Test
    void combinedRegistrationTransfersMemberAndReusesOwnedVehicleAtQuota() {
        Household from = household("COMBINED-A", 3), to = household("COMBINED-B", 1);
        FamilyMember owner = member(from, "owner", "existing-face"), former = member(from, "former", "existing-face");
        owner.setCitizenId("123123123123"); members.saveAndFlush(owner);
        Vehicle owned = vehicle(from, owner, "COMBTEST01", owner, former);
        HouseholdInput destination = new HouseholdInput("COMBINED-B", "COMBINED-B", "", "", 1, 1);
        MemberInput input = new MemberInput(null, null, "new owner", "123123123123", "", null, "", "", "", "", "", null, "", "", null, "");
        VehicleInput vehicle = new VehicleInput(null, null, null, null, "COMBTEST01", VehicleType.MOTORBIKE,
            FuelType.GASOLINE, "registration", "brand", "model", "color", null, "frame", "engine", null, null, null, null);

        Vehicle result = writes.saveRegistration(destination, input, vehicle);
        assertThat(result.getId()).isEqualTo(owned.getId());
        assertThat(members.findById(owner.getId()).orElseThrow().getHousehold().getId()).isEqualTo(to.getId());
        Vehicle moved = vehicles.findById(owned.getId()).orElseThrow();
        assertThat(moved.getHousehold().getId()).isEqualTo(to.getId());
        assertThat(moved.getAuthorizedMembers()).extracting(FamilyMember::getId).containsExactly(owner.getId());
        assertThat(vehicles.findByHouseholdIdAndActiveTrue(to.getId())).hasSize(1);
    }

    @Test
    void vehicleRejectsSelectedMemberFromAnotherHouseholdWithoutWriting() {
        Household h = household("AUTH-A", 2), other = household("AUTH-B", 2);
        FamilyMember owner = member(h, "owner", "existing-face"), outsider = member(other, "outsider", "existing-face");
        VehicleInput input = new VehicleInput(null, h.getId(), owner.getId(), List.of(outsider.getId()), "TESTAUTH01",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, null, "legacy-reference");
        assertThatThrownBy(() -> writes.saveVehicle(input)).isInstanceOf(IllegalArgumentException.class);
        assertThat(vehicles.findByPlateNumberIgnoreCase("TESTAUTH01")).isEmpty();
    }

    @Test
    void failureDuringSessionSaveRollsBackRegistrationAndBothNewImages() throws IOException {
        ParkingSession open = new ParkingSession(); open.setEntryPlate("TESTFAIL01");
        open.setEntryTime(LocalDateTime.now()); open = sessions.saveAndFlush(open);
        Long sessionId = open.getId(); Set<String> before = files();
        doThrow(new DataAccessResourceFailureException("synthetic failure"))
            .when(sessions).save(any(ParkingSession.class));
        HouseholdInput household = new HouseholdInput("FAIL-HOUSE", "FAIL-HOUSE", "", "", 2, 1);
        MemberInput member = new MemberInput(null, null, "synthetic", "123456789012", "", null, "", "", "", "", "", null, "", "", image("face"), "");
        VehicleInput vehicle = new VehicleInput(null, null, null, null, "TESTFAIL01", VehicleType.MOTORBIKE,
            FuelType.GASOLINE, "registration", "brand", "model", "color", null, "frame", "engine", null, null, image("registration"), null);

        assertThatThrownBy(() -> writes.saveRegistration(household, member, vehicle))
            .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(households.findByHouseholdCodeIgnoreCase("FAIL-HOUSE")).isEmpty();
        assertThat(members.findByCitizenId("123456789012")).isEmpty();
        assertThat(vehicles.findByPlateNumberIgnoreCase("TESTFAIL01")).isEmpty();
        assertThat(sessions.findById(sessionId).orElseThrow().getVehicle()).isNull();
        assertThat(files()).isEqualTo(before);
    }

    @Test
    void rollbackKeepsOldFaceAndRemovesItsReplacement() throws IOException {
        String oldFace = images.save(image("face"), "");
        Household h = household("FACE-ROLLBACK", 2);
        FamilyMember owner = member(h, "original", oldFace);
        owner.setCitizenId("111222333444"); members.saveAndFlush(owner);
        Set<String> before = files();
        HouseholdInput household = new HouseholdInput("FACE-ROLLBACK", "FACE-ROLLBACK", "", "", 2, 1);
        MemberInput updated = new MemberInput(null, null, "changed", "111222333444", "", null, "", "", "", "", "", null, "", "", image("face"), "");
        VehicleInput vehicle = new VehicleInput(null, null, null, null, "TESTFACE01", VehicleType.MOTORBIKE,
            FuelType.GASOLINE, "registration", "brand", "model", "color", null, "frame", "engine", null, null, null, null);

        assertThatThrownBy(() -> writes.saveRegistration(household, updated, vehicle)).isInstanceOf(IllegalArgumentException.class);
        FamilyMember unchanged = members.findById(owner.getId()).orElseThrow();
        assertThat(unchanged.getFullName()).isEqualTo("original");
        assertThat(unchanged.getRegistrationFaceImagePath()).isEqualTo(oldFace);
        assertThat(images.readStoredReference(oldFace)).isNotEmpty();
        assertThat(files()).isEqualTo(before);
        assertThat(vehicles.findByPlateNumberIgnoreCase("TESTFACE01")).isEmpty();
    }

    @Test
    void concurrentVehicleWritesCannotExceedQuotaInH2() throws Exception {
        Household h = household("CONCURRENT", 1);
        FamilyMember owner = member(h, "owner", "existing-face");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await();
                try {
                    writes.saveVehicle(new VehicleInput(null, h.getId(), owner.getId(), List.of(), "TESTRACE0" + i,
                        VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
                        null, "frame", "engine", null, null, image("registrationImage"), ""));
                    return "saved";
                } catch (IllegalArgumentException ex) { return "quota"; }
            })).toList();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("saved", "quota");
        }
        assertThat(vehicles.findByHouseholdIdAndActiveTrue(h.getId())).hasSize(1);
    }

    @Test
    void newVehicleRejectsUnverifiedClientRegistrationReference() {
        Household h = household("PATH-COMPAT", 1);
        FamilyMember owner = member(h, "owner", "existing-face");
        VehicleInput input = new VehicleInput(null, h.getId(), owner.getId(), List.of(), "TESTPATH01",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, null, "missing-synthetic-reference");
        assertThatThrownBy(() -> writes.saveVehicle(input)).isInstanceOf(IllegalArgumentException.class);
        assertThat(vehicles.findByPlateNumberIgnoreCase("TESTPATH01")).isEmpty();
        VehicleInput noImage = new VehicleInput(null, h.getId(), owner.getId(), List.of(), "TESTPATH02",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, null, "");
        assertThatThrownBy(() -> writes.saveVehicle(noImage)).isInstanceOf(IllegalArgumentException.class);
        assertThat(vehicles.findByPlateNumberIgnoreCase("TESTPATH02")).isEmpty();
    }

    @Test
    void editKeepsDatabaseImageAndRejectsAnotherVehiclesReference() throws IOException {
        Household h = household("PATH-EDIT", 3);
        FamilyMember owner = member(h, "owner", "existing-face");
        Vehicle edited = vehicle(h, owner, "PATHEXIST1", owner);
        Vehicle other = vehicle(h, owner, "PATHOTHER1", owner);
        other.setRegistrationImagePath(images.save(image("registrationImage"), "")); vehicles.saveAndFlush(other);
        VehicleInput forged = new VehicleInput(edited.getId(), h.getId(), owner.getId(), List.of(), "PATHEXIST1",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, null, other.getRegistrationImagePath());
        assertThatThrownBy(() -> writes.saveVehicle(forged)).isInstanceOf(IllegalArgumentException.class);
        assertThat(vehicles.findById(edited.getId()).orElseThrow().getRegistrationImagePath()).isEqualTo("legacy-test-image");
        VehicleInput edit = new VehicleInput(edited.getId(), h.getId(), owner.getId(), List.of(), "PATHEXIST1",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, null, "legacy-test-image");
        writes.saveVehicle(edit);
        assertThat(vehicles.findById(edited.getId()).orElseThrow().getRegistrationImagePath()).isEqualTo("legacy-test-image");
        VehicleInput noPath = new VehicleInput(edited.getId(), h.getId(), owner.getId(), List.of(), "PATHEXIST1",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, null, "");
        writes.saveVehicle(noPath);
        assertThat(vehicles.findById(edited.getId()).orElseThrow().getRegistrationImagePath()).isEqualTo("legacy-test-image");
    }

    @Test
    void validUploadedRegistrationImageIsStoredByServer() throws IOException {
        Household h = household("PATH-UPLOAD", 1);
        FamilyMember owner = member(h, "owner", "existing-face");
        Vehicle saved = writes.saveVehicle(new VehicleInput(null, h.getId(), owner.getId(), List.of(), "PATHUPLOAD1",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, image("registrationImage"), ""));
        assertThat(saved.getRegistrationImagePath()).startsWith("resident:");
        assertThat(images.readStoredReference(saved.getRegistrationImagePath())).isNotEmpty();
    }

    @Test
    void replacingRegistrationImagePreservesOldFile() throws IOException {
        Household h = household("PATH-REPLACE", 1);
        FamilyMember owner = member(h, "owner", "existing-face");
        Vehicle existing = vehicle(h, owner, "PATHREPLACE1", owner);
        String oldReference = images.save(image("registrationImage"), "");
        existing.setRegistrationImagePath(oldReference); vehicles.saveAndFlush(existing);
        Vehicle saved = writes.saveVehicle(new VehicleInput(existing.getId(), h.getId(), owner.getId(), List.of(), "PATHREPLACE1",
            VehicleType.MOTORBIKE, FuelType.GASOLINE, "registration", "brand", "model", "color",
            null, "frame", "engine", null, null, image("registrationImage"), oldReference));
        assertThat(saved.getRegistrationImagePath()).startsWith("resident:").isNotEqualTo(oldReference);
        assertThat(images.readStoredReference(saved.getRegistrationImagePath())).isNotEmpty();
        assertThat(images.readStoredReference(oldReference)).isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void postRejectsClientReferenceEvenWhenUploadIsPresent() throws Exception {
        Household h = household("PATH-HTTP", 2);
        FamilyMember owner = member(h, "owner", "existing-face");
        String otherReference = images.save(image("registrationImage"), "");
        Set<String> before = files();
        mvc.perform(multipart("/vehicles").file(image("registrationImage")).with(csrf())
                .param("householdId", h.getId().toString()).param("registeredOwnerId", owner.getId().toString())
                .param("plateNumber", "PATHHTTP01").param("vehicleType", "MOTORBIKE").param("fuelType", "GASOLINE")
                .param("registrationNumber", "registration").param("brand", "brand").param("modelName", "model")
                .param("color", "color").param("chassisNumber", "frame").param("engineNumber", "engine")
                .param("registrationImagePath", otherReference))
            .andExpect(status().isBadRequest());
        assertThat(vehicles.findByPlateNumberIgnoreCase("PATHHTTP01")).isEmpty();
        assertThat(files()).isEqualTo(before);
        assertThat(images.readStoredReference(otherReference)).isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void validationAndCsrfRejectWithoutWriting() throws Exception {
        mvc.perform(post("/households").param("householdCode", "INVALID-TEST").param("apartmentNumber", ""))
            .andExpect(status().isForbidden());
        mvc.perform(post("/households").with(csrf()).param("householdCode", "INVALID-TEST").param("apartmentNumber", ""))
            .andExpect(status().isBadRequest());
        assertThat(households.findByHouseholdCodeIgnoreCase("INVALID-TEST")).isEmpty();
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void postDatabaseFailureReturnsUnknownOutcomeAndRollsBack() throws Exception {
        Household h = household("POST-FAILURE", 2);
        FamilyMember owner = member(h, "owner", "existing-face");
        ParkingSession open = new ParkingSession(); open.setEntryPlate("POSTFAIL01");
        open.setEntryTime(LocalDateTime.now()); sessions.saveAndFlush(open);
        doThrow(new DataAccessResourceFailureException("synthetic secret diagnostic"))
            .when(sessions).save(any(ParkingSession.class));
        mvc.perform(multipart("/vehicles").file(image("registrationImage")).with(csrf()).param("householdId", h.getId().toString())
                .param("registeredOwnerId", owner.getId().toString()).param("plateNumber", "POSTFAIL01")
                .param("vehicleType", "MOTORBIKE").param("fuelType", "GASOLINE")
                .param("registrationNumber", "registration").param("brand", "brand").param("modelName", "model")
                .param("color", "color").param("chassisNumber", "frame").param("engineNumber", "engine"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string(containsString("Không xác nhận được kết quả lưu")))
            .andExpect(content().string(not(containsString("synthetic secret diagnostic"))));
        assertThat(vehicles.findByPlateNumberIgnoreCase("POSTFAIL01")).isEmpty();
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void integrityFailureReturnsConflictWithoutClaimingSuccess() throws Exception {
        Household h = household("POST-CONFLICT", 2);
        FamilyMember owner = member(h, "owner", "existing-face");
        ParkingSession open = new ParkingSession(); open.setEntryPlate("POSTCON01");
        open.setEntryTime(LocalDateTime.now()); sessions.saveAndFlush(open);
        doThrow(new DataIntegrityViolationException("synthetic internal detail"))
            .when(sessions).save(any(ParkingSession.class));
        mvc.perform(multipart("/vehicles").file(image("registrationImage")).with(csrf()).param("householdId", h.getId().toString())
                .param("registeredOwnerId", owner.getId().toString()).param("plateNumber", "POSTCON01")
                .param("vehicleType", "MOTORBIKE").param("fuelType", "GASOLINE")
                .param("registrationNumber", "registration").param("brand", "brand").param("modelName", "model")
                .param("color", "color").param("chassisNumber", "frame").param("engineNumber", "engine"))
            .andExpect(status().isConflict())
            .andExpect(content().string(not(containsString("synthetic internal detail"))));
        assertThat(vehicles.findByPlateNumberIgnoreCase("POSTCON01")).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "GATE_OPERATE")
    void unauthorizedCallerCannotMutateCore() throws Exception {
        mvc.perform(post("/households").with(csrf()).param("householdCode", "DENIED-TEST").param("apartmentNumber", "X"))
            .andExpect(status().isForbidden());
        assertThat(households.findByHouseholdCodeIgnoreCase("DENIED-TEST")).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "SECURITY_AUDIT_VIEW")
    void overrideAuditIncludesBothActionPrefixes() throws Exception {
        audit.save(new SecurityAuditEvent(null, "synthetic", "OVERRIDE_TEST", "TEST", "test", "SUCCESS", null, null, Instant.now()));
        audit.save(new SecurityAuditEvent(null, "synthetic", "MANUAL_OVERRIDE_APPROVED", "TEST", "test", "SUCCESS", null, null, Instant.now()));
        mvc.perform(get("/account-admin/audit/overrides")).andExpect(status().isOk())
            .andExpect(content().string(allOf(containsString("OVERRIDE_TEST"), containsString("MANUAL_OVERRIDE_APPROVED"))));
    }

    @Test
    @WithMockUser(authorities = "SECURITY_AUDIT_VIEW")
    void auditOutageIsNotDisplayedAsEmptyResults() throws Exception {
        doThrow(new DataAccessResourceFailureException("synthetic internal detail"))
            .when(audit).findAll(any(Specification.class), any(Pageable.class));
        mvc.perform(get("/security-audit")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Nhật ký tạm thời không khả dụng")))
            .andExpect(content().string(not(containsString("audit-table-area"))))
            .andExpect(content().string(not(containsString("synthetic internal detail"))));
    }
}
