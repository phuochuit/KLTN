using System.Text.Json.Serialization;

namespace ParkingGateDesktop;

public record ParkingRequest(
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("cardCode")] string CardCode,
    [property: JsonPropertyName("vehicleType")] string VehicleType,
    [property: JsonPropertyName("manualOverride")] bool ManualOverride,
    [property: JsonPropertyName("familyMemberId")] long? FamilyMemberId,
    [property: JsonPropertyName("evidenceId")] string? EvidenceId,
    [property: JsonPropertyName("faceEvidenceId")] string? FaceEvidenceId,
    [property: JsonPropertyName("override")] ManualOverrideRequest? Override = null);

public record ManualOverrideRequest([property: JsonPropertyName("reason")] string Reason);

public record AuthorizedMemberResponse(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("fullName")] string FullName,
    [property: JsonPropertyName("relationship")] string Relationship,
    [property: JsonPropertyName("faceImageAvailable")] bool FaceImageAvailable)
{ public override string ToString() => $"{FullName} ({Relationship})"; }

public record ParkingResponse(
    [property: JsonPropertyName("sessionId")] long SessionId,
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("ownerName")] string OwnerName,
    [property: JsonPropertyName("vehicleType")] string VehicleType,
    [property: JsonPropertyName("cardCode")] string CardCode,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("entryTime")] DateTime EntryTime,
    [property: JsonPropertyName("exitTime")] DateTime? ExitTime,
    [property: JsonPropertyName("fee")] decimal Fee,
    [property: JsonPropertyName("message")] string Message,
    [property: JsonPropertyName("warning")] bool Warning);

public record ResidentLookupResponse(
    [property: JsonPropertyName("registered")] bool Registered,
    [property: JsonPropertyName("cardMatched")] bool CardMatched,
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("ownerName")] string OwnerName,
    [property: JsonPropertyName("ownerPhone")] string OwnerPhone,
    [property: JsonPropertyName("apartmentNumber")] string ApartmentNumber,
    [property: JsonPropertyName("vehicleType")] string VehicleType,
    [property: JsonPropertyName("cardCode")] string CardCode,
    [property: JsonPropertyName("passType")] string PassType,
    [property: JsonPropertyName("validUntil")] DateTime? ValidUntil,
    [property: JsonPropertyName("monthlyValid")] bool MonthlyValid,
    [property: JsonPropertyName("message")] string Message,
    [property: JsonPropertyName("authorizedMembers")] List<AuthorizedMemberResponse> AuthorizedMembers);

public record FaceVerificationResponse(
    [property: JsonPropertyName("decision")] string Decision,
    [property: JsonPropertyName("similarity")] double Similarity,
    [property: JsonPropertyName("matchThreshold")] double? MatchThreshold,
    [property: JsonPropertyName("message")] string Message,
    [property: JsonPropertyName("realtimeImageBase64")] string RealtimeImageBase64,
    [property: JsonPropertyName("evidenceId")] string? EvidenceId);

public record ParkingSlotResponse(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("slotCode")] string SlotCode,
    [property: JsonPropertyName("zoneName")] string ZoneName,
    [property: JsonPropertyName("floor")] string? Floor,
    [property: JsonPropertyName("slotType")] string? SlotType,
    [property: JsonPropertyName("allowedVehicleType")] string? AllowedVehicleType,
    [property: JsonPropertyName("statusOverride")] string? StatusOverride,
    [property: JsonPropertyName("assignedPlate")] string? AssignedPlate,
    [property: JsonPropertyName("assignedOwnerName")] string? AssignedOwnerName,
    [property: JsonPropertyName("assignedOwnerPhone")] string? AssignedOwnerPhone,
    [property: JsonPropertyName("assignedApartment")] string? AssignedApartment,
    [property: JsonPropertyName("occupiedPlate")] string? OccupiedPlate,
    [property: JsonPropertyName("occupiedEntryTime")] DateTime? OccupiedEntryTime,
    [property: JsonPropertyName("borrowedPlate")] string? BorrowedPlate,
    [property: JsonPropertyName("borrowedUntil")] DateTime? BorrowedUntil,
    [property: JsonPropertyName("borrowNotes")] string? BorrowNotes,
    [property: JsonPropertyName("overdue")] bool Overdue,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("statusDescription")] string? StatusDescription);

public record SlotAssignRequest([property: JsonPropertyName("vehicleId")] long? VehicleId);
public record SlotBorrowRequest(
    [property: JsonPropertyName("borrowedPlate")] string BorrowedPlate,
    [property: JsonPropertyName("hours")] int Hours,
    [property: JsonPropertyName("borrowNotes")] string? BorrowNotes);
public record SlotStatusRequest([property: JsonPropertyName("statusOverride")] string StatusOverride);
public record DispatchSessionRequest([property: JsonPropertyName("sessionId")] long SessionId);

public record UnassignedSessionResponse(
    [property: JsonPropertyName("sessionId")] long SessionId,
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("vehicleType")] string VehicleType,
    [property: JsonPropertyName("entryTime")] string EntryTime,
    [property: JsonPropertyName("slotCode")] string SlotCode);

public record UnassignedVehicleResponse(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("vehicleType")] string VehicleType);

public record BoundingBox(
    [property: JsonPropertyName("x")] int X,
    [property: JsonPropertyName("y")] int Y,
    [property: JsonPropertyName("width")] int Width,
    [property: JsonPropertyName("height")] int Height);

public record AnprResponse(
    [property: JsonPropertyName("plateText")] string PlateText,
    [property: JsonPropertyName("vehicleType")] string VehicleType,
    [property: JsonPropertyName("detectionConfidence")] double DetectionConfidence,
    [property: JsonPropertyName("ocrConfidence")] double OcrConfidence,
    [property: JsonPropertyName("vehicleConfidence")] double VehicleConfidence,
    [property: JsonPropertyName("boundingBox")] BoundingBox? BoundingBox,
    [property: JsonPropertyName("frameIndex")] int FrameIndex,
    [property: JsonPropertyName("annotatedImageBase64")] string AnnotatedImageBase64,
    [property: JsonPropertyName("message")] string Message,
    [property: JsonPropertyName("evidenceId")] string? EvidenceId,
    [property: JsonPropertyName("processingStatus")] string? ProcessingStatus = null);
