package vn.edu.parking.service;

import java.time.Instant;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.repository.SecurityAuditRepository;

@Service
public class SecurityAuditService {
    private final SecurityAuditRepository events;
    public SecurityAuditService(SecurityAuditRepository events) { this.events = events; }

    public void recordLoginFailure(String username) {
        String target = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (target.length() > 80) target = target.substring(0, 80);
        events.save(new SecurityAuditEvent(null, null, "LOGIN_FAILURE", "SYSTEM_ACCOUNT",
            target.isBlank() ? null : target, "FAILURE", "INVALID_CREDENTIALS", null, Instant.now()));
    }

    public void recordLoginThrottled(String username) {
        String target = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (target.length() > 80) target = target.substring(0, 80);
        events.save(new SecurityAuditEvent(null, null, "LOGIN_THROTTLED", "SYSTEM_ACCOUNT",
            target.isBlank() ? null : target, "FAILURE", "TOO_MANY_ATTEMPTS", null, Instant.now()));
    }

    @Transactional
    public void recordLoginSuccess(Authentication actor) {
        record(actor, "LOGIN_SUCCESS", "SYSTEM_ACCOUNT", actor == null ? null : actor.getName(),
            "SUCCESS", null, null);
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
            if (actor.getPrincipal() instanceof AccountPrincipal account) {
                actorId = account.getAccountId();
                actorUsername = account.getUsername();
            }
        }
        events.save(new SecurityAuditEvent(actorId, actorUsername, action, targetType,
            targetReference, outcome, reason, evidenceReference, Instant.now()));
    }
}
