package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.ParkingPolicy;
import java.util.Optional;

public interface ParkingPolicyRepository extends JpaRepository<ParkingPolicy, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from ParkingPolicy p where p.id = :id")
    Optional<ParkingPolicy> findForQuotaCheck(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParkingPolicy p where p.id = :id")
    Optional<ParkingPolicy> findByIdForUpdate(@Param("id") Long id);
}
