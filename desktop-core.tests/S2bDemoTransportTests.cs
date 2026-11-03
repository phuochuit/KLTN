using System.Net;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace DesktopCore.Tests;

public class S2bDemoTransportTests
{
    [Fact]
    public async Task DemoLoginAndSessionUseRealClientWithFakeTransport()
    {
        using var client = CreateClient(new DemoTransport());
        await client.LoginAsync("demo", "demo");
        var profile = await client.ValidateSessionAsync();
        Assert.Equal("demo", profile.Username);
        await client.LogoutAsync();
        Assert.False(client.IsAuthenticated);
    }

    [Fact]
    public async Task DemoInvalidationAndUnknownRoutesFailInMemory()
    {
        var handler = new DemoTransport();
        using var client = CreateClient(handler);
        await client.LoginAsync("demo", "demo");
        handler.SessionValid = false;
        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.ValidateSessionAsync());
        using var http = new HttpClient(new DemoTransport());
        using var result = await http.GetAsync("https://example.invalid/not-implemented");
        Assert.Equal(HttpStatusCode.Unauthorized, result.StatusCode);
        http.DefaultRequestHeaders.Authorization = new("Bearer", "demo-access");
        using var authenticatedUnknown = await http.GetAsync("https://example.invalid/not-implemented");
        Assert.Equal(HttpStatusCode.NotFound, authenticatedUnknown.StatusCode);
    }

    [Fact]
    public async Task ClientKeepsAuthenticatedOriginBindingWithDemoTransport()
    {
        using var client = CreateClient(new DemoTransport());
        await client.LoginAsync("demo", "demo");
        client.BaseUrl = "https://other.invalid";
        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.ValidateSessionAsync());
    }

    [Fact]
    public async Task DemoRejectsWrongPasswordWithoutAuthenticating()
    {
        using var client = CreateClient(new DemoTransport());
        await Assert.ThrowsAnyAsync<Exception>(() => client.LoginAsync("demo", "wrong"));
        Assert.False(client.IsAuthenticated);
    }

    [Fact]
    public async Task ClientRejectsNonLoopbackHttpBeforeSendingCredentials()
    {
        using var client = CreateClient(new DemoTransport());
        client.BaseUrl = "http://example.invalid";
        await Assert.ThrowsAsync<InvalidOperationException>(() => client.LoginAsync("demo", "demo"));
        Assert.False(client.IsAuthenticated);
    }

    private static ParkingApiClient CreateClient(DemoTransport handler) => new(handler,
        new OfflineOutboxStore(Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString(), "outbox.json")),
        new GateStationSettings()) { BaseUrl = "https://demo.invalid" };
}
