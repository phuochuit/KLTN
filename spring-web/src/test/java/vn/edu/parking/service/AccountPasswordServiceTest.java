package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountPasswordServiceTest {
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final DesktopRefreshTokenService desktopSessions = mock(DesktopRefreshTokenService.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountPasswordService service = new AccountPasswordService(accounts, encoder, desktopSessions, audit);

    @Test
    void changesPasswordOnlyAfterVerifyingCurrentPasswordAndClearsForcedChange() {
        SystemAccount account = account("old synthetic password");
        account.setMustChangePassword(true);
        when(accounts.lockByUsername("manager")).thenReturn(Optional.of(account));

        service.changeOwnPassword("manager", "old synthetic password", "new synthetic password");

        assertTrue(encoder.matches("new synthetic password", account.getPasswordHash()));
        assertFalse(account.isMustChangePassword());
        assertTrue(account.getSecurityVersion() > 0);
        verify(accounts).save(account);
        verify(desktopSessions).revokeAllForAccount(account.getId());
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutChangingAccount() {
        SystemAccount account = account("old synthetic password");
        when(accounts.lockByUsername("manager")).thenReturn(Optional.of(account));

        assertThrows(InvalidCurrentPasswordException.class,
            () -> service.changeOwnPassword("manager", "wrong synthetic password", "new synthetic password"));

        assertTrue(encoder.matches("old synthetic password", account.getPasswordHash()));
    }

    @Test
    void rejectsTooShortNewPassword() {
        SystemAccount account = account("old synthetic password");
        when(accounts.lockByUsername("manager")).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
            () -> service.changeOwnPassword("manager", "old synthetic password", "short"));
    }

    private SystemAccount account(String password) {
        return new SystemAccount("manager", encoder.encode(password), AccountRole.MANAGEMENT, false);
    }
}
