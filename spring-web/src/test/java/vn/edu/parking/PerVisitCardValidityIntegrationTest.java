package vn.edu.parking;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.CardStatus;
import vn.edu.parking.domain.ParkingCard;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.ParkingCardRepository;
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
class PerVisitCardValidityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void savesAndDisplaysConcreteValidityForPerVisitCard() throws Exception {
        Vehicle vehicle = vehicle("60A123450", "Người dùng vé 10 ngày");
        LocalDate start = LocalDate.of(2026, 10, 28);

        mvc.perform(post("/cards").with(csrf())
                .param("vehicleId", vehicle.getId().toString())
                .param("status", "ACTIVE")
                .param("passType", "PER_VISIT")
                .param("startDate", start.toString())
                .param("durationDays", "10"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cards"));

        ParkingCard card = cards.findByVehicleId(vehicle.getId()).orElseThrow();
        assertThat(card.getValidFrom()).isEqualTo(start);
        assertThat(card.getValidUntil()).isEqualTo(start.plusDays(9));

        mvc.perform(get("/cards"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("28/10/2026 - 06/11/2026")));
    }

    @Test
    void expiredPerVisitCardWarnsButStillCreatesEntryAtPerVisitPricing() throws Exception {
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
            .andExpect(jsonPath("$.message", containsString("Vé lượt đã hết hạn")));
    }

    private Vehicle vehicle(String plate, String owner) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(owner);
        vehicle.setVehicleType(VehicleType.CAR);
        return vehicles.save(vehicle);
    }
}
