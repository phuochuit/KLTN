package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {
    Optional<ParkingSession> findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(String plate, SessionStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSession s where lower(s.entryPlate) = lower(:plate) "
            + "and s.status = :status order by s.entryTime desc, s.id desc")
    List<ParkingSession> findLatestByEntryPlateAndStatusForUpdate(@Param("plate") String plate,
            @Param("status") SessionStatus status, Pageable pageable);
    Optional<ParkingSession> findFirstByParkingCard_CardCodeIgnoreCaseAndStatusOrderByEntryTimeDesc(String cardCode, SessionStatus status);
    boolean existsByEntryPlateIgnoreCaseAndStatus(String plate, SessionStatus status);
    List<ParkingSession> findAllByOrderByEntryTimeDesc();
    List<ParkingSession> findByStatusOrderByEntryTimeDesc(SessionStatus status);
    @Query(value = """
            select session from ParkingSession session left join session.parkingCard card
            where (:status is null or session.status = :status)
              and (:searchPattern = ''
                or lower(session.entryPlate) like :searchPattern escape '!'
                or lower(coalesce(session.exitPlate, '')) like :searchPattern escape '!'
                or lower(coalesce(card.cardCode, '')) like :searchPattern escape '!')
              and (:fromTime is null or session.entryTime >= :fromTime)
              and (:toExclusive is null or session.entryTime < :toExclusive)
            """,
            countQuery = """
            select count(session) from ParkingSession session left join session.parkingCard card
            where (:status is null or session.status = :status)
              and (:searchPattern = ''
                or lower(session.entryPlate) like :searchPattern escape '!'
                or lower(coalesce(session.exitPlate, '')) like :searchPattern escape '!'
                or lower(coalesce(card.cardCode, '')) like :searchPattern escape '!')
              and (:fromTime is null or session.entryTime >= :fromTime)
              and (:toExclusive is null or session.entryTime < :toExclusive)
            """)
    Page<ParkingSession> findHistoryPage(@Param("status") SessionStatus status,
            @Param("searchPattern") String searchPattern,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toExclusive") LocalDateTime toExclusive, Pageable pageable);
    List<ParkingSession> findByStatusAndExitTimeBetween(SessionStatus status, LocalDateTime from, LocalDateTime to);
    long countByEntryTimeBetween(LocalDateTime from, LocalDateTime to);
    long countByStatusAndExitTimeBetween(SessionStatus status, LocalDateTime from, LocalDateTime to);
    long countByStatus(SessionStatus status);

    @Query("select count(s) from ParkingSession s where s.entryTime < :end " +
            "and (s.exitTime is null or s.exitTime >= :end)")
    long countPresentAt(@Param("end") LocalDateTime end);
}
