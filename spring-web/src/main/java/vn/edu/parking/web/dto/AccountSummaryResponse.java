package vn.edu.parking.web.dto;

import java.time.LocalDate;

public record AccountSummaryResponse(Long id, String username, String employeeCode, String fullName,
        String phone, String email, String citizenId, LocalDate dateOfBirth, String address,
        Long roleId, String roleCode, String roleName, boolean enabled, boolean mustChangePassword) { }
