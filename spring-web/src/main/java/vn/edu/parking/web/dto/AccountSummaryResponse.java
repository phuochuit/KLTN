package vn.edu.parking.web.dto;

import vn.edu.parking.domain.AccountRole;

public record AccountSummaryResponse(Long id, String username, AccountRole role,
        boolean enabled, boolean mustChangePassword) { }
