using System.Net;
using System.Text;
using ParkingGateDesktop;
using Xunit;

namespace DesktopCore.Tests;

public class SingleAttemptTests
{
    [Fact]
    public async Task SuccessSendsOnceWithProvidedIdAndDoesNotQueue()
    {
        string dir = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../.cache/test-temp", Guid.NewGuid().ToString("N")));
        Directory.CreateDirectory(dir);
        var store = new OfflineOutboxStore(Path.Combine(dir, "legacy.json"));
        int posts = 0;
        string body = "";
        using var client = new ParkingApiClient(new Handler(async request => {
            if (request.RequestUri!.AbsolutePath == "/api/auth/login")
                return Json("{\"accessToken\":\"token\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"refresh\",\"sessionId\":\"test\",\"absoluteExpiresAt\":\"2099-01-01T00:00:00Z\",\"username\":\"test\",\"fullName\":\"Test\"}");
            posts++;
            body = await request.Content!.ReadAsStringAsync();
            return Json("{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"OPEN\",\"entryTime\":\"2026-01-01T00:00:00\",\"fee\":0}");
        }), store, new GateStationSettings()) { BaseUrl = "https://example.invalid" };
        await client.LoginAsync("test", "test");
        string id = Guid.NewGuid().ToString();
        var request = new ParkingRequest("TEST123", "", "MOTORBIKE", false, null, false, null, "", "", "", "", "", OperationId: id);
        var result = await client.EntrySingleAttemptAsync(request);
        Assert.Equal(SingleAttemptOutcome.Success, result.Outcome);
        Assert.Equal(1, posts);
        Assert.Contains(id, body);
        Assert.Equal(0, (await store.InspectAsync()).PendingCount);
    }

    private static HttpResponseMessage Json(string value) => new(HttpStatusCode.OK) { Content = new StringContent(value, Encoding.UTF8, "application/json") };
    private sealed class Handler(Func<HttpRequestMessage, Task<HttpResponseMessage>> send) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token) => send(request);
    }
}
