package vn.edu.parking;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.service.ParkingService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:slot-vehicle-type-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.evidence.required=false"
})
@WithMockUser(username = "slot-manager", roles = "MANAGEMENT")
class ParkingSlotVehicleTypeIntegrationTest {
    @Autowired ParkingService parkingService;
    @Autowired ParkingSlotRepository slots;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingSessionRepository sessions;

    @Test
    void rejectsAssigningMotorbikeToCarSlot() {
        var carSlot = slots.findBySlotCodeIgnoreCase("A02").orElseThrow();
        var motorbike = vehicles.findByPlateNumberIgnoreCase("59A112345").orElseThrow();

        assertThatThrownBy(() -> parkingService.assignVehicleToSlot(carSlot.getId(), motorbike.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Ô A02 chỉ dành cho ô tô.");
    }

    @Test
    void rejectsKnownMotorbikeBorrowingCarSlot() {
        var carSlot = slots.findBySlotCodeIgnoreCase("A03").orElseThrow();

        assertThatThrownBy(() -> parkingService.setupBorrowing(
                carSlot.getId(), "59A112345", 2, "Kiểm thử sai loại"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Ô A03 chỉ dành cho ô tô.");
    }

    @Test
    void rejectsDispatchingMotorbikeSessionToCarSlot() {
        var carSlot = slots.findBySlotCodeIgnoreCase("A04").orElseThrow();
        ParkingSession session = new ParkingSession();
        session.setEntryPlate("59Z999999");
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.now());
        session.setStatus(SessionStatus.OPEN);
        session = sessions.save(session);
        Long sessionId = session.getId();

        assertThatThrownBy(() -> parkingService.dispatchSessionToSlot(carSlot.getId(), sessionId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Ô A04 chỉ dành cho ô tô.");
    }
}
