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
    private string? _accessToken;
    private string? _refreshToken;
    private string? _authenticatedBaseUrl;
    private DateTimeOffset _accessExpiresAt;
    private DateTimeOffset _sessionExpiresAt;

    public ParkingApiClient() : this(new HttpClientHandler { AllowAutoRedirect = false }) { }

    internal ParkingApiClient(HttpMessageHandler handler)
    {
        _http = new HttpClient(handler) { Timeout = Timeout.InfiniteTimeSpan };
    }

    public string BaseUrl { get; set; } = "http://localhost:8080";
    public bool IsAuthenticated => _accessToken is not null && _refreshToken is not null;

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

    public Task<ParkingResponse> EntryAsync(ParkingRequest request) => PostAsync("/api/parking/entry", request);
    public Task<ParkingResponse> PreviewExitAsync(ParkingRequest request) => PostAsync("/api/parking/exit-preview", request);
    public Task<ParkingResponse> ConfirmExitAsync(ParkingRequest request) => PostAsync("/api/parking/exit-confirm", request);

    public async Task<AnprResponse> RecognizeImageAsync(string path, string operation)
    {
        string extension = Path.GetExtension(path).ToLowerInvariant();
        string contentType = extension switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".bmp" => "image/bmp",
            ".webp" => "image/webp",
            _ => throw new InvalidOperationException("Định dạng ảnh không được hỗ trợ.")
        };
        using var response = await SendProtectedAsync("/api/parking/anpr/recognize/image", HttpMethod.Post, () =>
        {
            var content = new MultipartFormDataContent();
            content.Add(new StringContent(operation), "operation");
            var file = new StreamContent(File.OpenRead(path));
            file.Headers.ContentType = new MediaTypeHeaderValue(contentType);
            content.Add(file, "file", Path.GetFileName(path));
            return content;
        }, requestTimeout: TimeSpan.FromSeconds(45));
        return await ApiResponseReader.ReadAsync<AnprResponse>(response, _json,
            "Spring Web", "nhận dạng ảnh qua ANPR");
    }

    public async Task<AnprResponse> RecognizeVideoAsync(string path, string operation)
    {
        string extension = Path.GetExtension(path).ToLowerInvariant();
        string contentType = extension switch
        {
            ".mp4" or ".m4v" => "video/mp4",
            ".avi" => "video/x-msvideo",
            ".mov" => "video/quicktime",
            ".mkv" => "video/x-matroska",
            ".wmv" => "video/x-ms-wmv",
            ".webm" => "video/webm",
            _ => throw new InvalidOperationException("Định dạng video không được hỗ trợ.")
        };
        if (new FileInfo(path).Length > 10L * 1024 * 1024)
            throw new InvalidOperationException("Video vượt quá giới hạn 10 MB.");
        using var response = await SendProtectedAsync("/api/parking/anpr/recognize/video", HttpMethod.Post, () =>
        {
            var content = new MultipartFormDataContent();
            content.Add(new StringContent(operation), "operation");
            var file = new StreamContent(File.OpenRead(path));
            file.Headers.ContentType = new MediaTypeHeaderValue(contentType);
            content.Add(file, "file", Path.GetFileName(path));
            return content;
        }, requestTimeout: TimeSpan.FromSeconds(60));
        return await ApiResponseReader.ReadAsync<AnprResponse>(response, _json,
            "Spring Web", "nhận dạng video qua ANPR");
    }

    public async Task<FaceVerificationResponse> CaptureGuestFaceAsync(string plateNumber)
    {
        using var response = await SendProtectedAsync("/api/parking/anpr/face/capture", HttpMethod.Post,
            () => JsonContent.Create(new FaceCaptureRequest("ENTRY", plateNumber), options: _json),
            requestTimeout: TimeSpan.FromSeconds(60));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Spring Web", "chụp khuôn mặt khách vãng lai");
    }

    public async Task<FaceVerificationResponse> VerifyFaceAsync(string operation, string plateNumber,
            long? familyMemberId, string evidenceId = "")
    {
        using var response = await SendProtectedAsync("/api/parking/anpr/face/verify", HttpMethod.Post,
            () => JsonContent.Create(new FaceVerificationRequest(operation, plateNumber, familyMemberId, evidenceId), options: _json),
            requestTimeout: TimeSpan.FromSeconds(60));
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "Spring Web", "xác thực khuôn mặt qua ANPR");
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

    private async Task<HttpResponseMessage> SendProtectedAsync(string path, HttpMethod method,
            Func<HttpContent?>? contentFactory = null, bool retrySafeRead = false, TimeSpan? requestTimeout = null)
    {
        string accessToken = await GetFreshAccessTokenAsync();
        Uri uri = BuildProtectedUri(path);
        using var timeout = new CancellationTokenSource(requestTimeout ?? TimeSpan.FromSeconds(10));
        using var request = new HttpRequestMessage(method, uri) { Content = contentFactory?.Invoke() };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        HttpResponseMessage response = await _http.SendAsync(request, timeout.Token);
        if (response.StatusCode != HttpStatusCode.Unauthorized) return response;

        bool refreshed = await RefreshAfterUnauthorizedAsync(accessToken);
        if (!retrySafeRead || !refreshed)
            return response;

        response.Dispose();
        string renewedAccessToken = await GetFreshAccessTokenAsync();
        using var retry = new HttpRequestMessage(method, BuildProtectedUri(path));
        retry.Headers.Authorization = new AuthenticationHeaderValue("Bearer", renewedAccessToken);
        return await _http.SendAsync(retry, timeout.Token);
    }

    private async Task<string> GetFreshAccessTokenAsync()
        => await GetFreshAccessTokenAsync(requireCurrentOrigin: true);

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
            if (_accessToken is not null && !StringComparer.Ordinal.Equals(_accessToken, failedAccessToken))
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
                || !StringComparer.OrdinalIgnoreCase.Equals(tokens.TokenType, "Bearer")
                || tokens.ExpiresIn is <= 0 or > 900
                || tokens.AbsoluteExpiresAt <= DateTimeOffset.UtcNow)
            throw new JsonException("Spring Web returned invalid Desktop authentication data.");
        _accessToken = tokens.AccessToken;
        _refreshToken = tokens.RefreshToken;
        _accessExpiresAt = DateTimeOffset.UtcNow.AddSeconds(tokens.ExpiresIn);
        _sessionExpiresAt = tokens.AbsoluteExpiresAt;
        _authenticatedBaseUrl = baseUrl;
    }

    private void ClearAuthentication()
    {
        _accessToken = null;
        _refreshToken = null;
        _authenticatedBaseUrl = null;
        _accessExpiresAt = DateTimeOffset.MinValue;
        _sessionExpiresAt = DateTimeOffset.MinValue;
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
        _http.Dispose();
    }

    private sealed record LoginRequest(string Username, string Password, string DeviceLabel);
    private sealed record RefreshRequest(string RefreshToken);
    private sealed record FaceCaptureRequest(string Operation, string PlateNumber);
    private sealed record FaceVerificationRequest(string Operation, string PlateNumber, long? FamilyMemberId,
        string EvidenceId);
    private sealed record TokenResponse(string AccessToken, string TokenType, long ExpiresIn,
        string RefreshToken, string SessionId, DateTimeOffset AbsoluteExpiresAt);
}
