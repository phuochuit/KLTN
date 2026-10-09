package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;

import java.util.List;
import java.util.Optional;

public interface SystemAccountRepository extends JpaRepository<SystemAccount, Long> {
    Optional<SystemAccount> findByUsername(String username);
    List<SystemAccount> findAllByOrderByUsernameAsc();
    long countByRoleAndEnabledTrue(AccountRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from SystemAccount account where account.username = :username")
    Optional<SystemAccount> lockByUsername(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from SystemAccount account where account.role = :role and account.enabled = true order by account.id")
    List<SystemAccount> lockEnabledByRole(@Param("role") AccountRole role);
}
