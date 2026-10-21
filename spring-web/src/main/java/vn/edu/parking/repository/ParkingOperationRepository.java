package vn.edu.parking.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import vn.edu.parking.domain.ParkingOperation;
import jakarta.persistence.LockModeType;

public interface ParkingOperationRepository extends JpaRepository<ParkingOperation, Long> {
    Optional<ParkingOperation> findByActorUsernameAndOperationTypeAndOperationKey(
        String actorUsername, String operationType, String operationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingOperation> findForUpdateById(Long id);
}
