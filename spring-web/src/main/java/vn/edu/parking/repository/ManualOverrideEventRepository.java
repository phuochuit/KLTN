package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.ManualOverrideEvent;

public interface ManualOverrideEventRepository extends JpaRepository<ManualOverrideEvent, Long> {
    long countByGateShiftAssignment_Id(Long shiftId);
}
