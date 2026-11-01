package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.OutboxEvent;
import vn.edu.parking.domain.OutboxStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    Optional<OutboxEvent> findByEventId(String eventId);
    List<OutboxEvent> findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(Collection<OutboxStatus> statuses, Instant nextRetryAt);
    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status);
    long countByStatus(OutboxStatus status);
}
