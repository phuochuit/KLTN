package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {
    Optional<ParkingSession> findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(String plate, SessionStatus status);
    Optional<ParkingSession> findFirstByParkingCard_CardCodeIgnoreCaseAndStatusOrderByEntryTimeDesc(String cardCode, SessionStatus status);
    boolean existsByEntryPlateIgnoreCaseAndStatus(String plate, SessionStatus status);
    List<ParkingSession> findAllByOrderByEntryTimeDesc();
    List<ParkingSession> findByStatusOrderByEntryTimeDesc(SessionStatus status);
    List<ParkingSession> findByStatusAndExitTimeBetween(SessionStatus status, LocalDateTime from, LocalDateTime to);
    long countByStatus(SessionStatus status);

    @Query("select count(s) from ParkingSession s where s.entryTime < :end " +
            "and (s.exitTime is null or s.exitTime >= :end)")
    long countPresentAt(@Param("end") LocalDateTime end);
}
