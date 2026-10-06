package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.Household;
import java.util.Optional;
import java.time.LocalDateTime;

public interface HouseholdRepository extends JpaRepository<Household, Long> {
    Optional<Household> findByHouseholdCodeIgnoreCase(String householdCode);
    long countByCreatedAtBefore(LocalDateTime time);
}
