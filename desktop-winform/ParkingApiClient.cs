using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;

namespace ParkingGateDesktop;

public sealed class ParkingApiClient : IDisposable
{
    private readonly HttpClient _http;
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);
    private readonly SemaphoreSlim _refreshGate = new(1, 1);
    private readonly SemaphoreSlim _outboxGate = new(1, 1);
    private readonly OfflineOutboxStore _outbox;
    private readonly HashSet<long> _shownShiftSummaries = new();
    private string? _accessToken;
    private string? _refreshToken;
    private string? _authenticatedBaseUrl;
    private DateTimeOffset _accessExpiresAt;
    private DateTimeOffset _sessionExpiresAt;
    public OperatorProfile? CurrentOperator { get; private set; }
    public event Action<OperatorProfile>? OperatorProfileChanged;

    public ParkingApiClient() : this(new HttpClientHandler { AllowAutoRedirect = false }) { }

    internal ParkingApiClient(HttpMessageHandler handler)
    {
        _http = new HttpClient(handler) { Timeout = Timeout.InfiniteTimeSpan };
        _outbox = new OfflineOutboxStore();
    }

    public string BaseUrl { get; set; } = "http://localhost:8080";
    public bool IsAuthenticated => _accessToken is not null && _refreshToken is not null;
    public GateStationSettings StationSettings { get; set; } = new GateStationSettingsStore().Load();
    public int VehicleCameraIndex => StationSettings.VehicleCameraIndex;
    public int FaceCameraIndex => StationSettings.FaceCameraIndex;

    public async Task LoginAsync(string username, string password)
    {
        ClearAuthentication();
        string baseUrl = NormalizeBaseUrl(BaseUrl);
        using var request = new HttpRequestMessage(HttpMethod.Post, BuildUri(baseUrl, "/api/auth/login"))
        {
            Content = JsonContent.Create(new LoginRequest(username, password, "Parking desktop"), options: _json)
        };
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        using var response = await _http.SendAsync(request, timeout.Token);
        var tokens = await ApiResponseReader.ReadAsync<TokenResponse>(response, _json,
            "Spring Web", "đăng nhập trạm gác");
        InstallTokens(tokens, baseUrl);
    }

    public async Task LogoutAsync()
    {
        string? baseUrl = _authenticatedBaseUrl;
        try
        {
            if (baseUrl is null) return;
            string token = await GetFreshAccessTokenAsync(requireCurrentOrigin: false);
            using var request = new HttpRequestMessage(HttpMethod.Post, BuildUri(baseUrl, "/api/auth/logout"));
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
            using var response = await _http.SendAsync(request, timeout.Token);
            await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "đăng xuất");
        }
        finally { ClearAuthentication(); }
    }

    public async Task<bool> HealthAsync()
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        using var response = await _http.GetAsync(
            BuildUri(NormalizeBaseUrl(BaseUrl), "/api/parking/health"), timeout.Token);
        return response.IsSuccessStatusCode;
    }

    public async Task<OperatorProfile> ValidateSessionAsync()
    {
        using var response = await SendProtectedAsync(
            "/api/auth/session", HttpMethod.Get, retrySafeRead: true);
        if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
            throw new DesktopAuthenticationException(
                "Phiên làm việc đã được kết thúc. Vui lòng đăng nhập lại.");
        var profile = await ApiResponseReader.ReadAsync<OperatorProfile>(response, _json,
            "Spring Web", "xác minh phiên làm việc");
        UpdateOperator(profile);
        return profile;
    }

    public async Task<ShiftSummary?> GetShiftSummaryAsync(bool previous)
    {
        string scope = previous ? "previous" : "current";
        using var response = await SendProtectedAsync(
            $"/api/auth/shift-summary?scope={scope}", HttpMethod.Get, retrySafeRead: true);
        if (response.StatusCode == HttpStatusCode.NoContent) return null;
        return await ApiResponseReader.ReadAsync<ShiftSummary>(response, _json,
            "Spring Web", "tải tổng kết ca");
    }

    public async Task<int> GetOfflineQueueCountAsync() => (await _outbox.ReadAsync()).Count;

    public bool MarkShiftSummaryShown(long shiftId) => _shownShiftSummaries.Add(shiftId);

    public async Task<bool> AiHealthAsync()
    {
        using var response = await SendProtectedAsync(
            "/api/ai/health", HttpMethod.Get, retrySafeRead: true);
        if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
            throw new DesktopAuthenticationException(
                "Phiên làm việc đã được kết thúc. Vui lòng đăng nhập lại.");
        return response.IsSuccessStatusCode;
    }

    public Task<AnprResponse> RecognizeImageAsync(string path, string operation) => UploadAiAsync("/api/ai/recognize/image", path, operation);
    public Task<AnprResponse> RecognizeVideoAsync(string path, string operation) => UploadAiAsync("/api/ai/recognize/video", path, operation);

    public async Task<AnprResponse> RecognizeCameraAsync(string operation, int? cameraIndex = null, int? frameCount = null)
    {
        using var response = await SendProtectedAsync("/api/ai/recognize/camera", HttpMethod.Post,
            () => JsonContent.Create(new { cameraIndex = cameraIndex ?? VehicleCameraIndex, cameraSource = StationSettings.VehicleCameraSource, frameCount = frameCount ?? StationSettings.CameraFrameCount, operation }, options: _json),
            requestTimeout: TimeSpan.FromSeconds(30));
        return await ApiResponseReader.ReadAsync<AnprResponse>(response, _json,
            "Hệ thống nhận dạng", "chụp và nhận dạng biển số");
    }

    public async Task<FaceVerificationResponse> VerifyCameraAsync(long familyMemberId, string operation)
    {
        using var response = await SendProtectedAsync("/api/ai/face/verify-camera", HttpMethod.Post,
            () => JsonContent.Create(new { familyMemberId, cameraIndex = FaceCameraIndex, cameraSource = StationSettings.FaceCameraSource, operation }, options: _json),
            requestTimeout: TimeSpan.FromMinutes(2));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Hệ thống nhận dạng", "xác thực khuôn mặt");
    }

    public async Task<FaceVerificationResponse> VerifyGuestCameraAsync(string vehicleEvidenceId)
    {
        using var response = await SendProtectedAsync("/api/ai/face/guest/verify-camera", HttpMethod.Post,
            () => JsonContent.Create(new { vehicleEvidenceId, cameraIndex = FaceCameraIndex, cameraSource = StationSettings.FaceCameraSource }, options: _json),
            requestTimeout: TimeSpan.FromMinutes(2));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Hệ thống nhận dạng", "xác thực khuôn mặt khách vãng lai");
    }

    public async Task<FaceVerificationResponse> CaptureCameraAsync(string operation)
    {
        using var response = await SendProtectedAsync("/api/ai/face/capture-camera", HttpMethod.Post,
            () => JsonContent.Create(new { cameraIndex = FaceCameraIndex, cameraSource = StationSettings.FaceCameraSource, operation }, options: _json),
            requestTimeout: TimeSpan.FromMinutes(2));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Hệ thống nhận dạng", "chụp khuôn mặt");
    }

    public Task<FaceVerificationResponse> VerifyFaceImageAsync(long familyMemberId, string path, string operation) =>
        UploadFaceAsync("/api/ai/face/verify-image", familyMemberId, path, operation, image: true);

    public Task<FaceVerificationResponse> VerifyFaceVideoAsync(long familyMemberId, string path, string operation) =>
        UploadFaceAsync("/api/ai/face/verify-video", familyMemberId, path, operation, image: false);

    public Task<FaceVerificationResponse> VerifyGuestFaceImageAsync(string vehicleEvidenceId, string path) =>
        UploadGuestFaceAsync("/api/ai/face/guest/verify-image", vehicleEvidenceId, path, image: true);

    public Task<FaceVerificationResponse> VerifyGuestFaceVideoAsync(string vehicleEvidenceId, string path) =>
        UploadGuestFaceAsync("/api/ai/face/guest/verify-video", vehicleEvidenceId, path, image: false);

    public Task<FaceVerificationResponse> VerifyCapturedFaceAsync(long familyMemberId, string imageBase64, string operation)
    {
        int separator = imageBase64.IndexOf(',');
        string payload = separator >= 0 ? imageBase64[(separator + 1)..] : imageBase64;
        return UploadFaceBytesAsync("/api/ai/face/verify-image", familyMemberId,
            Convert.FromBase64String(payload), "camera-face.jpg", "image/jpeg", operation);
    }

    public Task<FaceVerificationResponse> VerifyCapturedGuestFaceAsync(string vehicleEvidenceId, string imageBase64)
    {
        int separator = imageBase64.IndexOf(',');
        string payload = separator >= 0 ? imageBase64[(separator + 1)..] : imageBase64;
        return UploadGuestFaceBytesAsync("/api/ai/face/guest/verify-image", vehicleEvidenceId,
            Convert.FromBase64String(payload), "camera-face.jpg", "image/jpeg");
    }

    public Task<ParkingResponse> EntryAsync(ParkingRequest request) =>
        PostDurableAsync("/api/parking/entry", OfflineOperationType.Entry, request);
    public Task<ParkingResponse> PreviewExitAsync(ParkingRequest request) => PostAsync("/api/parking/exit-preview", request);
    public Task<ParkingResponse> ConfirmExitAsync(ParkingRequest request) =>
        PostDurableAsync("/api/parking/exit-confirm", OfflineOperationType.ExitConfirm, request);

    public async Task<int> FlushOutboxAsync()
    {
        await _outboxGate.WaitAsync();
        try
        {
            foreach (OfflineOperation item in await _outbox.ReadAsync())
            {
                string path = item.Type == OfflineOperationType.Entry
                    ? "/api/parking/entry" : "/api/parking/exit-confirm";
                try
                {
                    await PostAsync(path, item.Request);
                    await _outbox.RemoveAsync(item.OperationId);
                }
                catch (Exception ex) when (IsConnectivityFailure(ex))
                {
                    await _outbox.RecordFailureAsync(item.OperationId, ex.Message);
                    break;
                }
                catch (DesktopAuthenticationException) { throw; }
                catch (Exception ex)
                {
                    await _outbox.RecordFailureAsync(item.OperationId, ex.Message);
                    break;
                }
            }
            return (await _outbox.ReadAsync()).Count;
        }
        finally { _outboxGate.Release(); }
    }

    public async Task<ResidentLookupResponse> LookupResidentAsync(string plate, string cardCode)
    {
        string query = $"?plate={Uri.EscapeDataString(plate ?? "")}&cardCode={Uri.EscapeDataString(cardCode ?? "")}";
        using var response = await SendProtectedAsync("/api/parking/lookup" + query, HttpMethod.Get, retrySafeRead: true);
        return await ApiResponseReader.ReadAsync<ResidentLookupResponse>(response, _json,
            "Spring Web", "tra cứu cư dân/phương tiện");
    }

    public async Task<List<ParkingSlotResponse>> GetSlotsAsync()
    {
        using var response = await SendProtectedAsync("/api/parking/slots", HttpMethod.Get, retrySafeRead: true);
        return await ApiResponseReader.ReadAsync<List<ParkingSlotResponse>>(response, _json,
            "Spring Web", "tải sơ đồ bãi xe");
    }

    public async Task AssignSlotAsync(long slotId, long? vehicleId)
    {
        using var response = await SendProtectedAsync($"/api/parking/slots/{slotId}/assign", HttpMethod.Post,
            () => JsonContent.Create(new SlotAssignRequest(vehicleId), options: _json));
        await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "gán xe vào ô");
    }

    public async Task BorrowSlotAsync(long slotId, string borrowedPlate, int hours, string notes)
    {
        using var response = await SendProtectedAsync($"/api/parking/slots/{slotId}/borrow", HttpMethod.Post,
            () => JsonContent.Create(new SlotBorrowRequest(borrowedPlate, hours, notes), options: _json));
        await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "thiết lập đỗ nhờ");
    }

    public async Task CancelBorrowAsync(long slotId)
    {
        using var response = await SendProtectedAsync($"/api/parking/slots/{slotId}/cancel-borrow", HttpMethod.Post);
        await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "hủy đỗ nhờ");
    }

    public async Task UpdateSlotStatusAsync(long slotId, string statusOverride)
    {
        using var response = await SendProtectedAsync($"/api/parking/slots/{slotId}/status", HttpMethod.Post,
            () => JsonContent.Create(new SlotStatusRequest(statusOverride), options: _json));
        await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "đổi trạng thái ô");
    }

    public async Task ReleaseSlotAsync(long slotId)
    {
        using var response = await SendProtectedAsync($"/api/parking/slots/{slotId}/release", HttpMethod.Post);
        await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "giải phóng ô");
    }

    public async Task DispatchSessionAsync(long slotId, long sessionId)
    {
        using var response = await SendProtectedAsync($"/api/parking/slots/{slotId}/dispatch-session", HttpMethod.Post,
            () => JsonContent.Create(new DispatchSessionRequest(sessionId), options: _json));
        await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "điều phối xe");
    }

    public async Task<List<UnassignedSessionResponse>> GetRecentUnassignedSessionsAsync()
    {
        using var response = await SendProtectedAsync("/api/parking/recent-unassigned", HttpMethod.Get, retrySafeRead: true);
        return await ApiResponseReader.ReadAsync<List<UnassignedSessionResponse>>(response, _json, "Spring Web", "lấy xe vừa vào trạm");
    }

    public async Task<List<UnassignedVehicleResponse>> GetUnassignedVehiclesAsync()
    {
        using var response = await SendProtectedAsync("/api/parking/vehicles-unassigned", HttpMethod.Get, retrySafeRead: true);
        return await ApiResponseReader.ReadAsync<List<UnassignedVehicleResponse>>(response, _json, "Spring Web", "lấy xe cư dân chưa có ô");
    }

    private async Task<ParkingResponse> PostAsync(string path, ParkingRequest request)
    {
        using var response = await SendProtectedAsync(path, HttpMethod.Post,
            () => JsonContent.Create(request, options: _json));
        string action = path.Contains("entry") ? "ghi nhận xe vào" :
            path.Contains("preview") ? "xem trước phí xe ra" : "xác nhận xe ra";
        return await ApiResponseReader.ReadAsync<ParkingResponse>(response, _json, "Spring Web", action);
    }

    private async Task<ParkingResponse> PostDurableAsync(string path, OfflineOperationType type,
            ParkingRequest request)
    {
        ParkingRequest durable = request with {
            OperationId = string.IsNullOrWhiteSpace(request.OperationId)
                ? Guid.NewGuid().ToString() : request.OperationId
        };
        try
        {
            return await PostAsync(path, durable);
        }
        catch (Exception ex) when (IsConnectivityFailure(ex))
        {
            int pending = await _outbox.EnqueueAsync(type, durable);
            throw new OfflineQueuedException(pending, ex);
        }
    }

    private static bool IsConnectivityFailure(Exception ex) =>
        ex is HttpRequestException or IOException or OperationCanceledException;

    private async Task<AnprResponse> UploadAiAsync(string endpoint, string path, string operation)
    {
        using var response = await SendProtectedAsync(endpoint, HttpMethod.Post, () =>
        {
            var multipart = new MultipartFormDataContent();
            multipart.Add(new StringContent(operation), "operation");
            bool image = endpoint.EndsWith("/image", StringComparison.Ordinal);
            var upload = image ? PrepareImageUpload(path) : (File.ReadAllBytes(path), Path.GetFileName(path), MediaTypeFor(path));
            var file = new ByteArrayContent(upload.Item1);
            file.Headers.ContentType = new MediaTypeHeaderValue(upload.Item3);
            multipart.Add(file, "file", upload.Item2);
            return multipart;
        }, requestTimeout: TimeSpan.FromMinutes(5));
        return await ApiResponseReader.ReadAsync<AnprResponse>(response, _json,
            "Hệ thống nhận dạng", endpoint.EndsWith("video") ? "nhận dạng video" : "nhận dạng ảnh");
    }

    private async Task<FaceVerificationResponse> UploadFaceAsync(
        string endpoint, long familyMemberId, string path, string operation, bool image)
    {
        using var response = await SendProtectedAsync(endpoint, HttpMethod.Post, () =>
        {
            var multipart = new MultipartFormDataContent();
            var upload = image ? PrepareImageUpload(path) : (File.ReadAllBytes(path), Path.GetFileName(path), MediaTypeFor(path));
            multipart.Add(new StringContent(familyMemberId.ToString()), "familyMemberId");
            multipart.Add(new StringContent(operation), "operation");
            var file = new ByteArrayContent(upload.Item1);
            file.Headers.ContentType = new MediaTypeHeaderValue(upload.Item3);
            multipart.Add(file, "realtime", upload.Item2);
            return multipart;
        }, requestTimeout: TimeSpan.FromMinutes(3));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Hệ thống nhận dạng", image ? "xác thực ảnh khuôn mặt" : "xác thực video khuôn mặt");
    }

    private async Task<FaceVerificationResponse> UploadFaceBytesAsync(string endpoint,
        long familyMemberId, byte[] bytes, string filename, string contentType, string operation)
    {
        using var response = await SendProtectedAsync(endpoint, HttpMethod.Post, () =>
        {
            var multipart = new MultipartFormDataContent();
            multipart.Add(new StringContent(familyMemberId.ToString()), "familyMemberId");
            multipart.Add(new StringContent(operation), "operation");
            var file = new ByteArrayContent(bytes);
            file.Headers.ContentType = new MediaTypeHeaderValue(contentType);
            multipart.Add(file, "realtime", filename);
            return multipart;
        }, requestTimeout: TimeSpan.FromMinutes(2));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Hệ thống nhận dạng", "đối chiếu khuôn mặt vừa chụp");
    }

    private async Task<FaceVerificationResponse> UploadGuestFaceAsync(
        string endpoint, string vehicleEvidenceId, string path, bool image)
    {
        using var response = await SendProtectedAsync(endpoint, HttpMethod.Post, () =>
        {
            var multipart = new MultipartFormDataContent();
            var upload = image ? PrepareImageUpload(path) : (File.ReadAllBytes(path), Path.GetFileName(path), MediaTypeFor(path));
            multipart.Add(new StringContent(vehicleEvidenceId), "vehicleEvidenceId");
            var file = new ByteArrayContent(upload.Item1);
            file.Headers.ContentType = new MediaTypeHeaderValue(upload.Item3);
            multipart.Add(file, "realtime", upload.Item2);
            return multipart;
        }, requestTimeout: TimeSpan.FromMinutes(3));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Hệ thống nhận dạng", image ? "xác thực ảnh khách vãng lai" : "xác thực video khách vãng lai");
    }

    private async Task<FaceVerificationResponse> UploadGuestFaceBytesAsync(string endpoint,
        string vehicleEvidenceId, byte[] bytes, string filename, string contentType)
    {
        using var response = await SendProtectedAsync(endpoint, HttpMethod.Post, () =>
        {
            var multipart = new MultipartFormDataContent();
            multipart.Add(new StringContent(vehicleEvidenceId), "vehicleEvidenceId");
            var file = new ByteArrayContent(bytes);
            file.Headers.ContentType = new MediaTypeHeaderValue(contentType);
            multipart.Add(file, "realtime", filename);
            return multipart;
        }, requestTimeout: TimeSpan.FromMinutes(2));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Hệ thống nhận dạng", "đối chiếu khuôn mặt khách vừa chụp");
    }

    private static (byte[], string, string) PrepareImageUpload(string path)
    {
        using var source = Image.FromFile(path);
        const int maxDimension = 2240;
        double scale = Math.Min(1.0, (double)maxDimension / Math.Max(source.Width, source.Height));
        int width = Math.Max(1, (int)Math.Round(source.Width * scale));
        int height = Math.Max(1, (int)Math.Round(source.Height * scale));
        using var resized = new Bitmap(width, height, System.Drawing.Imaging.PixelFormat.Format24bppRgb);
        using (var graphics = Graphics.FromImage(resized))
        {
            graphics.CompositingQuality = System.Drawing.Drawing2D.CompositingQuality.HighQuality;
            graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
            graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.HighQuality;
            graphics.DrawImage(source, 0, 0, width, height);
        }
        using var output = new MemoryStream();
        var codec = System.Drawing.Imaging.ImageCodecInfo.GetImageEncoders()
            .First(item => item.FormatID == System.Drawing.Imaging.ImageFormat.Jpeg.Guid);
        using var parameters = new System.Drawing.Imaging.EncoderParameters(1);
        parameters.Param[0] = new System.Drawing.Imaging.EncoderParameter(
            System.Drawing.Imaging.Encoder.Quality, 90L);
        resized.Save(output, codec, parameters);
        return (output.ToArray(), Path.GetFileNameWithoutExtension(path) + ".jpg", "image/jpeg");
    }

    private static string MediaTypeFor(string path) => Path.GetExtension(path).ToLowerInvariant() switch
    {
        ".jpg" or ".jpeg" => "image/jpeg",
        ".png" => "image/png",
        ".bmp" => "image/bmp",
        ".webp" => "image/webp",
        ".mp4" or ".m4v" => "video/mp4",
        ".avi" => "video/x-msvideo",
        ".mov" => "video/quicktime",
        ".mkv" => "video/x-matroska",
        ".wmv" => "video/x-ms-wmv",
        ".webm" => "video/webm",
        _ => "application/octet-stream"
    };

    private async Task<HttpResponseMessage> SendProtectedAsync(string path, HttpMethod method,
            Func<HttpContent?>? contentFactory = null, bool retrySafeRead = false, TimeSpan? requestTimeout = null)
    {
        string accessToken = await GetFreshAccessTokenAsync(requireCurrentOrigin: true);
        Uri uri = BuildProtectedUri(path);
        using var timeout = new CancellationTokenSource(requestTimeout ?? TimeSpan.FromSeconds(10));
        using var request = new HttpRequestMessage(method, uri) { Content = contentFactory?.Invoke() };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        HttpResponseMessage response = await _http.SendAsync(request, timeout.Token);
        if (response.StatusCode != HttpStatusCode.Unauthorized) return response;

        bool refreshed = await RefreshAfterUnauthorizedAsync(accessToken);
        if (!retrySafeRead || !refreshed) return response;

        response.Dispose();
        string renewedAccessToken = await GetFreshAccessTokenAsync(requireCurrentOrigin: true);
        using var retry = new HttpRequestMessage(method, BuildProtectedUri(path));
        retry.Headers.Authorization = new AuthenticationHeaderValue("Bearer", renewedAccessToken);
        return await _http.SendAsync(retry, timeout.Token);
    }

    private async Task<string> GetFreshAccessTokenAsync(bool requireCurrentOrigin)
    {
        if (_authenticatedBaseUrl is null || _accessToken is null || _refreshToken is null)
            throw new DesktopAuthenticationException("Đăng nhập để tiếp tục sử dụng trạm gác.");
        string? accessToken = _accessToken;
        if (accessToken is not null && DateTimeOffset.UtcNow.AddSeconds(30) < _accessExpiresAt)
        {
            if (requireCurrentOrigin) EnsureBoundOrigin();
            return accessToken;
        }

        await _refreshGate.WaitAsync();
        try
        {
            if (requireCurrentOrigin) EnsureBoundOrigin();
            if (_accessToken is not null && DateTimeOffset.UtcNow.AddSeconds(30) < _accessExpiresAt)
                return _accessToken;
            if (!await RotateRefreshTokenAsync())
                throw new DesktopAuthenticationException("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
            return _accessToken!;
        }
        finally { _refreshGate.Release(); }
    }

    private async Task<bool> RefreshAfterUnauthorizedAsync(string failedAccessToken)
    {
        await _refreshGate.WaitAsync();
        try
        {
            if (_accessToken is not null && !string.Equals(_accessToken, failedAccessToken, StringComparison.Ordinal))
                return DateTimeOffset.UtcNow < _accessExpiresAt;
            return await RotateRefreshTokenAsync();
        }
        finally { _refreshGate.Release(); }
    }

    private async Task<bool> RotateRefreshTokenAsync()
    {
        if (_refreshToken is null || DateTimeOffset.UtcNow >= _sessionExpiresAt)
        {
            ClearAuthentication();
            return false;
        }

        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Post,
                BuildUri(_authenticatedBaseUrl!, "/api/auth/refresh"))
            {
                Content = JsonContent.Create(new RefreshRequest(_refreshToken), options: _json)
            };
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
            using var response = await _http.SendAsync(request, timeout.Token);
            if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.BadRequest)
            {
                ClearAuthentication();
                return false;
            }
            await ApiResponseReader.EnsureSuccessAsync(response, "Spring Web", "gia hạn phiên đăng nhập");
            var tokens = await JsonSerializer.DeserializeAsync<TokenResponse>(
                await response.Content.ReadAsStreamAsync(), _json)
                ?? throw new JsonException("Spring Web returned an empty token response.");
            InstallTokens(tokens, _authenticatedBaseUrl!);
            return true;
        }
        catch (Exception ex) when (ex is HttpRequestException or IOException or OperationCanceledException
                or JsonException or InvalidOperationException)
        {
            ClearAuthentication();
            throw new DesktopAuthenticationException(
                "Không thể xác minh an toàn phiên đăng nhập. Hãy đăng nhập lại trước khi tiếp tục.");
        }
    }

    private Uri BuildProtectedUri(string path)
    {
        EnsureBoundOrigin();
        return BuildUri(_authenticatedBaseUrl!, path);
    }

    private void EnsureBoundOrigin()
    {
        if (_authenticatedBaseUrl is null || !SameOrigin(
                BuildUri(NormalizeBaseUrl(BaseUrl), "/"), BuildUri(_authenticatedBaseUrl, "/")))
            throw new DesktopAuthenticationException(
                "Địa chỉ Spring đã thay đổi sau đăng nhập. Hãy đăng xuất và đăng nhập lại để không gửi token nhầm máy chủ.");
    }

    private void InstallTokens(TokenResponse tokens, string baseUrl)
    {
        if (string.IsNullOrWhiteSpace(tokens.AccessToken) || string.IsNullOrWhiteSpace(tokens.RefreshToken)
                || !string.Equals(tokens.TokenType, "Bearer", StringComparison.OrdinalIgnoreCase)
                || tokens.ExpiresIn is <= 0 or > 900
                || tokens.AbsoluteExpiresAt <= DateTimeOffset.UtcNow)
            throw new JsonException("Spring Web returned invalid Desktop authentication data.");
        _accessToken = tokens.AccessToken;
        _refreshToken = tokens.RefreshToken;
        _accessExpiresAt = DateTimeOffset.UtcNow.AddSeconds(tokens.ExpiresIn);
        _sessionExpiresAt = tokens.AbsoluteExpiresAt;
        _authenticatedBaseUrl = baseUrl;
        UpdateOperator(new OperatorProfile(
            string.IsNullOrWhiteSpace(tokens.Username) ? "Nhân viên" : tokens.Username,
            string.IsNullOrWhiteSpace(tokens.FullName) ? tokens.Username : tokens.FullName,
            tokens.ActiveShift));
    }

    private void ClearAuthentication()
    {
        _accessToken = null;
        _refreshToken = null;
        _authenticatedBaseUrl = null;
        _accessExpiresAt = DateTimeOffset.MinValue;
        _sessionExpiresAt = DateTimeOffset.MinValue;
        CurrentOperator = null;
    }

    private void UpdateOperator(OperatorProfile profile)
    {
        CurrentOperator = profile;
        OperatorProfileChanged?.Invoke(profile);
    }

    private static string NormalizeBaseUrl(string value)
    {
        if (!Uri.TryCreate(value?.Trim(), UriKind.Absolute, out Uri? uri)
                || (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp)
                || !string.IsNullOrEmpty(uri.Query) || !string.IsNullOrEmpty(uri.Fragment))
            throw new InvalidOperationException("Spring API URL must be an absolute HTTP(S) origin without query or fragment.");
        if (uri.Scheme == Uri.UriSchemeHttp && !uri.IsLoopback)
            throw new InvalidOperationException("HTTP is allowed only for localhost; configure HTTPS for a network Spring API.");
        return value.Trim().TrimEnd('/');
    }

    private static Uri BuildUri(string baseUrl, string path) => new(baseUrl.TrimEnd('/') + path, UriKind.Absolute);

    private static bool SameOrigin(Uri first, Uri second) =>
        Uri.Compare(first, second, UriComponents.SchemeAndServer, UriFormat.SafeUnescaped,
            StringComparison.OrdinalIgnoreCase) == 0;

    public void Dispose()
    {
        ClearAuthentication();
        _refreshGate.Dispose();
        _outboxGate.Dispose();
        _http.Dispose();
    }

    private sealed record LoginRequest(string Username, string Password, string DeviceLabel);
    private sealed record RefreshRequest(string RefreshToken);
    private sealed record TokenResponse(string AccessToken, string TokenType, long ExpiresIn,
        string RefreshToken, string SessionId, DateTimeOffset AbsoluteExpiresAt,
        string Username, string FullName, OperatorShift? ActiveShift);
}
