package vn.edu.parking.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.repository.SecurityAuditRepository;

import java.time.Instant;
import java.util.Locale;

@Service
public class SecurityAuditService {
    private final SecurityAuditRepository events;

    public SecurityAuditService(SecurityAuditRepository events) {
        this.events = events;
    }

    public void recordLoginFailure(String username) {
        String target = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (target.length() > 80) target = target.substring(0, 80);
        events.save(new SecurityAuditEvent(null, null, "LOGIN_FAILURE", "SYSTEM_ACCOUNT",
            target.isBlank() ? null : target, "FAILURE", "INVALID_CREDENTIALS", null, Instant.now()));
    }

    @Transactional
    public void recordLoginSuccess(Authentication actor) {
        String target = actor == null ? null : actor.getName();
        Long accountId = actor != null && actor.getPrincipal() instanceof AccountPrincipal principal
            ? principal.getAccountId() : null;
        if (target != null && target.length() > 80) target = target.substring(0, 80);
        events.save(new SecurityAuditEvent(accountId, target, "LOGIN_SUCCESS", "SYSTEM_ACCOUNT",
            target, "SUCCESS", null, null, Instant.now()));
    }

    public void recordCurrent(String action, String targetType, String targetReference,
            String outcome, String reason, String evidenceReference) {
        record(SecurityContextHolder.getContext().getAuthentication(), action, targetType,
            targetReference, outcome, reason, evidenceReference);
    }

    public void record(Authentication actor, String action, String targetType, String targetReference,
            String outcome, String reason, String evidenceReference) {
        Long actorId = null;
        String actorUsername = null;
        if (actor != null && actor.isAuthenticated() && !(actor instanceof AnonymousAuthenticationToken)) {
            actorUsername = actor.getName();
            Object principal = actor.getPrincipal();
            if (principal instanceof AccountPrincipal account) {
                actorId = account.getAccountId();
                actorUsername = account.getUsername();
            } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails user) {
                actorUsername = user.getUsername();
            } else if (actor instanceof JwtAuthenticationToken jwt) {
                try {
                    actorId = Long.valueOf(jwt.getToken().getSubject());
                } catch (NumberFormatException ignored) {
                    actorId = null;
                }
            }
        }
        events.save(new SecurityAuditEvent(actorId, actorUsername, action, targetType,
            targetReference, outcome, reason, evidenceReference, Instant.now()));
    }
}
