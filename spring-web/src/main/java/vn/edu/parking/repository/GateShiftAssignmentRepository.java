package vn.edu.parking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.GateShiftAssignment;

public interface GateShiftAssignmentRepository extends JpaRepository<GateShiftAssignment, Long> {
    List<GateShiftAssignment> findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
        Long accountId, Instant startsAt, Instant endsAt);

    @Query("select count(shift) from GateShiftAssignment shift where shift.account.id = :accountId "
        + "and shift.startsAt < :endsAt and shift.endsAt > :startsAt")
    long countOverlapping(@Param("accountId") Long accountId,
        @Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt);

    @EntityGraph(attributePaths = "account")
    List<GateShiftAssignment> findTop50ByOrderByStartsAtDesc();

    Optional<GateShiftAssignment> findFirstByAccount_IdAndEndsAtLessThanEqualOrderByEndsAtDesc(
        Long accountId, Instant endsAt);

    @EntityGraph(attributePaths = "account")
    Optional<GateShiftAssignment> findFirstByEndsAtLessThanEqualOrderByEndsAtDesc(Instant endsAt);
}
