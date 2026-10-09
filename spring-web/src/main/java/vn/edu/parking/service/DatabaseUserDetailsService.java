package vn.edu.parking.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.edu.parking.repository.SystemAccountRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final SystemAccountRepository accounts;

    public DatabaseUserDetailsService(SystemAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        var account = accounts.findByUsername(normalized)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
        return new AccountPrincipal(account);
    }
}
