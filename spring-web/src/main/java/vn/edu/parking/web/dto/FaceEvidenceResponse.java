package vn.edu.parking.web.dto;

public record FaceEvidenceResponse(String evidenceId, String decision, double similarity,
    Double matchThreshold, String message, String realtimeImageBase64) { }
