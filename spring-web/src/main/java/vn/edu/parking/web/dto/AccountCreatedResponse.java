package vn.edu.parking.web.dto;

public record AccountCreatedResponse(AccountSummaryResponse account, String temporaryPassword) { }
