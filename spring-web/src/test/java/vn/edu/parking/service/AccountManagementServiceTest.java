package vn.edu.parking.service;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.domain.SecurityRole;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.repository.SecurityRoleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountManagementServiceTest {
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final SecurityRoleRepository roles = mock(SecurityRoleRepository.class);
    private final AccountSessionRevocationService sessions = mock(AccountSessionRevocationService.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountManagementService service = new AccountManagementService(accounts, roles, encoder, sessions, audit);

    @Test
    void disablesNonManagementAccountAndInvalidatesItsSecurityVersion() {
        var staff = account("gate-1", AccountRole.GATE_STAFF, true);
        when(accounts.lockEnabledByRole(AccountRole.MANAGEMENT))
            .thenReturn(List.of(account("manager", AccountRole.MANAGEMENT, true)));
        when(accounts.lockByUsername("gate-1")).thenReturn(Optional.of(staff));
        service.disable("gate-1");
        assertFalse(staff.isEnabled());
        assertEquals(1, staff.getSecurityVersion());
        verify(accounts).save(staff);
        verify(sessions).revokeAllForAccount(staff.getId());
    }

    @Test
    void protectsTheFinalEnabledManagementAccountFromDisableAndDemotion() {
        var manager = account("manager", AccountRole.MANAGEMENT, true);
        when(accounts.lockEnabledByRole(AccountRole.MANAGEMENT)).thenReturn(List.of(manager));
        when(accounts.lockByUsername("manager")).thenReturn(Optional.of(manager));
        assertThrows(LastManagementAccountException.class, () -> service.disable("manager"));
        assertThrows(LastManagementAccountException.class,
            () -> service.changeRole("manager", AccountRole.GATE_STAFF));
    }

    @Test
    void resetIssuesOnlyAnEncodedTemporaryPasswordAndRequiresChange() {
        var staff = account("gate-1", AccountRole.GATE_STAFF, true);
        when(accounts.lockByUsername("gate-1")).thenReturn(Optional.of(staff));
        String temporaryPassword = service.resetPassword("gate-1");
        assertTrue(encoder.matches(temporaryPassword, staff.getPasswordHash()));
        assertNotEquals(temporaryPassword, staff.getPasswordHash());
        assertTrue(staff.isMustChangePassword());
        assertEquals(1, staff.getSecurityVersion());
        verify(sessions).revokeAllForAccount(staff.getId());
    }

    @Test
    void enableAndRoleChangeAlsoRequestSessionRevocation() {
        var staff = account("gate-2", AccountRole.GATE_STAFF, false);
        when(accounts.lockEnabledByRole(AccountRole.MANAGEMENT))
            .thenReturn(List.of(account("manager", AccountRole.MANAGEMENT, true)));
        when(accounts.lockByUsername("gate-2")).thenReturn(Optional.of(staff));
        service.enable("gate-2");
        service.changeRole("gate-2", AccountRole.MANAGEMENT);
        verify(sessions, times(2)).revokeAllForAccount(staff.getId());
    }

    @Test
    void profileOnlyUpdateDoesNotRevokeActiveSessions() {
        var staff = account("gate-3", AccountRole.GATE_STAFF, true);
        var role = new SecurityRole("GATE_STAFF", "Nhân viên gác cổng", "GC", null, true,
            java.util.Set.of());
        staff.setSecurityRole(role);
        when(accounts.lockByUsername("gate-3")).thenReturn(Optional.of(staff));
        when(roles.findById(7L)).thenReturn(Optional.of(role));
        when(accounts.findByEmailIgnoreCase("gate3@example.test")).thenReturn(Optional.empty());
        when(accounts.findByCitizenId("079203001235")).thenReturn(Optional.empty());

        service.updateProfile("gate-3", 7L, new AccountManagementService.EmployeeProfile(
            "Nguyễn Văn Ba", "0901234568", "gate3@example.test", "079203001235",
            LocalDate.of(2000, 1, 15), "Quận 1, Thành phố Hồ Chí Minh"));

        assertEquals(0, staff.getSecurityVersion());
        verify(accounts).save(staff);
        verify(sessions, never()).revokeAllForAccount(staff.getId());
    }

    private SystemAccount account(String username, AccountRole role, boolean enabled) {
        var account = new SystemAccount(username, encoder.encode("synthetic password"), role, false);
        account.setEnabled(enabled);
        return account;
    }
}
