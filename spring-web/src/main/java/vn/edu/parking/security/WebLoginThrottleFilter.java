package vn.edu.parking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.parking.service.LoginAttemptThrottle;

import java.io.IOException;

public class WebLoginThrottleFilter extends OncePerRequestFilter {
    private final LoginAttemptThrottle throttle;

    public WebLoginThrottleFilter(LoginAttemptThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestPath = request.getRequestURI().substring(contextPath.length());
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/login".equals(requestPath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (throttle.isBlocked(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"too_many_attempts\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
