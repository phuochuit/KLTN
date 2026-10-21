package vn.edu.parking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.parking.service.LoginAttemptThrottle;
import vn.edu.parking.service.SecurityAuditService;

public final class WebLoginThrottleFilter extends OncePerRequestFilter {
    private final LoginAttemptThrottle throttle;
    private final SecurityAuditService audit;

    public WebLoginThrottleFilter(LoginAttemptThrottle throttle, SecurityAuditService audit) {
        this.throttle = throttle;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = request.getParameter("username");
        if (throttle.isBlocked(username, request.getRemoteAddr())) {
            audit.recordLoginThrottled(username);
            response.sendRedirect(request.getContextPath() + "/login?throttled");
            return;
        }
        chain.doFilter(request, response);
    }
}
