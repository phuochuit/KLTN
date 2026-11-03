using System.Net;
using System.Net.Http;
using System.Net.Http.Json;
using System.IO;
using System.Text.Json;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

namespace LumiPark.Desktop.Services;

// Explicit in-memory transport: every route is handled here and none is forwarded.
public sealed class DemoTransport : HttpMessageHandler
{
    private readonly List<string> paths = [];
    public bool SessionValid { get; set; } = true;
    public int AccessTokenLifetimeSeconds { get; set; } = 900;
    public HttpStatusCode? RefreshStatus { get; set; }
    public bool MalformedRefresh { get; set; }
    public Task? LogoutWait { get; set; }
    public Task? ShiftSummaryWait { get; set; }
    public TaskCompletionSource ShiftSummaryRequestStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
    public bool RegisteredMember { get; set; } = true;
    public bool GuestEntryFaceAvailable { get; set; } = true;
    public bool ExpiredPass { get; set; }
    public bool FaceReview { get; set; }
    public bool SlowResponses { get; set; }
    public bool ErrorResponses { get; set; }
    public bool UnknownMutation { get; set; }
    public int MutationCount { get; private set; }
    public int DispatchCount { get; private set; }
    public int RequestCount => paths.Count;
    public IReadOnlyList<string> Paths => paths;
    public string? LastPayload { get; private set; }
    private static object Shift() => new { id = 1, name = "Ca minh họa", startsAt = DateTimeOffset.UtcNow.AddHours(-1), endsAt = DateTimeOffset.UtcNow.AddHours(7), duty = "Trực cổng (minh họa)" };
    protected override void Dispose(bool disposing) { }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        var path = request.RequestUri?.AbsolutePath ?? ""; paths.Add(path); string? payload = null;
        if (request.Content?.Headers.ContentType?.MediaType == "application/json")
            payload = await request.Content.ReadAsStringAsync(cancellationToken);
        LastPayload = payload;
        if (SlowResponses) await Task.Delay(TimeSpan.FromSeconds(2), cancellationToken);
        if (ErrorResponses && path.StartsWith("/api/ai/", StringComparison.Ordinal)) return new(HttpStatusCode.ServiceUnavailable);
        if (request.Method == HttpMethod.Post && path == "/api/auth/login")
        {
            using var body = JsonDocument.Parse(payload ?? "{}");
            if (!body.RootElement.TryGetProperty("username", out var username) || username.GetString() != "demo" || !body.RootElement.TryGetProperty("password", out var password) || password.GetString() != "demo") return new(HttpStatusCode.Unauthorized);
            SessionValid = true;
            return Json(new { accessToken = "demo-access", tokenType = "Bearer", expiresIn = AccessTokenLifetimeSeconds, refreshToken = "demo-refresh", sessionId = "demo-session", absoluteExpiresAt = DateTimeOffset.UtcNow.AddHours(1), username = "demo", fullName = "Nhân viên minh họa", activeShift = Shift() });
        }
        if (path == "/api/auth/session" && request.Method == HttpMethod.Get)
            return SessionValid && request.Headers.Authorization?.Parameter == "demo-access" ? Json(new { username = "demo", fullName = "Nhân viên minh họa", activeShift = Shift() }) : new(HttpStatusCode.Unauthorized);
        if (path == "/api/auth/logout" && request.Method == HttpMethod.Post)
        { if (LogoutWait is not null) await LogoutWait.WaitAsync(cancellationToken); SessionValid = false; return new(HttpStatusCode.NoContent); }
        if (path == "/api/auth/refresh" && request.Method == HttpMethod.Post)
            return MalformedRefresh ? Json(new { invalid = true }) : new(RefreshStatus ?? HttpStatusCode.Unauthorized);
        if (!SessionValid || request.Headers.Authorization?.Parameter != "demo-access") return new(HttpStatusCode.Unauthorized);
        if (path == "/api/auth/shift-summary" && request.Method == HttpMethod.Get)
        {
            ShiftSummaryRequestStarted.TrySetResult();
            if (ShiftSummaryWait is not null) await ShiftSummaryWait.WaitAsync(cancellationToken);
            bool previous = request.RequestUri?.Query.Contains("scope=previous", StringComparison.OrdinalIgnoreCase) == true;
            return Json(new { shiftId = previous ? 7000L : 7001L, shiftName = previous ? "Ca minh họa trước" : "Ca minh họa", operatorName = "Nhân viên minh họa",
                startsAt = DateTimeOffset.UtcNow.AddHours(-1), endsAt = DateTimeOffset.UtcNow.AddHours(7), summarizedAt = DateTimeOffset.UtcNow,
                duty = "Trực cổng · dữ liệu minh họa", totalRevenue = 25000m, cashRevenue = 25000m, bankTransferRevenue = 0m, posRevenue = 0m,
                vehicleEntries = 1L, vehicleExits = 0L, vehiclesCurrentlyInside = 1L, occupiedSlots = 1L, availableSlots = 4L,
                pendingReconciliation = 0L, manualOverrides = 0L });
        }
        if (path == "/api/parking/health" && request.Method == HttpMethod.Get)
            return Json(new { status = "UP", service = "parking-web", version = "DEMO", database = "Dữ liệu minh họa" });
        if (path == "/api/ai/health" && request.Method == HttpMethod.Get)
            return Json(new { status = ErrorResponses ? "DOWN" : "UP", service = "anpr-demo" });
        if (path == "/api/parking/slots" && request.Method == HttpMethod.Get)
            return Json(new[] {
                new { id = 8101L, slotCode = "A-01", zoneName = "Khu A", floor = "Tầng B1", slotType = "VISITOR", allowedVehicleType = "CAR_6_7", statusOverride = "NORMAL", assignedPlate = (string?)null, assignedOwnerName = (string?)null, assignedOwnerPhone = (string?)null, assignedApartment = (string?)null, occupiedPlate = (string?)null, occupiedEntryTime = (DateTime?)null, borrowedPlate = (string?)null, borrowedUntil = (DateTime?)null, borrowNotes = (string?)null, overdue = false, status = "AVAILABLE", statusDescription = "Còn trống · dữ liệu minh họa" },
                new { id = 8102L, slotCode = "A-02", zoneName = "Khu A", floor = "Tầng B1", slotType = "VISITOR", allowedVehicleType = "MOTORBIKE", statusOverride = "NORMAL", assignedPlate = (string?)null, assignedOwnerName = (string?)null, assignedOwnerPhone = (string?)null, assignedApartment = (string?)null, occupiedPlate = (string?)"DEMO-02", occupiedEntryTime = (DateTime?)DateTime.Today.AddHours(8), borrowedPlate = (string?)null, borrowedUntil = (DateTime?)null, borrowNotes = (string?)null, overdue = false, status = "OCCUPIED_OPEN", statusDescription = "Đang đỗ · dữ liệu minh họa" },
                new { id = 8103L, slotCode = "B-01", zoneName = "Khu B", floor = "Tầng B2", slotType = "VISITOR", allowedVehicleType = "BICYCLE_ELECTRIC_BICYCLE", statusOverride = "NORMAL", assignedPlate = (string?)null, assignedOwnerName = (string?)null, assignedOwnerPhone = (string?)null, assignedApartment = (string?)null, occupiedPlate = (string?)null, occupiedEntryTime = (DateTime?)null, borrowedPlate = (string?)null, borrowedUntil = (DateTime?)null, borrowNotes = (string?)null, overdue = false, status = "AVAILABLE", statusDescription = "Còn trống · dữ liệu minh họa" }
            });
        if (path == "/api/parking/recent-unassigned" && request.Method == HttpMethod.Get)
            return Json(new[] { new { sessionId = 8901L, plateNumber = "TEST-01", vehicleType = "CAR", entryTime = DateTime.Today.AddHours(9).ToString("O"), ownerName = "Khách minh họa" } });
        if (path.StartsWith("/api/parking/slots/", StringComparison.Ordinal) && path.EndsWith("/dispatch-session", StringComparison.Ordinal) && request.Method == HttpMethod.Post)
        {
            using var dispatchBody = JsonDocument.Parse(payload ?? "{}");
            if (!dispatchBody.RootElement.TryGetProperty("sessionId", out var sessionId) || sessionId.GetInt64() != 8901L)
                return Json(new { message = "Không tìm thấy lượt xe minh họa." });
            DispatchCount++;
            return UnknownMutation ? new(HttpStatusCode.ServiceUnavailable) : new(HttpStatusCode.NoContent);
        }
        if (path == "/api/parking/evidence-comparison" && request.Method == HttpMethod.Get)
        {
            var image = SyntheticImage();
            return Json(new { sessionId = 7001L, sessionStatus = "COMPLETED", entryTime = DateTime.UtcNow.AddHours(-2), exitTime = DateTime.UtcNow,
                entryPlate = "TEST-01", exitPlate = "TEST-01", plateMatched = true, entryVehicleType = "CAR", exitVehicleType = "CAR", vehicleTypeMatched = true,
                entryDetectionConfidence = 0d, entryOcrConfidence = 0d, entryVehicleConfidence = 0d, exitDetectionConfidence = 0d, exitOcrConfidence = 0d, exitVehicleConfidence = 0d,
                entryFaceDecision = "NOT_EVALUATED", entryFaceSimilarity = (double?)null, exitFaceDecision = "NOT_EVALUATED", exitFaceSimilarity = (double?)null,
                faceMatched = false, manualOverride = false, matchStatus = "REVIEW", summaryMessage = "Dữ liệu đối chiếu minh họa.",
                entryVehicleEvidenceId = "demo-entry-vehicle", exitVehicleEvidenceId = "demo-exit-vehicle", entryFaceEvidenceId = "demo-entry-face", exitFaceEvidenceId = "demo-exit-face",
                entryVehicleImageBase64 = image, exitVehicleImageBase64 = image, entryFaceImageBase64 = image, exitFaceImageBase64 = image });
        }
        if (path == "/api/parking/lookup" && request.Method == HttpMethod.Get)
        {
            var query = request.RequestUri?.Query.TrimStart('?').Split('&').FirstOrDefault(x => x.StartsWith("plate=", StringComparison.Ordinal));
            var plate = query is null ? "TEST-01" : Uri.UnescapeDataString(query[6..].Replace('+', ' '));
            var members = RegisteredMember ? new[] { new { id = 7L, fullName = "Minh họa · Nguyễn An", relationship = "Chủ xe", faceImageAvailable = true } } : Array.Empty<object>();
            return Json(new { registered = RegisteredMember, cardMatched = true, plateNumber = plate, ownerName = RegisteredMember ? "Nguyễn An (minh họa)" : "", ownerPhone = RegisteredMember ? "0900 000 000" : "", apartmentNumber = RegisteredMember ? "A-TEST" : "", vehicleType = "CAR", cardCode = plate, passType = RegisteredMember ? "MONTHLY" : "", validUntil = (DateTime?)DateTime.Today.AddDays(ExpiredPass ? -1 : 20), packageValid = RegisteredMember && !ExpiredPass, message = "Dữ liệu trình diễn — không phải hồ sơ thật.", authorizedMembers = members, guestEntryFaceAvailable = GuestEntryFaceAvailable });
        }
        if (path is "/api/ai/recognize/image" or "/api/ai/recognize/video" or "/api/ai/recognize/camera")
            return Json(new { plateText = "TEST-01", vehicleType = "CAR", detectionConfidence = 0d, ocrConfidence = 0d, vehicleConfidence = 0d, boundingBox = (object?)null, frameIndex = 0, annotatedImageBase64 = SyntheticImage(), message = "Ảnh xe minh họa", evidenceId = "demo-vehicle-evidence", processedAt = DateTimeOffset.UtcNow, modelVersion = "DEMO" });
        if (path.StartsWith("/api/ai/face/", StringComparison.Ordinal))
        {
            var capture = path.EndsWith("capture-camera", StringComparison.Ordinal);
            return Json(new { decision = capture ? "NOT_EVALUATED" : FaceReview ? "REVIEW" : "PASS", similarity = 0d, matchThreshold = 0d, message = "Kết quả khuôn mặt minh họa.", realtimeImageBase64 = SyntheticImage(), evidenceId = "demo-face-evidence", modelVersion = "DEMO" });
        }
        if (path == "/api/parking/exit-preview" && request.Method == HttpMethod.Post)
        {
            using var body = JsonDocument.Parse(payload ?? "{}"); var plate = body.RootElement.GetProperty("plateNumber").GetString() ?? "TEST-01";
            return Json(new { sessionId = 7001L, plateNumber = plate, ownerName = "Minh họa", vehicleType = "CAR", cardCode = "", status = "PREVIEW", entryTime = DateTime.Now.AddHours(-2), exitTime = DateTime.Now, fee = 25000m, message = "Phí chỉ mang tính minh họa.", warning = false });
        }
        if (path is "/api/parking/entry" or "/api/parking/exit-confirm")
        {
            MutationCount++; if (UnknownMutation) return new(HttpStatusCode.ServiceUnavailable);
            using var body = JsonDocument.Parse(payload ?? "{}"); var exit = path.EndsWith("exit-confirm", StringComparison.Ordinal); var plate = body.RootElement.GetProperty("plateNumber").GetString() ?? "TEST-01";
            return Json(new { sessionId = 7001L, plateNumber = plate, ownerName = "Minh họa", vehicleType = "CAR", cardCode = "", status = exit ? "COMPLETED" : "OPEN", entryTime = DateTime.Now.AddHours(-2), exitTime = exit ? DateTime.Now : (DateTime?)null, fee = exit ? 25000m : 0m, message = "Giao dịch minh họa đã xác nhận.", warning = false, receiptNumber = exit ? "DEMO-RECEIPT-01" : (string?)null, paymentMethod = exit ? "CASH" : null, paymentReference = (string?)null });
        }
        return new(HttpStatusCode.NotFound);
    }
    private static HttpResponseMessage Json<T>(T body) => new(HttpStatusCode.OK) { Content = JsonContent.Create(body) };
    private static readonly Lazy<string> EvidenceImage = new(CreateSyntheticImage);
    private static string SyntheticImage() => EvidenceImage.Value;

    // GDI is already part of DesktopCore's Windows dependency. No WPF dispatcher is needed by the transport.
    private static string CreateSyntheticImage()
    {
        using var bitmap = new Bitmap(640, 360);
        using var graphics = Graphics.FromImage(bitmap);
        graphics.SmoothingMode = SmoothingMode.AntiAlias;
        using var background = new LinearGradientBrush(new Rectangle(0, 0, 640, 360), Color.FromArgb(38, 64, 112), Color.FromArgb(151, 174, 218), 90f);
        graphics.FillRectangle(background, 0, 0, 640, 360);
        using var car = new GraphicsPath();
        car.AddArc(118, 92, 48, 48, 180, 90); car.AddArc(474, 92, 48, 48, 270, 90);
        car.AddArc(474, 222, 48, 48, 0, 90); car.AddArc(118, 222, 48, 48, 90, 90); car.CloseFigure();
        using var body = new SolidBrush(Color.FromArgb(247, 249, 255));
        using var outline = new Pen(Color.FromArgb(66, 85, 128), 3);
        graphics.FillPath(body, car); graphics.DrawPath(outline, car);
        graphics.FillRectangle(Brushes.White, 200, 205, 240, 62);
        using var platePen = new Pen(Color.SlateGray, 2);
        graphics.DrawRectangle(platePen, 200, 205, 240, 62);
        using var plateFont = new Font("Arial", 36, FontStyle.Regular, GraphicsUnit.Pixel);
        using var captionFont = new Font("Arial", 16, FontStyle.Regular, GraphicsUnit.Pixel);
        graphics.DrawString("TEST-01", plateFont, Brushes.DarkBlue, 226, 215);
        graphics.DrawString("MINH HỌA · DỮ LIỆU GIẢ", captionFont, Brushes.White, 12, 12);
        using var stream = new MemoryStream();
        bitmap.Save(stream, ImageFormat.Png);
        return Convert.ToBase64String(stream.ToArray());
    }
}
