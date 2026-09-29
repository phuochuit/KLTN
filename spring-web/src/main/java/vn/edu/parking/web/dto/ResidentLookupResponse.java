package vn.edu.parking.web.dto;

import java.time.LocalDate;
import java.util.List;

public record ResidentLookupResponse(
    boolean registered,
    boolean cardMatched,
    String plateNumber,
    String ownerName,
    String ownerPhone,
    String apartmentNumber,
    String vehicleType,
    String cardCode,
    String passType,
    LocalDate validUntil,
    boolean monthlyValid,
    String message,
    List<AuthorizedMemberResponse> authorizedMembers,
    String guestEntryFaceImagePath
) { }
