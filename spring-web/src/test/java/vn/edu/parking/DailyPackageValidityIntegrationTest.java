package vn.edu.parking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.CardStatus;
import vn.edu.parking.domain.ParkingCard;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.domain.FamilyMember;
import vn.edu.parking.domain.Household;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.PaymentTransactionRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.HouseholdRepository;
import java.util.Set;
import vn.edu.parking.repository.VehicleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DailyPackageValidityIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedProperties(org.springframework.test.context.DynamicPropertyRegistry registry) throws java.io.IOException {
        LoginTestIsolation.register(registry, "daily-package-validity");
    }

    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;
    @Autowired PaymentTransactionRepository paymentTransactions;
    @Autowired SystemAccountRepository systemAccounts;
    @Autowired FamilyMemberRepository members;
    @Autowired HouseholdRepository households;

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void chargesAndDisplaysConcreteValidityForTenDayPackage() throws Exception {
        seedCollectorAccount();
        Vehicle vehicle = vehicle("60A123450", "Người dùng gói 10 ngày");
        LocalDate start = LocalDate.now();
        long paymentsBefore = paymentTransactions.count();

        mvc.perform(post("/cards").with(csrf())
                .param("vehicleId", vehicle.getId().toString())
                .param("status", "ACTIVE")
                .param("passType", "DAILY")
                .param("startDate", start.toString())
                .param("durationDays", "10")
                .param("paymentMethod", "CASH"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cards"));

        ParkingCard card = cards.findByVehicleId(vehicle.getId()).orElseThrow();
        assertThat(card.getValidFrom()).isEqualTo(start);
        assertThat(card.getValidUntil()).isEqualTo(start.plusDays(9));
        assertThat(card.getPassType()).isEqualTo(PassType.DAILY);
        assertThat(card.getSubscriptionFee()).isEqualByComparingTo("1000000");
        assertThat(paymentTransactions.count()).isEqualTo(paymentsBefore + 1);

        DateTimeFormatter display = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        mvc.perform(get("/cards"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                display.format(start) + " – " + display.format(start.plusDays(9)))));

        mvc.perform(get("/api/parking/lookup")
                .param("plate", "60A123450").param("cardCode", "60A123450"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passType").value("DAILY"))
            .andExpect(jsonPath("$.packageValid").value(true));

        long memberId = vehicle.getRegisteredOwner().getId();
        String body = "{\"plateNumber\":\"60A123450\",\"cardCode\":\"60A123450\","
            + "\"vehicleType\":\"CAR\",\"manualOverride\":false,\"familyMemberId\":"
            + memberId + ",\"faceVerified\":true}";
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warning").value(false));
        // Quyền lợi được chốt lúc vào: gia hạn/hết hạn hoặc khóa card sau đó không đổi lượt đang mở.
        card.setValidUntil(start.minusDays(1));
        card.setStatus(CardStatus.BLOCKED);
        cards.saveAndFlush(card);
        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fee").value(0));
    }

    @Test
    void legacyPerVisitCardIsTreatedAsResidentWithoutPackage() throws Exception {
        Vehicle vehicle = vehicle("60A123451", "Người dùng vé hết hạn");
        ParkingCard card = new ParkingCard();
        card.setCardCode(vehicle.getPlateNumber());
        card.setVehicle(vehicle);
        card.setStatus(CardStatus.ACTIVE);
        card.setPassType(PassType.PER_VISIT);
        card.setValidFrom(LocalDate.now().minusDays(10));
        card.setValidUntil(LocalDate.now().minusDays(1));
        cards.save(card);

        String body = "{\"plateNumber\":\"60A123451\",\"cardCode\":\"60A123451\","
            + "\"vehicleType\":\"CAR\",\"manualOverride\":false,\"familyMemberId\":"
            + vehicle.getRegisteredOwner().getId() + ",\"faceVerified\":true}";
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warning").value(true))
            .andExpect(jsonPath("$.message", containsString("chưa mua gói")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void monthlyPackageAlwaysUsesThirtyInclusiveDays() throws Exception {
        seedCollectorAccount();
        Vehicle vehicle = vehicle("60A123452", "Người dùng gói tháng");
        LocalDate start = LocalDate.now();

        mvc.perform(post("/cards").with(csrf())
                .param("vehicleId", vehicle.getId().toString())
                .param("status", "ACTIVE")
                .param("passType", "MONTHLY")
                .param("startDate", start.toString())
                .param("durationDays", "5")
                .param("paymentMethod", "CASH"))
            .andExpect(status().is3xxRedirection());

        ParkingCard card = cards.findByVehicleId(vehicle.getId()).orElseThrow();
        assertThat(card.getValidFrom()).isEqualTo(start);
        assertThat(card.getValidUntil()).isEqualTo(start.plusDays(29));
        assertThat(card.getSubscriptionFee()).isEqualByComparingTo("1500000");
    }

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void packageActivationRejectsNonActiveAndLegacyPerVisitChoices() throws Exception {
        Vehicle vehicle = vehicle("60A123453", "Người dùng gói");
        for (String[] invalid : new String[][]{{"BLOCKED", "DAILY"}, {"ACTIVE", "PER_VISIT"}}) {
            mvc.perform(post("/cards").with(csrf())
                    .param("vehicleId", vehicle.getId().toString())
                    .param("status", invalid[0]).param("passType", invalid[1])
                    .param("durationDays", "1").param("paymentMethod", "CASH"))
                .andExpect(status().isBadRequest());
        }
        assertThat(cards.findByVehicleId(vehicle.getId())).isEmpty();
    }

    private Vehicle vehicle(String plate, String owner) {
        Household household = new Household();
        household.setHouseholdCode("HH-" + plate);
        household.setApartmentNumber("TEST-" + plate);
        household = households.save(household);
        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName(owner);
        member.setCitizenId("CID-" + plate);
        member.setRegistrationFaceImagePath("resident:test-" + plate + ".jpg");
        member.setActive(true);
        member = members.save(member);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(owner);
        vehicle.setVehicleType(VehicleType.CAR);
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(member);
        vehicle.setAuthorizedMembers(Set.of(member));
        return vehicles.save(vehicle);
    }

    private void seedCollectorAccount() {
        if (systemAccounts.findByUsername("admin").isPresent()) return;
        SystemAccount account = new SystemAccount("admin", "Test Administrator",
            "test-password-hash", AccountRole.MANAGEMENT, false);
        account.setEnabled(true);
        systemAccounts.save(account);
    }
}
