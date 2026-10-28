package vn.edu.parking.config;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.domain.SecurityRole;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagementBootstrapTest {
    private static final String DEMO_SECRET = "synthetic demo secret";
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final SecurityRoleRepository roles = mock(SecurityRoleRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void createsManagementAccountWithForcedPasswordChangeAndEncodedSecret() {
        when(accounts.findByUsername("manager")).thenReturn(Optional.empty());
        when(roles.findByCode("MANAGEMENT")).thenReturn(Optional.of(
            new SecurityRole("MANAGEMENT", "Quản trị viên", "QT", null, true, java.util.Set.of())));
        var bootstrap = new ManagementBootstrap(accounts, encoder, roles, " Manager ",
            "synthetic bootstrap secret", "");
        bootstrap.run(new DefaultApplicationArguments(new String[0]));
        var saved = org.mockito.ArgumentCaptor.forClass(SystemAccount.class);
        verify(accounts).save(saved.capture());
        assertEquals(AccountRole.MANAGEMENT, saved.getValue().getRole());
        assertTrue(saved.getValue().isMustChangePassword());
        assertTrue(encoder.matches("synthetic bootstrap secret", saved.getValue().getPasswordHash()));
    }

    @Test
    void leavesExistingManagementAccountUntouched() {
        when(accounts.findByUsername("manager")).thenReturn(Optional.of(
            new SystemAccount("manager", "existing hash", AccountRole.MANAGEMENT, false)));
        new ManagementBootstrap(accounts, encoder, roles, "manager", "synthetic bootstrap secret", "")
            .run(new DefaultApplicationArguments(new String[0]));
        verify(accounts, never()).save(any());
    }

    @Test
    void doesNothingWhenBootstrapCredentialsAreNotConfigured() {
        new ManagementBootstrap(accounts, encoder, roles, "", "", "")
            .run(new DefaultApplicationArguments(new String[0]));
        verify(accounts, never()).findByUsername(any());
        verify(accounts, never()).save(any());
    }

    @Test
    void seedsDemoAccountWhenDatabaseHasNoAccount() {
        when(accounts.count()).thenReturn(0L);
        when(roles.findByCode("MANAGEMENT")).thenReturn(Optional.of(
            new SecurityRole("MANAGEMENT", "Quản trị viên", "QT", null, true, java.util.Set.of())));
        new ManagementBootstrap(accounts, encoder, roles, "", "", DEMO_SECRET)
            .run(new DefaultApplicationArguments(new String[0]));
        var saved = org.mockito.ArgumentCaptor.forClass(SystemAccount.class);
        verify(accounts).save(saved.capture());
        assertEquals("admin", saved.getValue().getUsername());
        assertEquals(AccountRole.MANAGEMENT, saved.getValue().getRole());
        assertTrue(saved.getValue().isMustChangePassword());
        assertTrue(encoder.matches(DEMO_SECRET, saved.getValue().getPasswordHash()));
    }

    @Test
    void doesNotSeedDemoAccountWhenDatabaseAlreadyHasAccounts() {
        when(accounts.count()).thenReturn(2L);
        new ManagementBootstrap(accounts, encoder, roles, "", "", DEMO_SECRET)
            .run(new DefaultApplicationArguments(new String[0]));
        verify(accounts, never()).save(any());
    }

    @Test
    void rejectsIncompleteOrWeakBootstrapCredentials() {
        assertThrows(IllegalStateException.class, () ->
            new ManagementBootstrap(accounts, encoder, roles, "manager", "", "")
                .run(new DefaultApplicationArguments(new String[0])));
        assertThrows(IllegalStateException.class, () ->
            new ManagementBootstrap(accounts, encoder, roles, "manager", "too-short", "")
                .run(new DefaultApplicationArguments(new String[0])));
    }
}
