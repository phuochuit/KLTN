package vn.edu.parking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.AccountPrincipal;

import java.io.IOException;

public final class WebAccountSessionValidationFilter extends OncePerRequestFilter {
    private final SystemAccountRepository accounts;

    public WebAccountSessionValidationFilter(SystemAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            vn.edu.parking.domain.SystemAccount currentAccount;
            try {
                currentAccount = accounts.findById(principal.getAccountId()).orElse(null);
            } catch (RuntimeException unavailable) {
                invalidateSession(request);
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
                return;
            }
            boolean current = currentAccount != null && currentAccount.isEnabled()
                && currentAccount.getSecurityVersion() == principal.getSecurityVersion()
                && currentAccount.getRole() == principal.getRole()
                && currentAccount.isMustChangePassword() == principal.mustChangePassword();
            if (!current) {
                invalidateSession(request);
            }
        }

        chain.doFilter(request, response);
    }

    private static void invalidateSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // A concurrent request may already have invalidated this session.
            }
        }
    }
}
