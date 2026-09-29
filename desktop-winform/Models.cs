using System.Text.Json.Serialization;

namespace ParkingGateDesktop;

public record ParkingRequest(
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("cardCode")] string CardCode,
    [property: JsonPropertyName("vehicleType")] string VehicleType,
    [property: JsonPropertyName("manualOverride")] bool ManualOverride,
    [property: JsonPropertyName("familyMemberId")] long? FamilyMemberId,
    [property: JsonPropertyName("faceVerified")] bool FaceVerified,
    [property: JsonPropertyName("faceSimilarity")] double? FaceSimilarity,
    [property: JsonPropertyName("realtimeFaceImageBase64")] string RealtimeFaceImageBase64);

public record AuthorizedMemberResponse(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("fullName")] string FullName,
    [property: JsonPropertyName("relationship")] string Relationship,
    [property: JsonPropertyName("registrationFaceImagePath")] string RegistrationFaceImagePath,
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
    [property: JsonPropertyName("authorizedMembers")] List<AuthorizedMemberResponse> AuthorizedMembers,
    [property: JsonPropertyName("guestEntryFaceImagePath")] string GuestEntryFaceImagePath);

public record FaceVerificationResponse(
    [property: JsonPropertyName("decision")] string Decision,
    [property: JsonPropertyName("similarity")] double Similarity,
    [property: JsonPropertyName("matchThreshold")] double MatchThreshold,
    [property: JsonPropertyName("message")] string Message,
    [property: JsonPropertyName("realtimeImageBase64")] string RealtimeImageBase64);

public record ParkingSlotResponse(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("slotCode")] string SlotCode,
    [property: JsonPropertyName("zoneName")] string ZoneName,
    [property: JsonPropertyName("assignedPlate")] string AssignedPlate,
    [property: JsonPropertyName("occupiedPlate")] string OccupiedPlate,
    [property: JsonPropertyName("status")] string Status);

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
    [property: JsonPropertyName("message")] string Message);
