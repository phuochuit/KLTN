package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.edu.parking.domain.ParkingSlot;
import vn.edu.parking.domain.VehicleType;

import java.util.List;
import java.util.Optional;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    List<ParkingSlot> findAllByOrderBySlotCodeAsc();
    List<ParkingSlot> findByActiveTrueOrderBySlotCodeAsc();
    Optional<ParkingSlot> findBySlotCodeIgnoreCase(String slotCode);
    boolean existsBySlotCodeIgnoreCase(String slotCode);

    Optional<ParkingSlot> findFirstByAssignedVehicleIdAndActiveTrue(Long vehicleId);
    Optional<ParkingSlot> findFirstByAssignedVehicleIdAndCurrentSessionIsNullAndActiveTrue(Long vehicleId);
    Optional<ParkingSlot> findFirstByCurrentSessionId(Long sessionId);
    Optional<ParkingSlot> findFirstByCurrentSessionIsNullAndActiveTrueOrderBySlotCodeAsc();

    Optional<ParkingSlot> findFirstByBorrowedPlateIgnoreCaseAndCurrentSessionIsNullAndActiveTrue(String borrowedPlate);

    List<ParkingSlot> findByFloorOrderBySlotCodeAsc(String floor);
    List<ParkingSlot> findByZoneNameOrderBySlotCodeAsc(String zoneName);
    List<ParkingSlot> findByFloorAndZoneNameOrderBySlotCodeAsc(String floor, String zoneName);

    @Query("SELECT DISTINCT s.floor FROM ParkingSlot s WHERE s.active = true ORDER BY s.floor ASC")
    List<String> findDistinctFloors();

    @Query("SELECT DISTINCT s.zoneName FROM ParkingSlot s WHERE s.active = true ORDER BY s.zoneName ASC")
    List<String> findDistinctZones();
}
