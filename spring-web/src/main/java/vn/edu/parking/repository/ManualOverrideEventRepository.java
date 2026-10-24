package vn.edu.parking.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.ManualOverrideEvent;

public interface ManualOverrideEventRepository extends JpaRepository<ManualOverrideEvent, Long> {
    long countByGateShiftAssignment_Id(Long shiftId);

    @EntityGraph(attributePaths = { "account", "gateShiftAssignment", "parkingSession" })
    List<ManualOverrideEvent> findTop500ByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    @EntityGraph(attributePaths = { "account", "gateShiftAssignment", "parkingSession" })
    List<ManualOverrideEvent> findTop500ByOrderByCreatedAtDesc();
}
