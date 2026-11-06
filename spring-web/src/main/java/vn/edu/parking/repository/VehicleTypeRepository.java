package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.domain.VehicleTypeEntity;

import java.util.List;
import java.util.Optional;

public interface VehicleTypeRepository extends JpaRepository<VehicleTypeEntity, Long> {
    Optional<VehicleTypeEntity> findByCodeIgnoreCase(String code);
    Optional<VehicleTypeEntity> findByDisplayNameIgnoreCase(String displayName);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByDisplayNameIgnoreCase(String displayName);
    List<VehicleTypeEntity> findByActiveTrueOrderByDisplayNameAsc();
    List<VehicleTypeEntity> findAllByOrderByDisplayNameAsc();
    Optional<VehicleTypeEntity> findFirstByCategoryAndActiveTrueOrderByIdAsc(VehicleType category);
}
