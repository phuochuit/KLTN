using System.Text.Json.Serialization;

namespace ParkingGateDesktop;

public record OperatorShift(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("startsAt")] DateTimeOffset StartsAt,
    [property: JsonPropertyName("endsAt")] DateTimeOffset EndsAt,
    [property: JsonPropertyName("duty")] string Duty);

public record OperatorProfile(
    [property: JsonPropertyName("username")] string Username,
    [property: JsonPropertyName("fullName")] string FullName,
    [property: JsonPropertyName("activeShift")] OperatorShift? ActiveShift);

public record ShiftSummary(
    [property: JsonPropertyName("shiftId")] long ShiftId,
    [property: JsonPropertyName("shiftName")] string ShiftName,
    [property: JsonPropertyName("operatorName")] string OperatorName,
    [property: JsonPropertyName("startsAt")] DateTimeOffset StartsAt,
    [property: JsonPropertyName("endsAt")] DateTimeOffset EndsAt,
    [property: JsonPropertyName("summarizedAt")] DateTimeOffset SummarizedAt,
    [property: JsonPropertyName("duty")] string Duty,
    [property: JsonPropertyName("totalRevenue")] decimal TotalRevenue,
    [property: JsonPropertyName("cashRevenue")] decimal CashRevenue,
    [property: JsonPropertyName("bankTransferRevenue")] decimal BankTransferRevenue,
    [property: JsonPropertyName("posRevenue")] decimal PosRevenue,
    [property: JsonPropertyName("vehicleEntries")] long VehicleEntries,
    [property: JsonPropertyName("vehicleExits")] long VehicleExits,
    [property: JsonPropertyName("vehiclesCurrentlyInside")] long VehiclesCurrentlyInside,
    [property: JsonPropertyName("occupiedSlots")] long OccupiedSlots,
    [property: JsonPropertyName("availableSlots")] long AvailableSlots,
    [property: JsonPropertyName("pendingReconciliation")] long PendingReconciliation,
    [property: JsonPropertyName("manualOverrides")] long ManualOverrides);

public record ParkingRequest(
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("cardCode")] string CardCode,
    [property: JsonPropertyName("vehicleType")] string VehicleType,
    [property: JsonPropertyName("manualOverride")] bool ManualOverride,
    [property: JsonPropertyName("familyMemberId")] long? FamilyMemberId,
    [property: JsonPropertyName("faceVerified")] bool FaceVerified,
    [property: JsonPropertyName("faceSimilarity")] double? FaceSimilarity,
    [property: JsonPropertyName("realtimeFaceImageBase64")] string RealtimeFaceImageBase64,
    [property: JsonPropertyName("vehicleEvidenceId")] string VehicleEvidenceId,
    [property: JsonPropertyName("faceEvidenceId")] string FaceEvidenceId,
    [property: JsonPropertyName("manualOverrideReasonCode")] string ManualOverrideReasonCode,
    [property: JsonPropertyName("manualOverrideReasonText")] string ManualOverrideReasonText,
    [property: JsonPropertyName("paymentMethod")] string? PaymentMethod = null,
    [property: JsonPropertyName("paymentReference")] string? PaymentReference = null,
    [property: JsonPropertyName("operationId")] string? OperationId = null);

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
    [property: JsonPropertyName("warning")] bool Warning,
    [property: JsonPropertyName("receiptNumber")] string? ReceiptNumber = null,
    [property: JsonPropertyName("paymentMethod")] string? PaymentMethod = null,
    [property: JsonPropertyName("paymentReference")] string? PaymentReference = null);

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
    [property: JsonPropertyName("guestEntryFaceAvailable")] bool GuestEntryFaceAvailable);

public record FaceVerificationResponse(
    [property: JsonPropertyName("decision")] string Decision,
    [property: JsonPropertyName("similarity")] double Similarity,
    [property: JsonPropertyName("matchThreshold")] double MatchThreshold,
    [property: JsonPropertyName("message")] string Message,
    [property: JsonPropertyName("realtimeImageBase64")] string RealtimeImageBase64,
    [property: JsonPropertyName("contractVersion")] string? ContractVersion = null,
    [property: JsonPropertyName("requestId")] string? RequestId = null,
    [property: JsonPropertyName("evidenceId")] string? EvidenceId = null,
    [property: JsonPropertyName("processedAt")] DateTimeOffset? ProcessedAt = null,
    [property: JsonPropertyName("modelVersion")] string? ModelVersion = null);

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
    [property: JsonPropertyName("ownerName")] string OwnerName);

public record UnassignedVehicleResponse(
    [property: JsonPropertyName("id")] long Id,
    [property: JsonPropertyName("plateNumber")] string PlateNumber,
    [property: JsonPropertyName("ownerName")] string OwnerName,
    [property: JsonPropertyName("apartmentNumber")] string ApartmentNumber,
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
    [property: JsonPropertyName("contractVersion")] string? ContractVersion = null,
    [property: JsonPropertyName("requestId")] string? RequestId = null,
    [property: JsonPropertyName("evidenceId")] string? EvidenceId = null,
    [property: JsonPropertyName("processedAt")] DateTimeOffset? ProcessedAt = null,
    [property: JsonPropertyName("modelVersion")] string? ModelVersion = null);

public record EvidenceComparisonResponse(
    [property: JsonPropertyName("sessionId")] long SessionId,
    [property: JsonPropertyName("sessionStatus")] string SessionStatus,
    [property: JsonPropertyName("entryTime")] DateTime EntryTime,
    [property: JsonPropertyName("exitTime")] DateTime? ExitTime,
    [property: JsonPropertyName("entryPlate")] string EntryPlate,
    [property: JsonPropertyName("exitPlate")] string ExitPlate,
    [property: JsonPropertyName("plateMatched")] bool PlateMatched,
    [property: JsonPropertyName("entryVehicleType")] string EntryVehicleType,
    [property: JsonPropertyName("exitVehicleType")] string ExitVehicleType,
    [property: JsonPropertyName("vehicleTypeMatched")] bool VehicleTypeMatched,
    [property: JsonPropertyName("entryDetectionConfidence")] double? EntryDetectionConfidence,
    [property: JsonPropertyName("entryOcrConfidence")] double? EntryOcrConfidence,
    [property: JsonPropertyName("entryVehicleConfidence")] double? EntryVehicleConfidence,
    [property: JsonPropertyName("exitDetectionConfidence")] double? ExitDetectionConfidence,
    [property: JsonPropertyName("exitOcrConfidence")] double? ExitOcrConfidence,
    [property: JsonPropertyName("exitVehicleConfidence")] double? ExitVehicleConfidence,
    [property: JsonPropertyName("entryFaceDecision")] string EntryFaceDecision,
    [property: JsonPropertyName("entryFaceSimilarity")] double? EntryFaceSimilarity,
    [property: JsonPropertyName("exitFaceDecision")] string ExitFaceDecision,
    [property: JsonPropertyName("exitFaceSimilarity")] double? ExitFaceSimilarity,
    [property: JsonPropertyName("faceMatched")] bool FaceMatched,
    [property: JsonPropertyName("manualOverride")] bool ManualOverride,
    [property: JsonPropertyName("matchStatus")] string MatchStatus,
    [property: JsonPropertyName("summaryMessage")] string SummaryMessage,
    [property: JsonPropertyName("entryVehicleEvidenceId")] string? EntryVehicleEvidenceId,
    [property: JsonPropertyName("exitVehicleEvidenceId")] string? ExitVehicleEvidenceId,
    [property: JsonPropertyName("entryFaceEvidenceId")] string? EntryFaceEvidenceId,
    [property: JsonPropertyName("exitFaceEvidenceId")] string? ExitFaceEvidenceId,
    [property: JsonPropertyName("entryVehicleImageBase64")] string EntryVehicleImageBase64,
    [property: JsonPropertyName("exitVehicleImageBase64")] string ExitVehicleImageBase64,
    [property: JsonPropertyName("entryFaceImageBase64")] string EntryFaceImageBase64,
    [property: JsonPropertyName("exitFaceImageBase64")] string ExitFaceImageBase64);
