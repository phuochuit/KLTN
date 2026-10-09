namespace ParkingGateDesktop;

public sealed class AnprApiClient
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromMinutes(5) };
    public string BaseUrl { get; set; } = "http://localhost:8001";

    public async Task<bool> HealthAsync()
    {
        using var response = await _http.GetAsync($"{BaseUrl.TrimEnd('/')}/health");
        return response.IsSuccessStatusCode;
    }

}
