package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.EvidenceStatus;
import vn.edu.parking.domain.EvidenceOperation;
import vn.edu.parking.domain.EvidenceType;
import vn.edu.parking.domain.GateEvidence;

public interface GateEvidenceRepository extends JpaRepository<GateEvidence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evidence from GateEvidence evidence where evidence.id = :id")
    Optional<GateEvidence> lockById(@Param("id") String id);

    List<GateEvidence> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
        EvidenceStatus status, Instant cutoff);

    Optional<GateEvidence> findFirstByParkingSessionIdAndEvidenceTypeAndOperationAndStatusOrderByConsumedAtDesc(
        Long parkingSessionId, EvidenceType evidenceType, EvidenceOperation operation, EvidenceStatus status);

    @Query("select e from GateEvidence e where e.evidenceType = :type and e.faceDecision in :decisions and e.createdAt between :from and :to order by e.createdAt desc")
    List<GateEvidence> findRecentFaceExceptions(
        @Param("type") EvidenceType type,
        @Param("decisions") List<String> decisions,
        @Param("from") Instant from,
        @Param("to") Instant to);

    List<GateEvidence> findTop100ByDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(Instant cutoff);
}
