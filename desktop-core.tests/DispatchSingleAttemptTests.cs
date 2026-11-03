using System.Net;
using System.Text;
using System.Text.Json;
using ParkingGateDesktop;
using Xunit;

namespace DesktopCore.Tests;

public sealed class DispatchSingleAttemptTests
{
    [Fact]
    public async Task SuccessUsesExactRouteAndOnlySessionIdAndAcceptsEmptyBody()
    {
        var handler = new DispatchHandler(_ => new(HttpStatusCode.NoContent));
        using var client = await Login(handler);

        var result = await client.DispatchSessionSingleAttemptAsync(17, 29);

        Assert.Equal(SingleAttemptOutcome.Success, result.Outcome);
        Assert.Null(result.Response);
        Assert.Single(handler.Dispatches);
        Assert.Equal("/api/parking/slots/17/dispatch-session", handler.Dispatches[0].Path);
        using var body = JsonDocument.Parse(handler.Dispatches[0].Body);
        Assert.Single(body.RootElement.EnumerateObject());
        Assert.Equal("sessionId", body.RootElement.EnumerateObject().Single().Name);
        Assert.Equal(29, body.RootElement.GetProperty("sessionId").GetInt64());
    }

    [Theory]
    [InlineData(0, 3)]
    [InlineData(3, 0)]
    [InlineData(-1, 3)]
    public async Task InvalidIdsAreNotSent(long slotId, long sessionId)
    {
        var handler = new DispatchHandler(_ => new(HttpStatusCode.NoContent));
        using var client = await Login(handler);

        Assert.Equal(SingleAttemptOutcome.NotSent,
            (await client.DispatchSessionSingleAttemptAsync(slotId, sessionId)).Outcome);
        Assert.Empty(handler.Dispatches);
    }

    [Fact]
    public async Task Bearer401IsRejectedAndNeverRefreshesOrResendsDispatch()
    {
        var handler = new DispatchHandler(_ => {
            var response = new HttpResponseMessage(HttpStatusCode.Unauthorized);
            response.Headers.WwwAuthenticate.Add(new("Bearer"));
            return response;
        });
        using var client = await Login(handler);

        Assert.Equal(SingleAttemptOutcome.Rejected,
            (await client.DispatchSessionSingleAttemptAsync(1, 2)).Outcome);
        Assert.Single(handler.Dispatches);
        Assert.Equal(0, handler.Refreshes);
    }

    [Theory]
    [InlineData("{\"message\":\"Ô điều phối không phù hợp\"}", SingleAttemptOutcome.Rejected)]
    [InlineData("{\"message\":\"Yêu cầu đang được xử lý. Vui lòng chờ rồi kiểm tra lại.\"}", SingleAttemptOutcome.Unknown)]
    [InlineData("{\"message\":\"Không hợp lệ\",\"code\":\"bad\"}", SingleAttemptOutcome.Unknown)]
    [InlineData("{\"message\":\"<script>unsafe</script>\"}", SingleAttemptOutcome.Unknown)]
    public async Task BadRequestClassificationRequiresTrustedSingleMessage(string body, SingleAttemptOutcome expected)
    {
        var handler = new DispatchHandler(_ => Json(HttpStatusCode.BadRequest, body));
        using var client = await Login(handler);

        Assert.Equal(expected, (await client.DispatchSessionSingleAttemptAsync(1, 2)).Outcome);
        Assert.Single(handler.Dispatches);
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.InternalServerError)]
    public async Task UntrustedStatusIsUnknownAndNotResent(HttpStatusCode status)
    {
        var handler = new DispatchHandler(_ => new(status));
        using var client = await Login(handler);

        Assert.Equal(SingleAttemptOutcome.Unknown,
            (await client.DispatchSessionSingleAttemptAsync(1, 2)).Outcome);
        Assert.Single(handler.Dispatches);
    }

    [Fact]
    public async Task TransportLossAfterSendIsUnknownAndNotRetried()
    {
        var handler = new DispatchHandler(_ => throw new OperationCanceledException("simulated timeout"));
        using var client = await Login(handler);

        Assert.Equal(SingleAttemptOutcome.Unknown,
            (await client.DispatchSessionSingleAttemptAsync(1, 2)).Outcome);
        Assert.Single(handler.Dispatches);
    }

    private static async Task<ParkingApiClient> Login(DispatchHandler handler)
    {
        string dir = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../.cache/test-temp", Guid.NewGuid().ToString("N")));
        Directory.CreateDirectory(dir);
        var client = new ParkingApiClient(handler, new OfflineOutboxStore(Path.Combine(dir, "legacy.json")), new GateStationSettings())
        { BaseUrl = "https://example.invalid" };
        await client.LoginAsync("operator", "password");
        return client;
    }

    private static HttpResponseMessage Json(HttpStatusCode status, string body) =>
        new(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };

    private sealed class DispatchHandler(Func<int, HttpResponseMessage> dispatch) : HttpMessageHandler
    {
        public List<(string Path, string Body)> Dispatches { get; } = [];
        public int Refreshes { get; private set; }

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            string path = request.RequestUri!.AbsolutePath;
            if (path == "/api/auth/login")
                return Json(HttpStatusCode.OK, "{\"accessToken\":\"token\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"refresh\",\"sessionId\":\"test\",\"absoluteExpiresAt\":\"2099-01-01T00:00:00Z\",\"username\":\"operator\",\"fullName\":\"Operator\"}");
            if (path == "/api/auth/refresh") { Refreshes++; return new(HttpStatusCode.Unauthorized); }
            string body = request.Content is null ? "" : await request.Content.ReadAsStringAsync(token);
            Dispatches.Add((path, body));
            return dispatch(Dispatches.Count);
        }
    }
}
