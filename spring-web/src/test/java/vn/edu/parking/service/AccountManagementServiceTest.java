package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountManagementServiceTest {
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final DesktopRefreshTokenService desktopSessions = mock(DesktopRefreshTokenService.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountManagementService service = new AccountManagementService(accounts, encoder, desktopSessions, audit);

    @Test
    void disablesNonManagementAccountAndInvalidatesItsSecurityVersion() {
        SystemAccount staff = account("gate-1", AccountRole.GATE_STAFF, true);
        when(accounts.lockEnabledByRole(AccountRole.MANAGEMENT))
            .thenReturn(List.of(account("manager", AccountRole.MANAGEMENT, true)));
        when(accounts.lockByUsername("gate-1")).thenReturn(Optional.of(staff));

        service.disable("gate-1");

        assertFalse(staff.isEnabled());
        assertEquals(1, staff.getSecurityVersion());
        verify(accounts).save(staff);
        verify(desktopSessions).revokeAllForAccount(staff.getId());
    }

    @Test
    void protectsTheFinalEnabledManagementAccountFromDisableAndDemotion() {
        SystemAccount manager = account("manager", AccountRole.MANAGEMENT, true);
        when(accounts.lockEnabledByRole(AccountRole.MANAGEMENT)).thenReturn(List.of(manager));
        when(accounts.lockByUsername("manager")).thenReturn(Optional.of(manager));

        assertThrows(LastManagementAccountException.class, () -> service.disable("manager"));
        assertThrows(LastManagementAccountException.class,
            () -> service.changeRole("manager", AccountRole.GATE_STAFF));
    }

    @Test
    void resetIssuesOnlyAnEncodedTemporaryPasswordAndRequiresChange() {
        SystemAccount staff = account("gate-1", AccountRole.GATE_STAFF, true);
        when(accounts.lockByUsername("gate-1")).thenReturn(Optional.of(staff));

        String temporaryPassword = service.resetPassword("gate-1");

        assertTrue(encoder.matches(temporaryPassword, staff.getPasswordHash()));
        assertNotEquals(temporaryPassword, staff.getPasswordHash());
        assertTrue(staff.isMustChangePassword());
        assertEquals(1, staff.getSecurityVersion());
        verify(accounts).save(staff);
        verify(desktopSessions).revokeAllForAccount(staff.getId());
    }

    @Test
    void enableAndRoleChangeAlsoRevokeExistingDesktopSessions() {
        SystemAccount staff = account("gate-2", AccountRole.GATE_STAFF, false);
        when(accounts.lockEnabledByRole(AccountRole.MANAGEMENT))
            .thenReturn(List.of(account("manager", AccountRole.MANAGEMENT, true)));
        when(accounts.lockByUsername("gate-2")).thenReturn(Optional.of(staff));

        service.enable("gate-2");
        service.changeRole("gate-2", AccountRole.MANAGEMENT);

        org.mockito.Mockito.verify(desktopSessions, org.mockito.Mockito.times(2))
            .revokeAllForAccount(staff.getId());
    }

    private SystemAccount account(String username, AccountRole role, boolean enabled) {
        SystemAccount account = new SystemAccount(username, encoder.encode("synthetic password"), role, false);
        account.setEnabled(enabled);
        return account;
    }
}
