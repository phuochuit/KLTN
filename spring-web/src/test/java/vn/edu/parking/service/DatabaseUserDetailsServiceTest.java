package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {
    private final SystemAccountRepository accounts = mock(SystemAccountRepository.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(accounts);

    @Test
    void loadsDatabaseAccountAndMapsItsStableRole() {
        SystemAccount account = new SystemAccount("gate-01", "{bcrypt}encoded-value",
            AccountRole.GATE_STAFF, false);
        when(accounts.findByUsername("gate-01")).thenReturn(Optional.of(account));

        var user = service.loadUserByUsername("gate-01");

        assertEquals("{bcrypt}encoded-value", user.getPassword());
        assertEquals("ROLE_GATE_STAFF", user.getAuthorities().iterator().next().getAuthority());
        assertEquals("gate-01", user.getUsername());
    }

    @Test
    void temporaryPasswordAccountGetsNoBusinessRole() {
        SystemAccount account = new SystemAccount("manager-01", "{bcrypt}encoded-value",
            AccountRole.MANAGEMENT, true);
        when(accounts.findByUsername("manager-01")).thenReturn(Optional.of(account));

        var user = service.loadUserByUsername("manager-01");

        assertEquals("ROLE_PASSWORD_CHANGE_REQUIRED", user.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rejectsUnknownUsernameWithoutReturningDefaultAccount() {
        when(accounts.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing"));
    }
}
