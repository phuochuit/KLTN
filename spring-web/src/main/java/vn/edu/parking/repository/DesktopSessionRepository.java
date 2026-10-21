package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.DesktopSession;

public interface DesktopSessionRepository extends JpaRepository<DesktopSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from DesktopSession session where session.sessionId = :sessionId")
    Optional<DesktopSession> lockBySessionId(@Param("sessionId") String sessionId);

    List<DesktopSession> findAllByAccount_IdAndRevokedAtIsNull(Long accountId);

    @EntityGraph(attributePaths = "account")
    List<DesktopSession> findTop100ByOrderByCreatedAtDesc();
}
