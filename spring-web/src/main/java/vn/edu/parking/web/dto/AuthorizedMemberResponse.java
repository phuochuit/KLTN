package vn.edu.parking.web.dto;

public record AuthorizedMemberResponse(Long id, String fullName, String relationship,
    boolean faceImageAvailable) { }
