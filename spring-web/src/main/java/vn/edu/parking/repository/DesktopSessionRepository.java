package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.DesktopSession;

import java.util.List;
import java.util.Optional;

public interface DesktopSessionRepository extends JpaRepository<DesktopSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from DesktopSession session where session.sessionId = :sessionId")
    Optional<DesktopSession> lockBySessionId(@Param("sessionId") String sessionId);

    List<DesktopSession> findAllByAccount_IdAndRevokedAtIsNull(Long accountId);
}
