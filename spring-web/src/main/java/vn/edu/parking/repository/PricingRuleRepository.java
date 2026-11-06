package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.PricingRule;
import vn.edu.parking.domain.VehicleType;
import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    Optional<PricingRule> findByVehicleType(VehicleType vehicleType);
    Optional<PricingRule> findFirstByVehicleTypeOrderByIdAsc(VehicleType vehicleType);
    Optional<PricingRule> findByVehicleTypeRef_Id(Long vehicleTypeId);
    Optional<PricingRule> findByVehicleTypeRef_Code(String code);
    boolean existsByVehicleTypeRef_Id(Long vehicleTypeId);
}
