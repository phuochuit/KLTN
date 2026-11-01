package vn.edu.parking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import vn.edu.parking.domain.SecurityAuditEvent;
import java.time.Instant;
import java.util.List;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, Long>, JpaSpecificationExecutor<SecurityAuditEvent> {
    Page<SecurityAuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
    Page<SecurityAuditEvent> findByActionStartingWithOrderByOccurredAtDesc(String actionPrefix, Pageable pageable);
    @Query("select e from SecurityAuditEvent e where substring(e.action, 1, 9) = 'OVERRIDE_' "
            + "or substring(e.action, 1, 16) = 'MANUAL_OVERRIDE_' "
            + "order by e.occurredAt desc, e.id desc")
    Page<SecurityAuditEvent> findOverrideEvents(Pageable pageable);
    List<SecurityAuditEvent> findTop100ByOccurredAtBeforeOrderByOccurredAtAsc(Instant cutoff);
}
