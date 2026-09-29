package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.ParkingSlot;
import java.util.List;
import java.util.Optional;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    List<ParkingSlot> findAllByOrderBySlotCodeAsc();
    List<ParkingSlot> findByActiveTrueOrderBySlotCodeAsc();
    Optional<ParkingSlot> findBySlotCodeIgnoreCase(String slotCode);
    Optional<ParkingSlot> findFirstByAssignedVehicleIdAndCurrentSessionIsNullAndActiveTrue(Long vehicleId);
    Optional<ParkingSlot> findFirstByCurrentSessionId(Long sessionId);
    Optional<ParkingSlot> findFirstByCurrentSessionIsNullAndActiveTrueOrderBySlotCodeAsc();
}
