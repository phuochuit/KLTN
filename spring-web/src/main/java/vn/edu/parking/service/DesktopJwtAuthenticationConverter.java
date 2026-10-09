package vn.edu.parking.service;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import vn.edu.parking.repository.SystemAccountRepository;

import java.time.Instant;
import java.util.ArrayList;

@Component
public class DesktopJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final SystemAccountRepository accounts;
    private final DesktopRefreshTokenService sessions;

    public DesktopJwtAuthenticationConverter(SystemAccountRepository accounts,
            DesktopRefreshTokenService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long accountId;
        long securityVersion;
        try {
            accountId = Long.parseLong(jwt.getSubject());
            Object version = jwt.getClaims().get("ver");
            if (!(version instanceof Number number)) throw new NumberFormatException("Missing token version");
            securityVersion = number.longValue();
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Invalid access token", ex);
        }
        String sessionId = jwt.getClaimAsString("sid");
        var account = accounts.findById(accountId)
            .filter(current -> current.isEnabled() && !current.isMustChangePassword())
            .orElseThrow(() -> new BadCredentialsException("Invalid access token"));
        if (!sessions.touchIfSessionActive(sessionId, accountId, securityVersion, Instant.now()))
            throw new BadCredentialsException("Invalid access token");

        AccountPrincipal principal = new AccountPrincipal(account);
        var authorities = new ArrayList<GrantedAuthority>(principal.getAuthorities());
        if (authorities.stream().anyMatch(authority -> "ROLE_MANAGEMENT".equals(authority.getAuthority())
                || "ROLE_GATE_STAFF".equals(authority.getAuthority())))
            authorities.add(new SimpleGrantedAuthority("OVERRIDE_CREATE"));
        return new JwtAuthenticationToken(jwt, authorities, principal.getUsername());
    }
}
