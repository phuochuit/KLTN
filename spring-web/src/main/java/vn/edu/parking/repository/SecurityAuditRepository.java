package vn.edu.parking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.SecurityAuditEvent;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, Long> {
    Page<SecurityAuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
    Page<SecurityAuditEvent> findByActionStartingWithOrderByOccurredAtDesc(String actionPrefix, Pageable pageable);
}
