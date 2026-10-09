package vn.edu.parking.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.GateEvidence;
import vn.edu.parking.domain.GateEvidenceKind;
import vn.edu.parking.domain.GateOperationType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface GateEvidenceRepository extends JpaRepository<GateEvidence, String> {
    Optional<GateEvidence> findByIdAndOwnerAccountIdAndDesktopSessionId(
        String id, Long ownerAccountId, String desktopSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evidence from GateEvidence evidence where evidence.id = :id " +
        "and evidence.ownerAccountId = :ownerAccountId and evidence.desktopSessionId = :desktopSessionId")
    Optional<GateEvidence> lockByIdAndOwnerAccountIdAndDesktopSessionId(@Param("id") String id,
        @Param("ownerAccountId") Long ownerAccountId, @Param("desktopSessionId") String desktopSessionId);

    Optional<GateEvidence> findFirstByParkingSessionIdAndOperationTypeAndEvidenceKindAndVerificationDecisionOrderByCreatedAtDesc(
        Long parkingSessionId, GateOperationType operationType, GateEvidenceKind evidenceKind,
        String verificationDecision);

    @Query("select evidence from GateEvidence evidence where evidence.expiresAt <= :now " +
        "and (evidence.preserveUntil is null or evidence.preserveUntil <= :now) " +
        "order by evidence.expiresAt asc")
    List<GateEvidence> findEligibleForDeletion(@Param("now") Instant now, Pageable pageable);

    @Query("select case when count(evidence) > 0 then true else false end from GateEvidence evidence " +
        "where evidence.fileName = :filename or evidence.derivedFileName = :filename")
    boolean isFileReferenced(@Param("filename") String filename);
}
