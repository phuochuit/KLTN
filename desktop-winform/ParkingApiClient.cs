using System.Net.Http.Json;
using System.Text.Json;

namespace ParkingGateDesktop;

public sealed class ParkingApiClient
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(10) };
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);

    public string BaseUrl { get; set; } = "http://localhost:8080";

    public async Task<bool> HealthAsync()
    {
        using var response = await _http.GetAsync($"{BaseUrl.TrimEnd('/')}/api/parking/health");
        return response.IsSuccessStatusCode;
    }

    public Task<ParkingResponse> EntryAsync(ParkingRequest request) => PostAsync("/api/parking/entry", request);
    public Task<ParkingResponse> PreviewExitAsync(ParkingRequest request) => PostAsync("/api/parking/exit-preview", request);
    public Task<ParkingResponse> ConfirmExitAsync(ParkingRequest request) => PostAsync("/api/parking/exit-confirm", request);

    public async Task<ResidentLookupResponse> LookupResidentAsync(string plate, string cardCode)
    {
        string query = $"plate={Uri.EscapeDataString(plate ?? "")}&cardCode={Uri.EscapeDataString(cardCode ?? "")}";
        using var response = await _http.GetAsync($"{BaseUrl.TrimEnd('/')}/api/parking/lookup?{query}");
        return await ApiResponseReader.ReadAsync<ResidentLookupResponse>(response, _json,
            "Spring Web", "tra cứu cư dân/phương tiện");
    }

    public async Task<List<ParkingSlotResponse>> GetSlotsAsync()
    {
        using var response = await _http.GetAsync($"{BaseUrl.TrimEnd('/')}/api/parking/slots");
        return await ApiResponseReader.ReadAsync<List<ParkingSlotResponse>>(response, _json,
            "Spring Web", "tải sơ đồ bãi xe");
    }

    private async Task<ParkingResponse> PostAsync(string path, ParkingRequest request)
    {
        using var response = await _http.PostAsJsonAsync($"{BaseUrl.TrimEnd('/')}{path}", request, _json);
        string action = path.Contains("entry") ? "ghi nhận xe vào" :
            path.Contains("preview") ? "xem trước phí xe ra" : "xác nhận xe ra";
        return await ApiResponseReader.ReadAsync<ParkingResponse>(response, _json, "Spring Web", action);
    }
}
