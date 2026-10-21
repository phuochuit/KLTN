package vn.edu.parking.web;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.parking.repository.SystemAccountRepository;

@ControllerAdvice
public class CurrentAccountModelAdvice {
    private final SystemAccountRepository accounts;

    public CurrentAccountModelAdvice(SystemAccountRepository accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute("currentAccountDisplayName")
    String currentAccountDisplayName(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return accounts.findByUsername(authentication.getName())
            .map(account -> account.getFullName())
            .orElse(authentication.getName());
    }

    @ModelAttribute("currentAuthorities")
    Set<String> currentAuthorities(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return Set.of();
        return authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .collect(Collectors.toUnmodifiableSet());
    }
}
