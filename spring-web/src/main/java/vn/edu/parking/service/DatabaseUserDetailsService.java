package vn.edu.parking.service;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.edu.parking.repository.SystemAccountRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final SystemAccountRepository accounts;
    public DatabaseUserDetailsService(SystemAccountRepository accounts) { this.accounts = accounts; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return accounts.findByUsername(normalized).map(AccountPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }
}
