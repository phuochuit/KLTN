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
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.PaymentTransactionRepository;
import vn.edu.parking.repository.SystemAccountRepository;
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
    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;
    @Autowired PaymentTransactionRepository paymentTransactions;
    @Autowired SystemAccountRepository systemAccounts;

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
                display.format(start) + " - " + display.format(start.plusDays(9)))));

        mvc.perform(get("/api/parking/lookup")
                .param("plate", "60A123450").param("cardCode", "60A123450"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passType").value("DAILY"))
            .andExpect(jsonPath("$.packageValid").value(true));

        String body = """
            {"plateNumber":"60A123450","cardCode":"60A123450","vehicleType":"CAR","manualOverride":false}
            """;
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warning").value(false));
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

        String body = """
            {"plateNumber":"60A123451","cardCode":"60A123451","vehicleType":"CAR","manualOverride":false}
            """;
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warning").value(true))
            .andExpect(jsonPath("$.message", containsString("chưa mua gói")));
    }

    private Vehicle vehicle(String plate, String owner) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(owner);
        vehicle.setVehicleType(VehicleType.CAR);
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
