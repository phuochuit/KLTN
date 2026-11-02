package vn.edu.parking.web.dto;

import java.time.LocalDateTime;

public record EvidenceComparisonResponse(
    Long sessionId,
    String sessionStatus,
    LocalDateTime entryTime,
    LocalDateTime exitTime,
    String entryPlate,
    String exitPlate,
    boolean plateMatched,
    String entryVehicleType,
    String exitVehicleType,
    boolean vehicleTypeMatched,
    Double entryDetectionConfidence,
    Double entryOcrConfidence,
    Double entryVehicleConfidence,
    Double exitDetectionConfidence,
    Double exitOcrConfidence,
    Double exitVehicleConfidence,
    String entryFaceDecision,
    Double entryFaceSimilarity,
    String exitFaceDecision,
    Double exitFaceSimilarity,
    boolean faceMatched,
    boolean manualOverride,
    String matchStatus,
    String summaryMessage,
    String entryVehicleEvidenceId,
    String exitVehicleEvidenceId,
    String entryFaceEvidenceId,
    String exitFaceEvidenceId,
    String entryVehicleImageBase64,
    String exitVehicleImageBase64,
    String entryFaceImageBase64,
    String exitFaceImageBase64
) {
}
