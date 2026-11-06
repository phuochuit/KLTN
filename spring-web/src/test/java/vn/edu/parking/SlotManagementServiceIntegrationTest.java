package vn.edu.parking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.ParkingSlot;
import vn.edu.parking.domain.SlotType;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.service.SlotManagementService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:slot-management-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.evidence.required=false"
})
class SlotManagementServiceIntegrationTest {
    @Autowired SlotManagementService service;
    @Autowired ParkingSlotRepository slots;
    @Autowired VehicleRepository vehicles;

    @Test
    void updatesOneSlotIncludingItsCode() {
        ParkingSlot slot = createSlot("MG101");

        int updated = service.updateSlots(List.of(slot.getId()), "MG109", null, null,
            "VISITOR_FLEXIBLE", null);

        ParkingSlot saved = slots.findById(slot.getId()).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(saved.getSlotCode()).isEqualTo("MG109");
        assertThat(saved.getSlotType()).isEqualTo(SlotType.VISITOR_FLEXIBLE);
    }

    @Test
    void updatesMultipleSlotsWithoutChangingCodes() {
        ParkingSlot first = createSlot("MG201");
        ParkingSlot second = createSlot("MG202");

        int updated = service.updateSlots(List.of(first.getId(), second.getId()), null, null, null,
            "VISITOR_FLEXIBLE", null);

        assertThat(updated).isEqualTo(2);
        assertThat(slots.findById(first.getId()).orElseThrow().getSlotType()).isEqualTo(SlotType.VISITOR_FLEXIBLE);
        assertThat(slots.findById(second.getId()).orElseThrow().getSlotType()).isEqualTo(SlotType.VISITOR_FLEXIBLE);
    }

    @Test
    void deactivatesMultipleFreeSlotsAndKeepsRecords() {
        ParkingSlot first = createSlot("MG301");
        ParkingSlot second = createSlot("MG302");

        int deactivated = service.deactivateSlots(List.of(first.getId(), second.getId()));

        assertThat(deactivated).isEqualTo(2);
        assertThat(slots.findById(first.getId()).orElseThrow().isActive()).isFalse();
        assertThat(slots.findById(first.getId()).orElseThrow().getDeactivatedReason()).isEqualTo("ADMIN_DEACTIVATED");
        assertThat(slots.findById(second.getId())).isPresent();
    }

    @Test
    void rejectsWholeDeactivationWhenOneSlotIsAssigned() {
        ParkingSlot free = createSlot("MG401");
        ParkingSlot assigned = createSlot("MG402");
        assigned.setAssignedVehicle(vehicles.findByPlateNumberIgnoreCase("51H88888").orElseThrow());
        slots.save(assigned);

        assertThatThrownBy(() -> service.deactivateSlots(List.of(free.getId(), assigned.getId())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MG402")
            .hasMessageContaining("đã cấp cho cư dân");
        assertThat(slots.findById(free.getId()).orElseThrow().isActive()).isTrue();
    }

    private ParkingSlot createSlot(String code) {
        ParkingSlot slot = new ParkingSlot();
        slot.setSlotCode(code);
        slot.setFloor("Tầng kiểm thử");
        slot.setZoneName("Khu kiểm thử");
        slot.setSlotType(SlotType.RESIDENT_RESERVED);
        slot.setActive(true);
        return slots.save(slot);
    }
}
