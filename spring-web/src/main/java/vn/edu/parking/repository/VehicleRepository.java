package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.Vehicle;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByPlateNumberIgnoreCase(String plateNumber);

    List<Vehicle> findByHouseholdIdAndActiveTrue(Long householdId);

    List<Vehicle> findByRegisteredOwnerId(Long ownerId);

    List<Vehicle> findByAuthorizedMembers_Id(Long memberId);
}
