package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.ParkingFloor;

import java.util.List;
import java.util.Optional;

public interface ParkingFloorRepository extends JpaRepository<ParkingFloor, Long> {
    Optional<ParkingFloor> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<ParkingFloor> findByActiveTrueOrderByDisplayOrderAsc();
    List<ParkingFloor> findAllByOrderByDisplayOrderAsc();
    List<ParkingFloor> findByIsBasementOrderByDisplayOrderAsc(boolean isBasement);
}
