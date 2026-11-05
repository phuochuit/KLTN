package vn.edu.parking.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.SecurityAuditEvent;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, Long> {
    Page<SecurityAuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
    Optional<SecurityAuditEvent> findTop1ByOrderByIdDesc();
    List<SecurityAuditEvent> findTop100ByActionInAndIdGreaterThanOrderByIdAsc(
        Collection<String> actions, Long id);
    Page<SecurityAuditEvent> findByActionStartingWithOrderByOccurredAtDesc(String actionPrefix, Pageable pageable);
    List<SecurityAuditEvent> findTop500ByOccurredAtBetweenOrderByOccurredAtDesc(Instant from, Instant to);
    List<SecurityAuditEvent> findTop100ByOccurredAtBeforeOrderByOccurredAtAsc(Instant cutoff);
}
