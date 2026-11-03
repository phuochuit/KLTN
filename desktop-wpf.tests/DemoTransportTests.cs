using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace LumiPark.Desktop.Tests;

public sealed class DemoTransportTests
{
    [Fact]
    public async Task ConcurrentSessionReadCannotReplaceMutationRequestPayload()
    {
        var transport = new DemoTransport();
        using var client = new HttpClient(transport) { BaseAddress = new Uri("https://demo.invalid") };
        using var login = await client.PostAsJsonAsync("/api/auth/login", new { username = "demo", password = "demo" });
        client.DefaultRequestHeaders.Authorization = new("Bearer", "demo-access");
        transport.SlowResponses = true;
        var entry = client.PostAsJsonAsync("/api/parking/entry", new { plateNumber = "TEST-CONCURRENT" });
        var session = client.GetAsync("/api/auth/session");
        await Task.WhenAll(entry, session).WaitAsync(TimeSpan.FromSeconds(5));
        using var mutationResponse = await entry;
        using var sessionResponse = await session;
        Assert.Equal(HttpStatusCode.OK, mutationResponse.StatusCode);
        using var body = JsonDocument.Parse(await mutationResponse.Content.ReadAsStringAsync());
        Assert.Equal("TEST-CONCURRENT", body.RootElement.GetProperty("plateNumber").GetString());
        Assert.Equal(HttpStatusCode.OK, sessionResponse.StatusCode);
        Assert.Equal(1, transport.MutationCount);
    }

    [Fact]
    public async Task DemoTransportHandlesOperationalRoutesInMemoryAndUnknownRoutesDoNotForward()
    {
        var transport = new DemoTransport { UnknownMutation = true };
        using var client = new HttpClient(transport) { BaseAddress = new Uri("https://demo.invalid") };
        var login = await client.PostAsJsonAsync("/api/auth/login", new { username = "demo", password = "demo" });
        Assert.Equal(HttpStatusCode.OK, login.StatusCode);
        using var token = JsonDocument.Parse(await login.Content.ReadAsStringAsync());
        var access = token.RootElement.GetProperty("accessToken").GetString();
        client.DefaultRequestHeaders.Authorization = new("Bearer", access);

        var lookup = await client.GetAsync("/api/parking/lookup?plate=TEST-01&cardCode=TEST-01");
        Assert.Equal(HttpStatusCode.OK, lookup.StatusCode);
        using var lookupBody = JsonDocument.Parse(await lookup.Content.ReadAsStringAsync());
        Assert.Equal(7, lookupBody.RootElement.GetProperty("authorizedMembers")[0].GetProperty("id").GetInt64());

        var mutation = await client.PostAsJsonAsync("/api/parking/entry", new { plateNumber = "TEST-01", operationId = Guid.NewGuid().ToString() });
        Assert.Equal(HttpStatusCode.ServiceUnavailable, mutation.StatusCode);
        Assert.Equal(1, transport.MutationCount);
        Assert.Contains("TEST-01", transport.LastPayload!);
        var unknown = await client.GetAsync("/not-a-demo-route");
        Assert.Equal(HttpStatusCode.NotFound, unknown.StatusCode);
        Assert.Equal(4, transport.RequestCount);
        Assert.Equal(new[] { "/api/auth/login", "/api/parking/lookup", "/api/parking/entry", "/not-a-demo-route" }, transport.Paths);
    }

    [Fact]
    public async Task DemoScenariosAreExplicitAndSyntheticPayloadsContainNoOperationalFiles()
    {
        var transport = new DemoTransport { RegisteredMember = false, ExpiredPass = true, FaceReview = true };
        using var client = new HttpClient(transport) { BaseAddress = new Uri("https://demo.invalid") };
        using var response = await client.GetAsync("/api/parking/lookup?plate=TEST-01");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.True(transport.ExpiredPass);
        Assert.True(transport.FaceReview);
        Assert.Equal(1, transport.RequestCount);
        Assert.Equal("/api/parking/lookup", transport.Paths[0]);
    }

    [Fact]
    public async Task DemoExposesEvidenceAndShiftSummaryThroughTheSharedAuthenticatedClient()
    {
        var folder = Path.Combine(Path.GetTempPath(), "wpf-demo-s3a-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(folder);
        var transport = new DemoTransport();
        using var client = new ParkingApiClient(transport,
            new OfflineOutboxStore(Path.Combine(folder, "legacy.json")), new GateStationSettings());
        try
        {
            await client.LoginAsync("demo", "demo");
            var evidence = await client.GetEvidenceComparisonAsync("TEST-01", "CAR", "demo-vehicle", "demo-face");
            Assert.Equal(7001, evidence.SessionId);
            Assert.Equal("TEST-01", evidence.EntryPlate);
            Assert.False(string.IsNullOrWhiteSpace(evidence.EntryVehicleImageBase64));
            var previous = await client.GetShiftSummaryAsync(true);
            Assert.NotNull(previous);
            Assert.Equal("Ca minh họa trước", previous!.ShiftName);
            Assert.Equal(25000m, previous.TotalRevenue);
            Assert.Contains("/api/parking/evidence-comparison", transport.Paths);
            Assert.Contains("/api/auth/shift-summary", transport.Paths);
            Assert.True(client.MarkShiftSummaryShown(previous.ShiftId));
            Assert.False(client.MarkShiftSummaryShown(previous.ShiftId));
        }
        finally
        {
            client.Dispose();
            if (Directory.Exists(folder)) Directory.Delete(folder, true);
        }
    }
}
