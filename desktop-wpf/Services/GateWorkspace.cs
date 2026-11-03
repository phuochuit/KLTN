using System.ComponentModel;
using ParkingGateDesktop;

namespace LumiPark.Desktop.Services;

public enum GateDirection { Entry, Exit }

public sealed record GateConfirmationTicket(long Revision, ParkingRequest Request, decimal Fee);
public sealed record GateTransactionConfirmed(ParkingResponse Response, bool ManualOverride, GateDirection Direction);

/// <summary>Headless operation state for the gate workspace. UI composition belongs to MainWindow.</summary>
public sealed class GateWorkspace : INotifyPropertyChanged
{
    private readonly ParkingApiClient client;
    private readonly MutationSubmissionCoordinator mutations;
    private readonly object sync = new();
    private GateDirection direction;
    private long revision, vehicleTicket, faceTicket, lookupTicket, previewTicket;
    private string plate = "", cardCode = "", vehicleType = "CAR", vehicleEvidenceId = "", faceEvidenceId = "";
    private string faceBase64 = "", reasonCode = "", reasonText = "";
    private string status = "Sẵn sàng";
    private double? faceSimilarity;
    private long? memberId, verifiedMemberId;
    private bool faceVerified, manual, writePending, knownResult;
    private ResidentLookupResponse? lookup;
    private ParkingResponse? preview;
    private GateConfirmationTicket? confirmation;
    private MutationHold hold = new(true, "Trạng thái thao tác chưa được kiểm tra.");
    private bool initialized;
    private bool invalidated;

    public GateWorkspace(ParkingApiClient client, MutationSubmissionCoordinator mutations)
    { this.client = client ?? throw new ArgumentNullException(nameof(client)); this.mutations = mutations ?? throw new ArgumentNullException(nameof(mutations)); }
    public GateWorkspace(AuthSession session) : this(
        (session ?? throw new ArgumentNullException(nameof(session))).ActiveServices
            ?? throw new InvalidOperationException("Đăng nhập trạm gác trước khi mở khu vực thao tác.")) { }
    private GateWorkspace(AuthServices services) : this(services.Client, services.Mutations) { }

    public event PropertyChangedEventHandler? PropertyChanged;
    public event EventHandler<GateTransactionConfirmed>? TransactionConfirmed;
    public GateDirection Direction => direction;
    public long Revision => revision;
    public string Plate { get => plate; set { if (Blocked() || plate == value) return; plate = value ?? ""; vehicleEvidenceId = faceBase64 = ""; ContextChanged(lookup: true); Changed(); } }
    public string CardCode { get => cardCode; set { if (Blocked() || cardCode == value) return; cardCode = value ?? ""; ContextChanged(lookup: true); Changed(); } }
    public string VehicleType { get => vehicleType; set { if (Blocked() || vehicleType == value) return; vehicleType = value ?? "CAR"; ContextChanged(); Changed(); } }
    public string VehicleEvidenceId => vehicleEvidenceId;
    public string FaceEvidenceId => faceEvidenceId;
    public bool CanVerifyFaceFile =>
        (memberId is long id && lookup?.AuthorizedMembers.Any(member => member.Id == id && member.FaceImageAvailable) == true)
        || (direction == GateDirection.Exit && lookup?.GuestEntryFaceAvailable == true && !string.IsNullOrWhiteSpace(vehicleEvidenceId));
    public string FaceFileUnavailableReason => lookup is null ? "Tra cứu thông tin trước khi chọn tệp khuôn mặt."
        : direction == GateDirection.Entry && !lookup.Registered ? "Khách xe vào: dùng camera để ghi nhận ảnh; không hỗ trợ tải tệp xác minh."
        : direction == GateDirection.Exit && !lookup.GuestEntryFaceAvailable && lookup.AuthorizedMembers?.All(member => member.Id != memberId || !member.FaceImageAvailable) == true
            ? "Chưa có ảnh khuôn mặt lúc vào hoặc ảnh thành viên để đối chiếu."
        : memberId is null ? "Chọn thành viên có ảnh khuôn mặt để tiếp tục."
        : "Thành viên được chọn chưa có ảnh khuôn mặt.";
    public string FaceBase64 => faceBase64;
    public string Status { get => status; private set { status = value; Changed(); } }
    public long? MemberId { get => memberId; set { if (Blocked() || memberId == value) return; memberId = value; ContextChanged(); Changed(); } }
    public bool FaceVerified => faceVerified && (memberId is null ? verifiedMemberId is null && lookup?.Registered != true : memberId == verifiedMemberId);
    public bool ManualOverride { get => manual; set { if (Blocked()) return; manual = value; ContextChanged(); Changed(); } }
    public string ManualReasonCode { get => reasonCode; set { if (Blocked()) return; reasonCode = value ?? ""; ContextChanged(); Changed(); } }
    public string ManualReasonText { get => reasonText; set { if (Blocked()) return; reasonText = value ?? ""; ContextChanged(); Changed(); } }
    public bool WritePending => writePending;
    public bool IsHeld => hold.IsHeld;
    public bool IsInitialized => initialized;
    public string HoldReason => hold.Reason;
    public ResidentLookupResponse? Lookup => lookup;
    public string LookupSummary => lookup is null ? "Chưa tra cứu" :
        $"{(lookup.Registered ? $"Chủ xe: {lookup.OwnerName}; căn hộ: {lookup.ApartmentNumber}; điện thoại: {lookup.OwnerPhone}" : "Khách vãng lai")}; {PackageSummary(lookup)}. {lookup.Message}";
    public string PackageWarning => lookup is { Registered: true, PassType: "MONTHLY" or "DAILY", PackageValid: false }
        ? "Gói đã hết hiệu lực; hệ thống sẽ tính khoản phí áp dụng theo quy định." : "";
    public ParkingResponse? Preview => preview;
    public bool CanLeaveWorkspace => !writePending;
    public bool CanNextContext => !writePending && !hold.IsHeld && knownResult;

    public void Invalidate()
    {
        invalidated = true; revision++; vehicleTicket++; faceTicket++; lookupTicket++; previewTicket++;
        PropertyChanged = null; TransactionConfirmed = null;
    }

    public void ApplyCoordinatorHold(MutationHold value)
    {
        if (invalidated) return;
        hold = value;
        if (value.IsHeld) Status = "Cần kiểm tra trạng thái thao tác trước đó trước khi tiếp tục.";
        Changed();
    }

    public async Task InitializeAsync()
    {
        if (initialized) return;
        hold = await mutations.InspectAsync(); initialized = true;
        if (hold.IsHeld) Status = "Cần kiểm tra thao tác trước đó trước khi tiếp tục.";
        Changed();
    }

    public void SetDirection(GateDirection value)
    {
        if (Blocked() || direction == value) return;
        direction = value; revision++; vehicleTicket++; faceTicket++; lookupTicket++; previewTicket++;
        plate = cardCode = ""; vehicleType = "CAR"; vehicleEvidenceId = faceEvidenceId = faceBase64 = "";
        memberId = verifiedMemberId = null; faceVerified = manual = false; reasonCode = reasonText = "";
        faceSimilarity = null; lookup = null; preview = null; confirmation = null; knownResult = false; status = "Sẵn sàng"; Changed();
    }

    public void SetPlate(string value) => Plate = value;
    public void SetMember(long? id) => MemberId = id;

    public async Task<ResidentLookupResponse?> LookupAsync(CancellationToken cancellationToken = default)
    {
        if (Blocked() || string.IsNullOrWhiteSpace(plate)) return null;
        var context = Capture(); var ticket = Interlocked.Increment(ref lookupTicket);
        try
        {
            var result = await client.LookupResidentAsync(context.Plate, Normalize(string.IsNullOrWhiteSpace(cardCode) ? context.Plate : cardCode));
            lock (sync)
            {
                if (Blocked() || ticket != lookupTicket || !SameLookup(context)) return null;
                bool removedMember = memberId is not null && (result.AuthorizedMembers is null || result.AuthorizedMembers.All(m => m.Id != memberId));
                lookup = result with { AuthorizedMembers = result.AuthorizedMembers?.ToList() ?? [] };
                if (removedMember) { memberId = null; ClearVerification(keepCapture: true); }
                else if (lookup is { Registered: true } && memberId is null) ClearVerification(keepCapture: true);
                InvalidatePreview();
            }
            Changed(); return lookup;
        }
        catch { if (ticket == lookupTicket) Status = "Tra cứu chưa hoàn tất. Vui lòng thử lại."; throw; }
    }

    public async Task<AnprResponse?> RecognizeImageAsync(string path) { var operation = Operation(); return await RecognizeAsync(() => Task.Run(() => client.RecognizeImageAsync(path, operation))); }
    public async Task<AnprResponse?> RecognizeVideoAsync(string path) { var operation = Operation(); return await RecognizeAsync(() => Task.Run(() => client.RecognizeVideoAsync(path, operation))); }
    public async Task<AnprResponse?> RecognizeVehicleCameraAsync() { var operation = Operation(); return await RecognizeAsync(() => Task.Run(() => client.RecognizeCameraAsync(operation))); }

    private async Task<AnprResponse?> RecognizeAsync(Func<Task<AnprResponse>> request)
    {
        if (Blocked()) return null;
        var context = Capture(); var ticket = Interlocked.Increment(ref vehicleTicket);
        var result = await request();
        lock (sync)
        {
            if (Blocked() || ticket != vehicleTicket || context.Revision != revision || context.Direction != direction) return null;
            plate = result.PlateText ?? ""; vehicleType = result.VehicleType ?? "CAR"; vehicleEvidenceId = result.EvidenceId ?? "";
            revision++; lookupTicket++; faceTicket++; ClearVerification(); lookup = null; InvalidatePreview();
        }
        Changed(); return result;
    }

    public async Task<FaceVerificationResponse?> VerifyFaceCameraAsync()
    {
        if (Blocked()) return null;
        var captured = Capture();
        var operation = captured.Direction == GateDirection.Entry ? "ENTRY" : "EXIT";
        if (memberId is long id && lookup?.AuthorizedMembers.Any(m => m.Id == id && m.FaceImageAvailable) == true)
            return await ApplyFaceAsync(() => client.VerifyCameraAsync(id, operation), captured, id);
        if (direction == GateDirection.Exit && lookup?.GuestEntryFaceAvailable == true && !string.IsNullOrWhiteSpace(vehicleEvidenceId))
            return await ApplyFaceAsync(() => client.VerifyGuestCameraAsync(vehicleEvidenceId), captured, null);
        if (direction == GateDirection.Entry)
        {
            if (Blocked()) return null;
            var ticket = Interlocked.Increment(ref faceTicket);
            var response = await Task.Run(() => client.CaptureCameraAsync("ENTRY"));
            if (Blocked() || ticket != faceTicket || !StillCurrent(captured)) return null;
            return ApplyFace(response, captured, ticket, null, false) ? response : null;
        }
        throw new InvalidOperationException("Chưa có ảnh khuôn mặt lúc xe vào để đối chiếu.");
    }

    public async Task<FaceVerificationResponse?> VerifyFaceFileAsync(string path, bool video)
    {
        if (Blocked()) return null;
        var captured = Capture(); var operation = captured.Direction == GateDirection.Entry ? "ENTRY" : "EXIT";
        var evidenceId = vehicleEvidenceId;
        if (memberId is long id && lookup?.AuthorizedMembers.Any(m => m.Id == id && m.FaceImageAvailable) == true)
            return await ApplyFaceAsync(() => video ? client.VerifyFaceVideoAsync(id, path, operation) : client.VerifyFaceImageAsync(id, path, operation), captured, id);
        if (captured.Direction == GateDirection.Exit && lookup?.GuestEntryFaceAvailable == true && !string.IsNullOrWhiteSpace(evidenceId))
            return await ApplyFaceAsync(() => video ? client.VerifyGuestFaceVideoAsync(evidenceId, path) : client.VerifyGuestFaceImageAsync(evidenceId, path), captured, null);
        throw new InvalidOperationException("Tệp khuôn mặt chỉ dùng cho thành viên đã tra cứu hoặc khách ra có ảnh lúc vào.");
    }

    public async Task<(AnprResponse Vehicle, FaceVerificationResponse Face)?> RecognizeAndCaptureAsync(Func<Task<AnprResponse>> vehicleRequest)
    {
        if (Blocked()) return null;
        var context = Capture(); var vt = Interlocked.Increment(ref vehicleTicket); var ft = Interlocked.Increment(ref faceTicket);
        var operation = context.Direction == GateDirection.Entry ? "ENTRY" : "EXIT";
        var vehicleTask = Task.Run(vehicleRequest);
        var faceTask = Task.Run(() => client.CaptureCameraAsync(operation));
        await Task.WhenAll(vehicleTask, faceTask);
        lock (sync)
        {
            if (Blocked() || knownResult || vt != vehicleTicket || ft != faceTicket || context.Revision != revision || context.Direction != direction) return null;
            plate = vehicleTask.Result.PlateText ?? ""; vehicleType = vehicleTask.Result.VehicleType ?? "CAR";
            vehicleEvidenceId = vehicleTask.Result.EvidenceId ?? ""; faceBase64 = faceTask.Result.RealtimeImageBase64 ?? "";
            faceEvidenceId = faceTask.Result.EvidenceId ?? ""; faceVerified = false; verifiedMemberId = null;
            revision++; lookupTicket++; InvalidatePreview();
        }
        Changed();
        var continuation = Capture(); var lookupTicketAtContinuation = lookupTicket + 1;
        if (Blocked() || vt != vehicleTicket || context.Revision + 1 != continuation.Revision || continuation.Direction != context.Direction) return null;
        var lookupResult = await LookupAsync();
        if (Blocked() || vt != vehicleTicket || lookupTicketAtContinuation != lookupTicket || lookupResult is null || !StillCurrent(continuation)
            || Normalize(vehicleTask.Result.PlateText) != Normalize(plate)) return null;
        var selected = lookupResult.AuthorizedMembers.FirstOrDefault(m => m.FaceImageAvailable);
        if (selected is not null)
        {
            if (Blocked() || vt != vehicleTicket || lookupTicketAtContinuation != lookupTicket || !StillCurrent(continuation)) return null;
            memberId = selected.Id;
            var verificationContext = Capture();
            await VerifyCapturedAsync(selected.Id, vehicleEvidenceId, faceTask.Result.RealtimeImageBase64 ?? "", verificationContext);
        }
        else if (continuation.Direction == GateDirection.Exit && lookupResult.GuestEntryFaceAvailable)
        {
            if (Blocked() || vt != vehicleTicket || lookupTicketAtContinuation != lookupTicket || !StillCurrent(continuation)) return null;
            await VerifyCapturedAsync(null, vehicleEvidenceId, faceTask.Result.RealtimeImageBase64 ?? "", Capture());
        }
        return (vehicleTask.Result, faceTask.Result);
    }

    private async Task<FaceVerificationResponse?> VerifyCapturedAsync(long? id, string capturedVehicleId, string capturedFace, Context context)
    {
        if (Blocked() || !StillCurrent(context) || vehicleEvidenceId != capturedVehicleId) return null;
        var ticket = Interlocked.Increment(ref faceTicket);
        var operation = context.Direction == GateDirection.Entry ? "ENTRY" : "EXIT";
        Func<Task<FaceVerificationResponse>> request = id is long member
            ? () => client.VerifyCapturedFaceAsync(member, capturedFace, operation)
            : () => client.VerifyCapturedGuestFaceAsync(capturedVehicleId, capturedFace);
        var result = await Task.Run(request);
        if (Blocked() || !StillCurrent(context) || vehicleEvidenceId != capturedVehicleId) return null;
        return ApplyFace(result, context, ticket, id, true) ? result : null;
    }

    private async Task<FaceVerificationResponse?> ApplyFaceAsync(Func<Task<FaceVerificationResponse>> request, Context context, long? id)
    {
        if (Blocked()) return null;
        var ticket = Interlocked.Increment(ref faceTicket); var result = await Task.Run(request);
        return ApplyFace(result, context, ticket, id, true) ? result : null;
    }

    private bool ApplyFace(FaceVerificationResponse result, Context context, long ticket, long? id, bool verified)
    {
        lock (sync)
        {
            if (Blocked() || ticket != faceTicket || context.Revision != revision || context.Direction != direction || context.MemberId != memberId) return false;
            faceBase64 = result.RealtimeImageBase64 ?? ""; faceEvidenceId = result.EvidenceId ?? "";
            faceVerified = verified && result.Decision == "PASS"; verifiedMemberId = faceVerified ? id : null;
            faceSimilarity = faceVerified ? result.Similarity : null;
            InvalidatePreview();
        }
        Changed(); return true;
    }

    public async Task<ParkingResponse?> PreviewExitAsync()
    {
        if (direction != GateDirection.Exit || Blocked()) throw new InvalidOperationException("Chỉ có thể xem trước phí trong luồng xe ra.");
        var context = Capture(); var ticket = Interlocked.Increment(ref previewTicket);
        var result = await client.PreviewExitAsync(BuildRequest(null, null));
        lock (sync)
        {
            if (Blocked() || knownResult || ticket != previewTicket || context.Revision != revision || context.Direction != direction || result.SessionId <= 0 || result.EntryTime == default || result.Status != "PREVIEW" || Normalize(result.PlateNumber) != Normalize(context.Plate) || result.Fee < 0) return null;
            preview = result; confirmation = null;
        }
        Changed(); return result;
    }

    public GateConfirmationTicket CreateConfirmationTicket(string? paymentMethod = null, string? reference = null)
    {
        if (direction != GateDirection.Exit || preview is null || Normalize(preview.PlateNumber) != Normalize(plate) || preview.Fee < 0)
            throw new InvalidOperationException("Hãy xem trước lại phí cho biển số hiện tại.");
        if (preview.Fee > 0 && paymentMethod is not ("CASH" or "BANK_TRANSFER" or "POS")) throw new InvalidOperationException("Hãy chọn phương thức thanh toán.");
        if (preview.Fee > 0 && paymentMethod != "CASH" && string.IsNullOrWhiteSpace(reference)) throw new InvalidOperationException("Cần mã tham chiếu thanh toán.");
        var request = BuildRequest(preview.Fee > 0 ? paymentMethod : null,
            preview.Fee > 0 && paymentMethod != "CASH" ? reference : null);
        return confirmation = new GateConfirmationTicket(revision, request, preview.Fee);
    }

    public async Task<SingleAttemptResult> SubmitEntryAsync()
    {
        if (direction != GateDirection.Entry) throw new InvalidOperationException("Chọn luồng xe vào.");
        return await SubmitAsync(MutationKind.Entry, BuildRequest(null, null));
    }

    public async Task<SingleAttemptResult> ConfirmExitAsync(GateConfirmationTicket ticket)
    {
        if (direction != GateDirection.Exit || confirmation != ticket || ticket.Revision != revision || preview is null)
            throw new InvalidOperationException("Phiên xác nhận đã cũ. Hãy xem trước lại phí.");
        confirmation = null;
        return await SubmitAsync(MutationKind.ExitConfirm, ticket.Request);
    }

    private async Task<SingleAttemptResult> SubmitAsync(MutationKind kind, ParkingRequest request)
    {
        if (writePending || knownResult) return new(SingleAttemptOutcome.NotSent, Message: "Thao tác đã hoàn tất hoặc đang xử lý.");
        writePending = true; knownResult = false; Changed();
        try
        {
            hold = await mutations.InspectAsync(); if (hold.IsHeld) { Status = "Cần kiểm tra thao tác trước đó trước khi tiếp tục."; return new(SingleAttemptOutcome.NotSent, Message: hold.Reason); }
            var result = await mutations.SubmitAsync(kind, request);
            hold = await mutations.InspectAsync();
            if (result.Outcome == SingleAttemptOutcome.Unknown) { hold = new(true, "Kết quả chưa xác định; cần kiểm tra trước khi tiếp tục."); Status = "Chưa xác định kết quả. Không gửi lại thao tác; cần kiểm tra trạng thái."; }
            else if (result.Outcome == SingleAttemptOutcome.Success && result.Response is { } response)
            { knownResult = true; TransactionConfirmed?.Invoke(this, new(response, manual, direction)); Status = response.Warning ? response.Message : "Giao dịch đã được xác nhận."; }
            else if (result.Outcome == SingleAttemptOutcome.Rejected) { knownResult = false; Status = result.Message ?? "Thao tác bị từ chối. Có thể chỉnh sửa rồi thử lại."; }
            else Status = "Chưa gửi thao tác. Kiểm tra kết nối và trạng thái.";
            return result;
        }
        finally { writePending = false; Changed(); }
    }

    public void ApplyCorrection(string reasonCode, string reasonText, string canonicalVehicleType)
        => ApplyCorrection(plate, reasonCode, reasonText, canonicalVehicleType);

    public void ApplyCorrection(string correctedPlate, string reasonCode, string reasonText, string canonicalVehicleType)
    {
        if (Blocked()) return;
        if (string.IsNullOrWhiteSpace(vehicleEvidenceId)) throw new InvalidOperationException("Cần bằng chứng xe trước khi hiệu chỉnh.");
        if (reasonCode is not ("PLATE_NOT_DETECTED" or "PLATE_CORRECTION" or "VEHICLE_TYPE_CORRECTION" or "FACE_REVIEW" or "FACE_FAILED" or "REGISTRATION_IMAGE_MISSING")
            || string.IsNullOrWhiteSpace(reasonText) || reasonText.Trim().Length is < 10 or > 500)
            throw new ArgumentException("Chọn mã lý do hợp lệ và nhập nội dung từ 10 đến 500 ký tự.");
        if (canonicalVehicleType is not ("MOTORBIKE" or "LARGE_MOTORBIKE" or "CAR" or "CAR_6_7" or "CAR_8_9"))
            throw new ArgumentException("Chọn một trong các loại xe được hỗ trợ.");
        bool plateChanged = !string.IsNullOrWhiteSpace(correctedPlate) && Normalize(correctedPlate) != Normalize(plate);
        if (plateChanged) { plate = correctedPlate; lookup = null; memberId = null; ClearVerification(keepCapture: true); lookupTicket++; faceTicket++; }
        manual = true; reasonText = reasonText ?? ""; vehicleType = canonicalVehicleType ?? vehicleType;
        this.reasonCode = reasonCode; this.reasonText = reasonText; revision++; InvalidatePreview(); Changed();
    }

    public bool NextContext()
    {
        if (!CanNextContext) return false;
        plate = cardCode = vehicleEvidenceId = faceEvidenceId = faceBase64 = reasonCode = reasonText = "";
        memberId = verifiedMemberId = null; faceVerified = manual = false; lookup = null; preview = null; confirmation = null;
        knownResult = false; status = "Sẵn sàng"; revision++; vehicleTicket++; faceTicket++; lookupTicket++; previewTicket++; Changed(); return true;
    }

    private ParkingRequest BuildRequest(string? payment, string? reference) => new(plate, Normalize(string.IsNullOrWhiteSpace(cardCode) ? plate : cardCode), vehicleType, manual,
        memberId, FaceVerified, FaceVerified ? faceSimilarity : null, direction == GateDirection.Entry ? faceBase64 : "",
        vehicleEvidenceId, faceEvidenceId, reasonCode, reasonText, payment, reference);
    private Context Capture() => new(revision, direction, plate, memberId);
    private bool SameLookup(Context c) => c.Direction == direction && Normalize(c.Plate) == Normalize(plate) && c.Revision == revision;
    private bool StillCurrent(Context c) => c.Revision == revision && c.Direction == direction && c.MemberId == memberId;
    private void ContextChanged(bool lookup = false)
    { revision++; faceTicket++; if (lookup) { lookupTicket++; this.lookup = null; memberId = null; } faceVerified = false; verifiedMemberId = null; faceSimilarity = null; faceEvidenceId = ""; InvalidatePreview(); }
    private void ClearVerification(bool keepCapture = false) { faceVerified = false; verifiedMemberId = null; if (!keepCapture) faceBase64 = ""; faceEvidenceId = ""; faceSimilarity = null; }
    private void InvalidatePreview() { preview = null; confirmation = null; previewTicket++; }
    private bool Blocked() => invalidated || !initialized || writePending || hold.IsHeld || knownResult;
    private string Operation() => direction == GateDirection.Entry ? "ENTRY" : "EXIT";
    private static string Normalize(string? value) => new((value ?? "").ToUpperInvariant().Where(char.IsLetterOrDigit).ToArray());
    private static string PackageSummary(ResidentLookupResponse value) => value.PassType switch
    {
        "MONTHLY" => value.PackageValid ? $"Gói tháng, hạn {value.ValidUntil:dd/MM/yyyy}" : "Gói tháng hết hiệu lực",
        "DAILY" => value.PackageValid ? $"Gói ngày, hạn {value.ValidUntil:dd/MM/yyyy}" : "Gói ngày hết hiệu lực",
        "PER_VISIT" => "Lượt (dữ liệu cũ)",
        _ => "Chưa có gói"
    };
    private void Changed() => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(null));
    private readonly record struct Context(long Revision, GateDirection Direction, string Plate, long? MemberId);
}
