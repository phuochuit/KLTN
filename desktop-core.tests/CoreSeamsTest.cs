using System.Net;
using System.Text.Json;
using ParkingGateDesktop;
using Xunit;

namespace DesktopCore.Tests;

public sealed class CoreSeamsTest
{
    private static string NewPath() {
        string dir = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../.cache/test-temp", Guid.NewGuid().ToString("N")));
        Directory.CreateDirectory(dir);
        return Path.Combine(dir, "outbox.json");
    }

    private static ParkingRequest Request(string id) => new(
        "TEST123", "TEST", "MOTORBIKE", false, null, true, 0.9,
        "", "", "", "", "", null, null, id);

    [Fact]
    public async Task InspectionCountsPendingAndPreservesCorruptBytesAndQuarantine()
    {
        string path = NewPath();
        var store = new OfflineOutboxStore(path);
        Assert.Equal(0, (await store.InspectAsync()).PendingCount);
        await store.EnqueueAsync(OfflineOperationType.Entry, Request("id-1"));
        Assert.Equal(1, (await store.InspectAsync()).PendingCount);
        await store.EnqueueAsync(OfflineOperationType.Entry, Request("id-2"));
        Assert.Equal(2, (await store.InspectAsync()).PendingCount);
        byte[] broken = "{ broken"u8.ToArray();
        await File.WriteAllBytesAsync(path, broken);
        var snapshot = await store.InspectAsync();
        Assert.True(snapshot.RequiresReview);
        Assert.Equal(broken, await File.ReadAllBytesAsync(path));
        Assert.Empty(Directory.GetFiles(Path.GetDirectoryName(path)!, "*.corrupt-*"));
        File.Delete(path);
        await File.WriteAllTextAsync(path + ".corrupt-123", "synthetic");
        Assert.True((await store.InspectAsync()).RequiresReview);
        Assert.Equal("synthetic", await File.ReadAllTextAsync(path + ".corrupt-123"));
    }

    [Fact]
    public async Task NullJsonRequiresReviewWithoutMutation()
    {
        string path = NewPath();
        await File.WriteAllTextAsync(path, "null");
        Assert.True((await new OfflineOutboxStore(path).InspectAsync()).RequiresReview);
        Assert.Equal("null", await File.ReadAllTextAsync(path));
    }

    [Fact]
    public async Task InspectionDoesNotTreatAnUnreadableOutboxAsEmpty()
    {
        string path = NewPath();
        Directory.CreateDirectory(path);
        Exception error = await Assert.ThrowsAnyAsync<Exception>(() => new OfflineOutboxStore(path).InspectAsync());
        Assert.True(error is IOException or UnauthorizedAccessException);
    }

    [Fact]
    public async Task InvalidDurableRecordRequiresReviewWithoutMutation()
    {
        string path = NewPath();
        var store = new OfflineOutboxStore(path);
        await store.EnqueueAsync(OfflineOperationType.Entry, Request("id-1"));
        string json = await File.ReadAllTextAsync(path);
        string invalid = json.Replace("\"type\": 0", "\"type\": 42");
        Assert.NotEqual(json, invalid);
        await File.WriteAllTextAsync(path, invalid);
        Assert.True((await store.InspectAsync()).RequiresReview);
        Assert.Equal(invalid, await File.ReadAllTextAsync(path));
        int lastId = json.LastIndexOf("\"operationId\": \"id-1\"", StringComparison.Ordinal);
        Assert.True(lastId >= 0);
        string mismatched = json[..lastId] + json[lastId..].Replace("\"operationId\": \"id-1\"", "\"operationId\": \"different\"");
        await File.WriteAllTextAsync(path, mismatched);
        Assert.True((await store.InspectAsync()).RequiresReview);
        Assert.Equal(mismatched, await File.ReadAllTextAsync(path));
        string missingType = json.Replace("\"type\": 0,", "");
        Assert.NotEqual(json, missingType);
        await File.WriteAllTextAsync(path, missingType);
        Assert.True((await store.InspectAsync()).RequiresReview);
        Assert.Equal(missingType, await File.ReadAllTextAsync(path));
    }

    [Fact]
    public async Task InjectedClientPreservesOriginSessionAndDurableOperationId()
    {
        string path = NewPath();
        var store = new OfflineOutboxStore(path);
        var settings = new GateStationSettings { VehicleCameraSource = "17" };
        string? sentId = null;
        int calls = 0;
        var handler = new FakeHandler(async request => {
            calls++;
            if (request.RequestUri!.AbsolutePath == "/api/auth/login")
                return Json("{\"accessToken\":\"synthetic-token\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"synthetic-refresh\",\"sessionId\":\"test\",\"absoluteExpiresAt\":\"2099-01-01T00:00:00Z\",\"username\":\"test\",\"fullName\":\"Test\"}");
            Assert.Equal("Bearer", request.Headers.Authorization!.Scheme);
            Assert.Equal("synthetic-token", request.Headers.Authorization.Parameter);
            if (request.RequestUri.AbsolutePath == "/api/auth/session")
                return Json("{\"username\":\"test\",\"fullName\":\"Test\"}");
            using var body = JsonDocument.Parse(await request.Content!.ReadAsStringAsync());
            sentId = body.RootElement.GetProperty("operationId").GetString();
            throw new HttpRequestException("synthetic offline");
        });
        using var client = new ParkingApiClient(handler, store, settings) { BaseUrl = "https://example.invalid" };
        Assert.Same(settings, client.StationSettings);
        Assert.Equal(17, client.VehicleCameraIndex);
        await client.LoginAsync("test", "test");
        Assert.True(client.IsAuthenticated);
        Assert.Equal("test", (await client.ValidateSessionAsync()).Username);
        await Assert.ThrowsAsync<OfflineQueuedException>(() => client.EntryAsync(Request("fixed-id")));
        Assert.Equal("fixed-id", sentId);
        Assert.Equal("fixed-id", Assert.Single(await store.ReadAsync()).OperationId);
        await Assert.ThrowsAsync<OfflineQueuedException>(() => client.EntryAsync(Request("fixed-id")));
        Assert.Single(await store.ReadAsync());
        client.BaseUrl = "https://other.invalid";
        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.ValidateSessionAsync());
        Assert.Equal(4, calls);
    }

    private static HttpResponseMessage Json(string value) => new(HttpStatusCode.OK) { Content = new StringContent(value, System.Text.Encoding.UTF8, "application/json") };
    private sealed class FakeHandler(Func<HttpRequestMessage, Task<HttpResponseMessage>> send) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) => send(request);
    }
}
