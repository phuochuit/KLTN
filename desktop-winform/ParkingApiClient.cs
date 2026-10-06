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

    public async Task AssignSlotAsync(long slotId, long? vehicleId)
    {
        using var response = await _http.PostAsJsonAsync($"{BaseUrl.TrimEnd('/')}/api/parking/slots/{slotId}/assign", new SlotAssignRequest(vehicleId), _json);
        if (!response.IsSuccessStatusCode)
        {
            string err = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException($"Lỗi gán xe: {err}");
        }
    }

    public async Task BorrowSlotAsync(long slotId, string borrowedPlate, int hours, string notes)
    {
        using var response = await _http.PostAsJsonAsync($"{BaseUrl.TrimEnd('/')}/api/parking/slots/{slotId}/borrow", new SlotBorrowRequest(borrowedPlate, hours, notes), _json);
        if (!response.IsSuccessStatusCode)
        {
            string err = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException($"Lỗi thiết lập đỗ nhờ: {err}");
        }
    }

    public async Task CancelBorrowAsync(long slotId)
    {
        using var response = await _http.PostAsync($"{BaseUrl.TrimEnd('/')}/api/parking/slots/{slotId}/cancel-borrow", null);
        if (!response.IsSuccessStatusCode)
        {
            string err = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException($"Lỗi hủy đỗ nhờ: {err}");
        }
    }

    public async Task UpdateSlotStatusAsync(long slotId, string statusOverride)
    {
        using var response = await _http.PostAsJsonAsync($"{BaseUrl.TrimEnd('/')}/api/parking/slots/{slotId}/status", new SlotStatusRequest(statusOverride), _json);
        if (!response.IsSuccessStatusCode)
        {
            string err = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException($"Lỗi đổi trạng thái: {err}");
        }
    }

    public async Task ReleaseSlotAsync(long slotId)
    {
        using var response = await _http.PostAsync($"{BaseUrl.TrimEnd('/')}/api/parking/slots/{slotId}/release", null);
        if (!response.IsSuccessStatusCode)
        {
            string err = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException($"Lỗi giải phóng ô: {err}");
        }
    }

    public async Task DispatchSessionAsync(long slotId, long sessionId)
    {
        using var response = await _http.PostAsJsonAsync($"{BaseUrl.TrimEnd('/')}/api/parking/slots/{slotId}/dispatch-session", new DispatchSessionRequest(sessionId), _json);
        if (!response.IsSuccessStatusCode)
        {
            string err = await response.Content.ReadAsStringAsync();
            throw new InvalidOperationException($"Lỗi điều phối xe: {err}");
        }
    }

    public async Task<List<UnassignedSessionResponse>> GetRecentUnassignedSessionsAsync()
    {
        using var response = await _http.GetAsync($"{BaseUrl.TrimEnd('/')}/api/parking/recent-unassigned");
        return await ApiResponseReader.ReadAsync<List<UnassignedSessionResponse>>(response, _json, "Spring Web", "lấy xe vừa vào trạm");
    }

    public async Task<List<UnassignedVehicleResponse>> GetUnassignedVehiclesAsync()
    {
        using var response = await _http.GetAsync($"{BaseUrl.TrimEnd('/')}/api/parking/vehicles-unassigned");
        return await ApiResponseReader.ReadAsync<List<UnassignedVehicleResponse>>(response, _json, "Spring Web", "lấy xe cư dân chưa có ô");
    }

    private async Task<ParkingResponse> PostAsync(string path, ParkingRequest request)
    {
        using var response = await _http.PostAsJsonAsync($"{BaseUrl.TrimEnd('/')}{path}", request, _json);
        string action = path.Contains("entry") ? "ghi nhận xe vào" :
            path.Contains("preview") ? "xem trước phí xe ra" : "xác nhận xe ra";
        return await ApiResponseReader.ReadAsync<ParkingResponse>(response, _json, "Spring Web", action);
    }
}
