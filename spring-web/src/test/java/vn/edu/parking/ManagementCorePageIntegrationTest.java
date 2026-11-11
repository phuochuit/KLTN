package vn.edu.parking;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.parking.service.AiProxyService;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.domain.Household;
import vn.edu.parking.domain.FamilyMember;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.VehicleRepository;
import java.time.Instant;
import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ManagementCorePageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityAuditRepository audit;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired VehicleRepository vehicles;
    @MockitoBean AiProxyService ai;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "management-core");
    }

    @Test
    @WithMockUser(authorities = "SLOT_MANAGE")
    void settingsRespectsHouseholdLinkPermissionAndRequiresCsrf() throws Exception {
        mvc.perform(get("/settings")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Hạn mức mặc định")))
            .andExpect(content().string(not(containsString("Đi đến hộ gia đình"))));
        mvc.perform(post("/settings").param("defaultMaxTwoWheelers", "1")
                .param("defaultMaxCars", "0"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void settingsAllowsSaveWithCsrf() throws Exception {
        String html = mvc.perform(get("/settings")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("id=\"resetLimitButton\""), "the loaded baseline can be restored locally");
        assertTrue(html.contains("id=\"limitSaved\""), "the saved baseline is visible");
        assertTrue(html.contains("không xóa hoặc vô hiệu hóa xe đã có"), "lowering a cap is non-retroactive");
        mvc.perform(post("/settings").with(csrf()).param("defaultMaxTwoWheelers", "1")
                .param("defaultMaxCars", "0"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PASSWORD_CHANGE_REQUIRED")
    void forcedPasswordPageHasNoManagementNavigationOrPasswordValues() throws Exception {
        mvc.perform(get("/account/password")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Mật khẩu mới cần có ít nhất 12 ký tự.")))
            .andExpect(content().string(not(containsString("UTF-16"))))
            .andExpect(content().string(not(containsString("UTF-8"))))
            .andExpect(content().string(not(containsString("password-step"))))
            .andExpect(content().string(containsString("Đăng xuất")))
            .andExpect(content().string(containsString("data-password-target=\"currentPassword\"")))
            .andExpect(content().string(containsString("autocomplete=\"current-password\"")))
            .andExpect(content().string(not(containsString("dashboard-navigation"))))
            .andExpect(content().string(not(containsString("value=\"LumiPark-test-only"))));
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void voluntaryPasswordPageHasIndividualPasswordControlsWithoutForcedLogoutAction() throws Exception {
        String html = mvc.perform(get("/account/password")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-password-target=\"newPassword\""));
        assertTrue(html.contains("data-password-target=\"confirmPassword\""));
        assertTrue(html.contains("Mật khẩu mới cần có ít nhất 12 ký tự."));
        assertFalse(html.contains("UTF-16"));
        assertFalse(html.contains("UTF-8"));
        assertFalse(html.contains("password-step"));
        assertTrue(html.contains("action=\"/account/password\""));
        assertFalse(html.contains("action=\"/logout\""));
    }

    @Test
    @WithMockUser(authorities = "SECURITY_AUDIT_VIEW")
    void auditFiltersRealEventsAndRejectsReversedDates() throws Exception {
        audit.save(new SecurityAuditEvent(null, "synthetic-auditor", "QA_SEARCH_EVENT", "TEST", "synthetic", "SUCCESS", null, null, Instant.parse("2026-01-02T00:00:00Z")));
        mvc.perform(get("/security-audit").param("actor", "synthetic-auditor").param("from", "2026-01-02").param("to", "2026-01-02"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("QA_SEARCH_EVENT")));
        mvc.perform(get("/security-audit").param("from", "2026-01-03").param("to", "2026-01-02"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Bộ lọc không hợp lệ")))
            .andExpect(content().string(not(containsString("QA_SEARCH_EVENT"))));
    }

    @Test
    @WithMockUser(authorities = "SECURITY_AUDIT_VIEW")
    void auditPaginationClampsOutOfRangePageAndTreatsLikeMetacharactersLiterally() throws Exception {
        Instant first = Instant.parse("2026-03-01T00:00:00Z");
        for (int index = 0; index < 26; index++) {
            audit.save(new SecurityAuditEvent(null, "synthetic-auditor", "G4A_PAGE_%", "TEST",
                "synthetic-" + index, "SUCCESS", null, null, first.plus(Duration.ofMinutes(index))));
        }
        audit.save(new SecurityAuditEvent(null, "synthetic-auditor", "G4A_PAGEXA", "TEST",
            "wildcard-neighbor", "SUCCESS", null, null, first.plus(Duration.ofDays(1))));

        String page = mvc.perform(get("/security-audit").param("action", "G4A_PAGE_%").param("page", "99"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Trang 2 / 2"), "out-of-range page is clamped to the final available page");
        assertTrue(page.contains("G4A_PAGE_%"), "literal percent search retains matching action");
        assertFalse(page.contains("G4A_PAGEXA"), "percent search does not treat the wildcard neighbor as a match");
        assertFalse(page.contains("Trang 100 /"), "pagination label does not contradict available results");
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void managementListsRenderExistingFormsAndCsrf() throws Exception {
        mvc.perform(get("/households")).andExpect(status().isOk())
            .andExpect(content().string(containsString("householdRows")))
            .andExpect(content().string(containsString("memberForm")))
            .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/vehicles")).andExpect(status().isOk())
            .andExpect(content().string(containsString("vehicleRows")))
            .andExpect(content().string(containsString("vehicleForm")))
            .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void householdAndResidentBadgesAlwaysExposeFullTotalsOnBothTabs() throws Exception {
        long householdTotal = households.count();
        long memberTotal = members.count();

        mvc.perform(get("/households").param("mode", "households"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("householdTotal", householdTotal))
            .andExpect(model().attribute("memberTotal", memberTotal));

        mvc.perform(get("/households").param("mode", "residents"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("householdTotal", householdTotal))
            .andExpect(model().attribute("memberTotal", memberTotal));
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void householdSearchTreatsLikeMetacharactersLiterallyAndPaginatesOnServer() throws Exception {
        for (int index = 0; index <= 10; index++) {
            Household household = new Household();
            household.setHouseholdCode("G4A-PCT%" + String.format("%02d", index));
            household.setApartmentNumber("Synthetic " + index);
            households.saveAndFlush(household);
        }
        Household wildcardNeighbor = new Household();
        wildcardNeighbor.setHouseholdCode("G4A-PCTX00");
        wildcardNeighbor.setApartmentNumber("Synthetic neighbor");
        households.saveAndFlush(wildcardNeighbor);

        String firstPage = mvc.perform(get("/households").param("mode", "households").param("q", "G4A-PCT%")
                .param("page", "0"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(firstPage.contains("G4A-PCT%00"), "first search page includes its first matching household");
        assertFalse(firstPage.contains("G4A-PCT%10"), "first search page excludes records on the next page");
        assertFalse(firstPage.contains("G4A-PCTX00"), "percent in the search is treated literally");

        String secondPage = mvc.perform(get("/households").param("mode", "households").param("q", "G4A-PCT%")
                .param("page", "1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(secondPage.contains("G4A-PCT%10"), "second search page includes the remaining match");
        assertFalse(secondPage.contains("G4A-PCT%00"), "second search page excludes the first-page records");
        assertFalse(secondPage.contains("G4A-PCTX00"), "percent in the search is treated literally");
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void residentSearchPaginatesLiterallyAndProfileOmitsQrAndPrivateImageData() throws Exception {
        Household household = new Household();
        household.setHouseholdCode("G4A-RESIDENT-TEST");
        household.setApartmentNumber("Synthetic resident block");
        household = households.saveAndFlush(household);
        Long selectedMemberId = null;
        for (int index = 0; index <= 10; index++) {
            FamilyMember member = new FamilyMember();
            member.setHousehold(household);
            member.setFullName("G4A-RES%" + String.format("%02d", index));
            member.setCitizenId(String.format("%012d", 900000000000L + index));
            member.setCccdQrRaw("SYNTHETIC-QR-G4A-NOT-FOR-URL");
            member.setRegistrationFaceImagePath("private/synthetic-face-reference.jpg");
            member = members.saveAndFlush(member);
            if (index == 0) selectedMemberId = member.getId();
        }
        FamilyMember wildcardNeighbor = new FamilyMember();
        wildcardNeighbor.setHousehold(household);
        wildcardNeighbor.setFullName("G4A-RESX00");
        members.saveAndFlush(wildcardNeighbor);

        String firstPage = mvc.perform(get("/households").param("mode", "residents")
                .param("q", "G4A-RES%").param("page", "0"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(firstPage.contains("G4A-RES%00"), "first resident page includes its first match");
        assertFalse(firstPage.contains("G4A-RES%10"), "first resident page excludes later records");
        assertFalse(firstPage.contains("G4A-RESX00"), "resident percent search is literal");
        assertFalse(firstPage.contains("SYNTHETIC-QR-G4A-NOT-FOR-URL"), "QR payload is not embedded in list HTML");
        assertFalse(firstPage.contains("900000000000"), "citizen ID is not embedded in list HTML");

        String secondPage = mvc.perform(get("/households").param("mode", "residents")
                .param("q", "G4A-RES%").param("page", "1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(secondPage.contains("G4A-RES%10"), "second resident page includes the remaining match");
        assertFalse(secondPage.contains("G4A-RES%00"), "second resident page excludes first-page records");
        assertFalse(secondPage.contains("G4A-RESX00"), "resident percent search is literal");

        String profile = mvc.perform(get("/households").param("mode", "residents")
                .param("memberId", selectedMemberId.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(profile.contains("HỒ SƠ CƯ DÂN"), "selected resident opens a read-only profile");
        assertFalse(profile.contains("SYNTHETIC-QR-G4A-NOT-FOR-URL"), "QR payload is not rendered in the profile");
        assertFalse(profile.contains("/face-image"), "stored private face image is not fetched by the profile");
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void householdProfileShowsActiveVehicleUsageAgainstExplicitZeroQuota() throws Exception {
        Household household = new Household();
        household.setHouseholdCode("G4A-QUOTA-PROFILE");
        household.setApartmentNumber("Synthetic quota test");
        household.setMaxTwoWheelers(0);
        household.setMaxCars(3);
        household = households.saveAndFlush(household);

        Vehicle activeMotorbike = new Vehicle();
        activeMotorbike.setHousehold(household);
        activeMotorbike.setPlateNumber("G4AQUOTA01");
        activeMotorbike.setOwnerName("Synthetic resident");
        activeMotorbike.setVehicleType(VehicleType.MOTORBIKE);
        vehicles.saveAndFlush(activeMotorbike);

        Vehicle inactiveCar = new Vehicle();
        inactiveCar.setHousehold(household);
        inactiveCar.setPlateNumber("G4AQUOTA02");
        inactiveCar.setOwnerName("Synthetic resident");
        inactiveCar.setVehicleType(VehicleType.CAR);
        inactiveCar.setActive(false);
        vehicles.saveAndFlush(inactiveCar);

        String profile = mvc.perform(get("/households").param("mode", "households")
                .param("householdId", household.getId().toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(profile.contains("Xe đang hoạt động / hạn mức"));
        assertTrue(profile.contains("1 / 0 xe hai bánh"), "active motorbikes remain visible against a zero cap");
        assertTrue(profile.contains("0 / 3 ô tô"), "inactive cars do not count toward active usage");
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void vehicleSearchTreatsLikeMetacharactersLiterallyAndPaginatesOnServer() throws Exception {
        Household household = new Household();
        household.setHouseholdCode("G4A-VEHICLE-SEARCH");
        household.setApartmentNumber("Synthetic vehicle search");
        household = households.saveAndFlush(household);
        for (int index = 0; index <= 10; index++) {
            Vehicle vehicle = new Vehicle();
            vehicle.setHousehold(household);
            vehicle.setPlateNumber("G4A-PCT%" + String.format("%02d", index));
            vehicle.setOwnerName("Synthetic vehicle owner");
            vehicles.saveAndFlush(vehicle);
        }
        Vehicle wildcardNeighbor = new Vehicle();
        wildcardNeighbor.setHousehold(household);
        wildcardNeighbor.setPlateNumber("G4A-PCTX00");
        wildcardNeighbor.setOwnerName("Synthetic vehicle neighbor");
        vehicles.saveAndFlush(wildcardNeighbor);

        String firstPage = mvc.perform(get("/vehicles").param("q", "G4A-PCT%").param("page", "0"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(firstPage.contains("G4A-PCT%00"), "first page includes its first literal match");
        assertFalse(firstPage.contains("G4A-PCT%10"), "first page excludes later matches");
        assertFalse(firstPage.contains("G4A-PCTX00"), "percent is matched literally");

        String secondPage = mvc.perform(get("/vehicles").param("q", "G4A-PCT%").param("page", "1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(secondPage.contains("G4A-PCT%10"), "second page includes the remaining match");
        assertFalse(secondPage.contains("G4A-PCT%00"), "second page excludes the first-page records");
        assertFalse(secondPage.contains("G4A-PCTX00"), "percent is matched literally");
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void vehicleProfileShowsActiveQuotaUsageExcludingTheSelectedVehicleWithoutExposingStoredImagePath()
            throws Exception {
        Household household = new Household();
        household.setHouseholdCode("G4A-VEHICLE-QUOTA");
        household.setApartmentNumber("Synthetic vehicle quota");
        household.setMaxTwoWheelers(2);
        household.setMaxCars(0);
        household = households.saveAndFlush(household);

        Vehicle selected = new Vehicle();
        selected.setHousehold(household);
        selected.setPlateNumber("G4AVEHQUOTA1");
        selected.setOwnerName("Synthetic vehicle owner");
        selected.setVehicleType(VehicleType.MOTORBIKE);
        selected.setRegistrationImagePath("private/synthetic-registration-reference.jpg");
        selected = vehicles.saveAndFlush(selected);

        Vehicle otherActiveMotorbike = new Vehicle();
        otherActiveMotorbike.setHousehold(household);
        otherActiveMotorbike.setPlateNumber("G4AVEHQUOTA2");
        otherActiveMotorbike.setOwnerName("Synthetic vehicle owner");
        otherActiveMotorbike.setVehicleType(VehicleType.MOTORBIKE);
        vehicles.saveAndFlush(otherActiveMotorbike);

        Vehicle inactiveMotorbike = new Vehicle();
        inactiveMotorbike.setHousehold(household);
        inactiveMotorbike.setPlateNumber("G4AVEHQUOTA3");
        inactiveMotorbike.setOwnerName("Synthetic vehicle owner");
        inactiveMotorbike.setVehicleType(VehicleType.MOTORBIKE);
        inactiveMotorbike.setActive(false);
        vehicles.saveAndFlush(inactiveMotorbike);

        String profile = mvc.perform(get("/vehicles").param("vehicleId", selected.getId().toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(profile.contains("HỒ SƠ PHƯƠNG TIỆN"), "selected vehicle opens a read-only profile");
        assertTrue(profile.contains("1 / 2 xe hai bánh"),
            "active quota usage excludes the selected vehicle and inactive vehicles");
        assertTrue(profile.contains("Có ảnh giấy đăng ký đã lưu"), "stored image state is visible");
        assertFalse(profile.contains("private/synthetic-registration-reference.jpg"),
            "private storage references are not rendered");

        mvc.perform(get("/admin-data/vehicles/" + selected.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationImageAvailable").value(true))
            .andExpect(jsonPath("$.registrationImagePath").doesNotExist());

        mvc.perform(get("/admin-data/vehicles/households/" + household.getId() + "/quota-preview")
                .param("vehicleType", "MOTORBIKE").param("vehicleId", selected.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeCount").value(1))
            .andExpect(jsonPath("$.limit").value(2))
            .andExpect(jsonPath("$.householdSpecific").value(true));
    }

    @Test
    @WithMockUser(authorities = "GATE_OPERATE")
    void unrelatedAuthorityCannotAccessManagementPagesOrMutateHouseholds() throws Exception {
        mvc.perform(get("/households")).andExpect(status().isForbidden());
        mvc.perform(get("/vehicles")).andExpect(status().isForbidden());
        mvc.perform(get("/security-audit")).andExpect(status().isForbidden());
        mvc.perform(get("/admin-data/vehicles/households/1/quota-preview").param("vehicleType", "CAR"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/households").with(csrf()).param("householdCode", "QA")
                .param("apartmentNumber", "QA"))
            .andExpect(status().isForbidden());
    }
}
