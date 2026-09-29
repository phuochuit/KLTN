package vn.edu.parking.web.dto;

public record AuthorizedMemberResponse(Long id, String fullName, String relationship,
    String registrationFaceImagePath, boolean faceImageAvailable) { }
