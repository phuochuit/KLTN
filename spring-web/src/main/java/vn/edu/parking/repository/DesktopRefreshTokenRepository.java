package vn.edu.parking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.parking.domain.DesktopRefreshToken;

import java.util.Optional;

public interface DesktopRefreshTokenRepository extends JpaRepository<DesktopRefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from DesktopRefreshToken token where token.tokenHash = :tokenHash")
    Optional<DesktopRefreshToken> lockByTokenHash(@Param("tokenHash") String tokenHash);
}
