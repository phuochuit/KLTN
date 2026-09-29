using System.Net.Http.Headers;
using System.Text.Json;
using System.Net.Http.Json;

namespace ParkingGateDesktop;

public sealed class AnprApiClient
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromMinutes(5) };
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);
    public string BaseUrl { get; set; } = "http://localhost:8001";

    public async Task<bool> HealthAsync()
    {
        using var response = await _http.GetAsync($"{BaseUrl.TrimEnd('/')}/health");
        return response.IsSuccessStatusCode;
    }

    public Task<AnprResponse> RecognizeImageAsync(string path) => UploadAsync("/recognize/image", path);
    public Task<AnprResponse> RecognizeVideoAsync(string path) => UploadAsync("/recognize/video", path);

    public async Task<FaceVerificationResponse> VerifyCameraAsync(string registeredImageUrl)
    {
        using var response = await _http.PostAsJsonAsync($"{BaseUrl.TrimEnd('/')}/face/verify-camera", new { registeredImageUrl, cameraIndex = 0 }, _json);
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "ANPR/Khuôn mặt", "xác thực khuôn mặt realtime");
    }

    public async Task<FaceVerificationResponse> CaptureCameraAsync()
    {
        using var response = await _http.PostAsync($"{BaseUrl.TrimEnd('/')}/face/capture-camera", null);
        return await ApiResponseReader.ReadAsync<FaceVerificationResponse>(response, _json,
            "ANPR/Khuôn mặt", "chụp khuôn mặt khách vãng lai");
    }

    private async Task<AnprResponse> UploadAsync(string endpoint, string path)
    {
        await using var stream = File.OpenRead(path);
        using var content = new MultipartFormDataContent();
        using var file = new StreamContent(stream);
        file.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        content.Add(file, "file", Path.GetFileName(path));
        using var response = await _http.PostAsync($"{BaseUrl.TrimEnd('/')}{endpoint}", content);
        return await ApiResponseReader.ReadAsync<AnprResponse>(response, _json,
            "ANPR", endpoint.Contains("video") ? "nhận dạng video" : "nhận dạng ảnh");
    }
}
