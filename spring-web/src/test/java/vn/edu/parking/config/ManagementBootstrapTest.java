package vn.edu.parking.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagementBootstrapTest {
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void createsManagementAccountWithForcedPasswordChangeAndEncodedSecret() {
        when(accounts.findByUsername("manager")).thenReturn(Optional.empty());
        ManagementBootstrap bootstrap = new ManagementBootstrap(accounts, encoder, " Manager ",
            "synthetic bootstrap secret");

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        var saved = org.mockito.ArgumentCaptor.forClass(SystemAccount.class);
        verify(accounts).save(saved.capture());
        SystemAccount account = saved.getValue();
        assertEquals(AccountRole.MANAGEMENT, account.getRole());
        assertTrue(account.isMustChangePassword());
        assertTrue(encoder.matches("synthetic bootstrap secret", account.getPasswordHash()));
    }

    @Test
    void leavesExistingManagementAccountUntouched() {
        when(accounts.findByUsername("manager")).thenReturn(Optional.of(
            new SystemAccount("manager", "existing hash", AccountRole.MANAGEMENT, false)));
        ManagementBootstrap bootstrap = new ManagementBootstrap(accounts, encoder, "manager",
            "synthetic bootstrap secret");

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(accounts, never()).save(any());
    }

    @Test
    void doesNothingWhenBootstrapCredentialsAreNotConfigured() {
        ManagementBootstrap bootstrap = new ManagementBootstrap(accounts, encoder, "", "");

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(accounts, never()).findByUsername(any());
        verify(accounts, never()).save(any());
    }
}
