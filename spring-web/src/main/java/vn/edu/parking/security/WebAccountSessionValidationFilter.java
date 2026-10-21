package vn.edu.parking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.AccountPrincipal;

public final class WebAccountSessionValidationFilter extends OncePerRequestFilter {
    private final SystemAccountRepository accounts;
    public WebAccountSessionValidationFilter(SystemAccountRepository accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            vn.edu.parking.domain.SystemAccount current;
            try {
                current = accounts.findById(principal.getAccountId()).orElse(null);
            } catch (RuntimeException unavailable) {
                invalidateSession(request);
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
                return;
            }
            boolean valid = current != null && current.isEnabled()
                && current.getSecurityVersion() == principal.getSecurityVersion()
                && current.getRole() == principal.getRole()
                && current.isMustChangePassword() == principal.mustChangePassword();
            if (!valid) invalidateSession(request);
        }
        chain.doFilter(request, response);
    }

    private static void invalidateSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            try { session.invalidate(); }
            catch (IllegalStateException ignored) { /* A concurrent request already invalidated it. */ }
        }
    }
}
