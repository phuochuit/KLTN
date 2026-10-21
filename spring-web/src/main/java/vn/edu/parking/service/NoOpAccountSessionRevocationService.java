package vn.edu.parking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "parking.security.jwt.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAccountSessionRevocationService implements AccountSessionRevocationService {
    @Override
    public void revokeAllForAccount(Long accountId) {
        // Desktop sessions are introduced in the JWT integration phase.
    }
}
