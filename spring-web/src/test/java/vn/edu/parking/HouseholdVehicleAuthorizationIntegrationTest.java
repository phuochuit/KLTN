package vn.edu.parking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.CardStatus;
import vn.edu.parking.domain.FamilyMember;
import vn.edu.parking.domain.Household;
import vn.edu.parking.domain.ParkingCard;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.VehicleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:household-vehicle-authorization;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.evidence.required=false"
})
@WithMockUser(username = "admin", roles = "MANAGEMENT")
class HouseholdVehicleAuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;
    @Autowired ParkingSessionRepository sessions;

    @Test
    void bothHouseholdMembersCanDriveAndCollectEitherFamilyVehicle() throws Exception {
        Household household = new Household();
        household.setHouseholdCode("HH-TWO-PEOPLE-TWO-VEHICLES");
        household.setApartmentNumber("A2-0202");
        household = households.save(household);

        FamilyMember personA = member(household, "Nguyễn Văn A", "079200000001", "a.jpg");
        FamilyMember personB = member(household, "Nguyễn Thị B", "079200000002", "b.jpg");
        Set<FamilyMember> bothMembers = new LinkedHashSet<>(Set.of(personA, personB));

        Vehicle hondaCar = vehicle(household, personA, bothMembers, "51H12345",
            VehicleType.CAR, "Honda", "City", 4);
        Vehicle hondaWave = vehicle(household, personA, bothMembers, "59A123456",
            VehicleType.MOTORBIKE, "Honda", "Wave", null);
        monthlyCard(hondaCar);
        monthlyCard(hondaWave);

        completeVisit(hondaCar, personA, personB);
        completeVisit(hondaCar, personB, personA);
        completeVisit(hondaWave, personA, personB);
        completeVisit(hondaWave, personB, personA);

        assertThat(vehicles.findById(hondaCar.getId()).orElseThrow().getRegisteredOwner().getId())
            .isEqualTo(personA.getId());
        assertThat(vehicles.findById(hondaWave.getId()).orElseThrow().getRegisteredOwner().getId())
            .isEqualTo(personA.getId());
    }

    private void completeVisit(Vehicle vehicle, FamilyMember driver, FamilyMember collector)
            throws Exception {
        String entry = request(vehicle, driver);
        String exit = request(vehicle, collector);

        mvc.perform(post("/api/parking/entry").contentType("application/json").content(entry))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(exit))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PREVIEW"))
            .andExpect(jsonPath("$.fee").value(0));
        mvc.perform(post("/api/parking/exit-confirm").contentType("application/json").content(exit))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.fee").value(0));

        ParkingSession completed = sessions
            .findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(
                vehicle.getPlateNumber(), SessionStatus.COMPLETED)
            .orElseThrow();
        assertThat(completed.getEntryMember().getId()).isEqualTo(driver.getId());
        assertThat(completed.getExitMember().getId()).isEqualTo(collector.getId());
        assertThat(completed.getOpenPlate()).isNull();
    }

    private FamilyMember member(Household household, String name, String citizenId, String face) {
        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName(name);
        member.setCitizenId(citizenId);
        member.setRegistrationFaceImagePath("resident:" + face);
        member.setActive(true);
        return members.save(member);
    }

    private Vehicle vehicle(Household household, FamilyMember owner,
            Set<FamilyMember> authorizedMembers, String plate, VehicleType type,
            String brand, String model, Integer seatCount) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(owner);
        vehicle.setAuthorizedMembers(new LinkedHashSet<>(authorizedMembers));
        vehicle.setPlateNumber(plate);
        vehicle.setOwnerName(owner.getFullName());
        vehicle.setApartmentNumber(household.getApartmentNumber());
        vehicle.setVehicleType(type);
        vehicle.setBrand(brand);
        vehicle.setModelName(model);
        vehicle.setSeatCount(seatCount);
        return vehicles.save(vehicle);
    }

    private void monthlyCard(Vehicle vehicle) {
        ParkingCard card = new ParkingCard();
        card.setCardCode(vehicle.getPlateNumber());
        card.setVehicle(vehicle);
        card.setStatus(CardStatus.ACTIVE);
        card.setPassType(PassType.MONTHLY);
        card.setValidFrom(LocalDate.now());
        card.setValidUntil(LocalDate.now().plusDays(29));
        card.setSubscriptionFee(BigDecimal.valueOf(500_000));
        cards.save(card);
    }

    private static String request(Vehicle vehicle, FamilyMember person) {
        return "{\"plateNumber\":\"" + vehicle.getPlateNumber()
            + "\",\"cardCode\":\"" + vehicle.getPlateNumber()
            + "\",\"vehicleType\":\"" + vehicle.getVehicleType().name()
            + "\",\"manualOverride\":false,\"familyMemberId\":" + person.getId()
            + ",\"faceVerified\":true}";
    }
}
