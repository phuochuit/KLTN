using System.Net;
using System.Text;
using System.Text.Json;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace DesktopCore.Tests;

public class MutationSubmissionTests
{
    private const string Tokens = "{\"accessToken\":\"token\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"refresh\",\"sessionId\":\"test\",\"absoluteExpiresAt\":\"2099-01-01T00:00:00Z\",\"username\":\"operator\",\"fullName\":\"Test\"}";
    private const string Success = "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"OPEN\",\"entryTime\":\"2026-01-01T00:00:00\",\"fee\":0}";
    private static ParkingRequest Request() => new("TEST123", "secret-card", "MOTORBIKE", false, null, false, null, "secret-face", "", "", "", "");
    private static HttpResponseMessage Reply(HttpStatusCode status, string body) => new(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };

    private sealed class Fixture : IAsyncDisposable
    {
        public readonly string Dir = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../.cache/test-temp", Guid.NewGuid().ToString("N")));
        public string Pathname => Path.Combine(Dir, "journal.json");
        public OfflineOutboxStore Legacy { get; }
        public ParkingApiClient Client { get; }
        public MutationJournal Journal = null!;
        public MutationSubmissionCoordinator Coordinator = null!;
        public int Posts;
        public string LoginTokens = Tokens;
        public Func<Task<HttpResponseMessage>> Refresh = () => Task.FromResult(Reply(HttpStatusCode.Unauthorized, "{}"));
        public Func<HttpRequestMessage, Task<HttpResponseMessage>> Business = _ => Task.FromResult(Reply(HttpStatusCode.OK, Success));
        public Fixture(bool preserveAuthenticationOnTransientRefreshFailure = false)
        {
            Directory.CreateDirectory(Dir);
            Legacy = new OfflineOutboxStore(Path.Combine(Dir, "legacy.json"));
            Client = new ParkingApiClient(new Handler(async r => {
                if (r.RequestUri!.AbsolutePath == "/api/auth/login") return Reply(HttpStatusCode.OK, LoginTokens);
                if (r.RequestUri.AbsolutePath == "/api/auth/refresh") return await Refresh();
                Posts++;
                return await Business(r);
            }), Legacy, new GateStationSettings(), preserveAuthenticationOnTransientRefreshFailure) { BaseUrl = "https://example.invalid" };
        }
        public async Task Start()
        {
            await Client.LoginAsync("operator", "password");
            Journal = await MutationJournal.OpenAsync(Pathname);
            Coordinator = new(Client, Journal, Legacy);
        }
        public async ValueTask DisposeAsync() { if (Journal != null) await Journal.DisposeAsync(); Client.Dispose(); }
    }

    [Fact]
    public async Task TransientRefreshFailureBeforeDispatchIsNotSentAndRetiresJournal()
    {
        await using var f = new Fixture(preserveAuthenticationOnTransientRefreshFailure: true);
        f.LoginTokens = Tokens.Replace("\"expiresIn\":900", "\"expiresIn\":1");
        int refreshes = 0;
        f.Refresh = () => { refreshes++; return Task.FromResult(Reply(HttpStatusCode.ServiceUnavailable, "{}")); };
        await f.Start();
        await Task.Delay(TimeSpan.FromMilliseconds(1100));

        var result = await f.Coordinator.SubmitAsync(MutationKind.Entry, Request());

        Assert.Equal(SingleAttemptOutcome.NotSent, result.Outcome);
        Assert.True(f.Client.IsAuthenticated);
        Assert.Equal(1, refreshes);
        Assert.Equal(0, f.Posts);
        Assert.False(File.Exists(f.Pathname));
        Assert.False((await f.Coordinator.InspectAsync()).IsHeld);
    }
    private sealed class Handler(Func<HttpRequestMessage, Task<HttpResponseMessage>> send) : HttpMessageHandler
    { protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage r, CancellationToken ct) => send(r); }

    [Theory]
    [InlineData(500, "{}")]
    [InlineData(200, "{broken")]
    [InlineData(200, "{}")]
    [InlineData(409, "{}")]
    [InlineData(400, "{\"message\":\"Yêu cầu đang được xử lý. Vui lòng chờ rồi kiểm tra lại.\"}")]
    [InlineData(400, "{\"message\":\"Yêu cầu đang được xử lý ở một trạm khác. Vui lòng chờ rồi kiểm tra lại.\"}")]
    [InlineData(408, "{\"message\":\"timeout\"}")]
    [InlineData(425, "{\"message\":\"retry\"}")]
    [InlineData(429, "{\"message\":\"retry\"}")]
    [InlineData(400, "<html>bad request</html>")]
    [InlineData(400, "{}")]
    [InlineData(400, "{\"message\":\"proxy error\",\"proxy\":true}")]
    public async Task AmbiguousResponseHoldsAcrossRestart(int status, string body)
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = _ => Task.FromResult(Reply((HttpStatusCode)status, body));
        Assert.Equal(SingleAttemptOutcome.Unknown, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
        await f.Journal.DisposeAsync();
        f.Journal = await MutationJournal.OpenAsync(f.Pathname);
        f.Coordinator = new(f.Client, f.Journal, f.Legacy);
        Assert.Equal(SingleAttemptOutcome.NotSent, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(1, f.Posts);
    }

    [Fact]
    public async Task TimeoutHoldsAndJournalContainsOnlyMetadataWithMatchingUuid()
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = async r => {
            string body = await r.Content!.ReadAsStringAsync();
            string metadata = await File.ReadAllTextAsync(f.Pathname);
            using var parsed = JsonDocument.Parse(body);
            Assert.Equal("/api/parking/exit-confirm", r.RequestUri!.AbsolutePath);
            Assert.Equal("secret-face", parsed.RootElement.GetProperty("realtimeFaceImageBase64").GetString());
            Assert.Contains(parsed.RootElement.GetProperty("operationId").GetString()!, metadata, StringComparison.OrdinalIgnoreCase);
            Assert.DoesNotContain("secret", metadata);
            Assert.DoesNotContain("TEST123", metadata);
            Assert.DoesNotContain("token", metadata);
            throw new OperationCanceledException();
        };
        Assert.Equal(SingleAttemptOutcome.Unknown, (await f.Coordinator.SubmitAsync(MutationKind.ExitConfirm, Request())).Outcome);
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
        Assert.Equal(0, (await f.Legacy.InspectAsync()).PendingCount);
    }

    [Fact]
    public async Task LegacyQuarantineAndUnreadableQueueHold()
    {
        await using var f = new Fixture(); await f.Start();
        string legacyPath = Path.Combine(f.Dir, "legacy.json");
        await File.WriteAllTextAsync(legacyPath + ".corrupt-test", "untouched");
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
        File.Delete(legacyPath + ".corrupt-test");
        Directory.CreateDirectory(legacyPath);
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
        Assert.Equal(SingleAttemptOutcome.NotSent, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(0, f.Posts);
    }

    [Fact]
    public async Task RejectionRetiresAndAllowsCorrectedAttempt()
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = _ => Task.FromResult(Reply(HttpStatusCode.BadRequest, "{\"message\":\"Biển số không hợp lệ\"}"));
        var rejection = await f.Coordinator.SubmitAsync(MutationKind.Entry, Request());
        Assert.Equal(SingleAttemptOutcome.Rejected, rejection.Outcome);
        Assert.Equal("Biển số không hợp lệ", rejection.Message);
        Assert.False((await f.Coordinator.InspectAsync()).IsHeld);
        f.Business = _ => Task.FromResult(Reply(HttpStatusCode.OK, Success));
        var success = await f.Coordinator.SubmitAsync(MutationKind.Entry, Request());
        Assert.Equal(1, success.Response!.SessionId);
        Assert.False((await f.Coordinator.InspectAsync()).IsHeld);
        Assert.Equal(2, f.Posts);
    }

    [Fact]
    public async Task UnauthorizedNeverResubmitsBusiness()
    {
        await using var f = new Fixture(); await f.Start();
        f.Refresh = () => throw new HttpRequestException("refresh unavailable");
        f.Business = _ => { var response = Reply(HttpStatusCode.Unauthorized, ""); response.Headers.WwwAuthenticate.ParseAdd("Bearer error=\"invalid_token\""); return Task.FromResult(response); };
        Assert.Equal(SingleAttemptOutcome.Rejected, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.False((await f.Coordinator.InspectAsync()).IsHeld);
        Assert.Equal(1, f.Posts);
    }

    [Fact]
    public async Task NonJsonErrorContentHoldsEvenWhenBodyLooksLikeContract()
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = _ => Task.FromResult(new HttpResponseMessage(HttpStatusCode.BadRequest)
            { Content = new StringContent("{\"message\":\"proxy error\"}", Encoding.UTF8, "text/html") });
        Assert.Equal(SingleAttemptOutcome.Unknown, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
    }

    [Theory]
    [InlineData(MutationKind.Entry, "{\"sessionId\":1,\"plateNumber\":\"OTHER\",\"status\":\"OPEN\",\"entryTime\":\"2026-01-01T00:00:00\",\"fee\":0}")]
    [InlineData(MutationKind.Entry, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"PREVIEW\",\"entryTime\":\"2026-01-01T00:00:00\",\"fee\":0}")]
    [InlineData(MutationKind.Entry, "{\"sessionId\":1,\"status\":\"OPEN\",\"entryTime\":\"2026-01-01T00:00:00\",\"fee\":0}")]
    [InlineData(MutationKind.Entry, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"entryTime\":\"2026-01-01T00:00:00\",\"fee\":0}")]
    [InlineData(MutationKind.Entry, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"OPEN\",\"entryTime\":\"bad\",\"fee\":0}")]
    [InlineData(MutationKind.Entry, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"OPEN\",\"entryTime\":\"2026-01-01T00:00:00\"}")]
    [InlineData(MutationKind.Entry, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"OPEN\",\"entryTime\":\"2026-01-01T00:00:00\",\"exitTime\":\"2026-01-02T00:00:00\",\"fee\":0}")]
    [InlineData(MutationKind.ExitConfirm, "{\"sessionId\":1,\"plateNumber\":\"OTHER\",\"status\":\"COMPLETED\",\"entryTime\":\"2026-01-01T00:00:00\",\"exitTime\":\"2026-01-02T00:00:00\",\"fee\":5}")]
    [InlineData(MutationKind.ExitConfirm, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"PREVIEW\",\"entryTime\":\"2026-01-01T00:00:00\",\"exitTime\":\"2026-01-02T00:00:00\",\"fee\":5}")]
    [InlineData(MutationKind.ExitConfirm, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"entryTime\":\"2026-01-01T00:00:00\",\"exitTime\":\"2026-01-02T00:00:00\",\"fee\":5}")]
    [InlineData(MutationKind.ExitConfirm, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"COMPLETED\",\"entryTime\":\"2026-01-01T00:00:00\",\"fee\":5}")]
    [InlineData(MutationKind.ExitConfirm, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"COMPLETED\",\"entryTime\":\"2026-01-02T00:00:00\",\"exitTime\":\"2026-01-01T00:00:00\",\"fee\":5}")]
    [InlineData(MutationKind.ExitConfirm, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"COMPLETED\",\"entryTime\":\"2026-01-01T00:00:00\",\"exitTime\":\"2026-01-02T00:00:00\",\"fee\":-1}")]
    [InlineData(MutationKind.ExitConfirm, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"COMPLETED\",\"entryTime\":\"2026-01-01T00:00:00\",\"exitTime\":\"2026-01-02T00:00:00\"}")]
    public async Task NonAuthoritativeSuccessHolds(MutationKind kind, string body)
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = _ => Task.FromResult(Reply(HttpStatusCode.OK, body));
        Assert.Equal(SingleAttemptOutcome.Unknown, (await f.Coordinator.SubmitAsync(kind, Request())).Outcome);
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
    }

    [Fact]
    public async Task CompletedExitWithNormalizedPlateRetiresJournal()
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = _ => Task.FromResult(Reply(HttpStatusCode.OK, "{\"sessionId\":1,\"plateNumber\":\"TEST123\",\"status\":\"COMPLETED\",\"entryTime\":\"2026-01-01T00:00:00\",\"exitTime\":\"2026-01-02T00:00:00\",\"fee\":5}"));
        Assert.Equal(SingleAttemptOutcome.Success, (await f.Coordinator.SubmitAsync(MutationKind.ExitConfirm, Request() with { PlateNumber = "test-123" })).Outcome);
        Assert.False((await f.Coordinator.InspectAsync()).IsHeld);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task PreSendOriginOrExpiredAuthDoesNotHold(bool expired)
    {
        await using var f = new Fixture();
        if (expired) f.LoginTokens = Tokens.Replace("\"expiresIn\":900", "\"expiresIn\":1");
        await f.Start();
        if (!expired) f.Client.BaseUrl = "https://other.invalid";
        Assert.Equal(SingleAttemptOutcome.NotSent, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(0, f.Posts);
        Assert.False((await f.Coordinator.InspectAsync()).IsHeld);
    }

    [Fact]
    public async Task NetworkFailureAfterDispatchHolds()
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = _ => throw new HttpRequestException("network failure");
        Assert.Equal(SingleAttemptOutcome.Unknown, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(1, f.Posts);
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
    }

    [Fact]
    public async Task CorruptAndLegacyPendingHoldWithoutModification()
    {
        await using var f = new Fixture(); await f.Start();
        await File.WriteAllTextAsync(f.Pathname, "broken");
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
        Assert.Equal("broken", await File.ReadAllTextAsync(f.Pathname));
        File.Delete(f.Pathname);
        await f.Legacy.EnqueueAsync(OfflineOperationType.Entry, Request() with { OperationId = Guid.NewGuid().ToString(), RealtimeFaceImageBase64 = "" });
        Assert.Equal(SingleAttemptOutcome.NotSent, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(0, f.Posts);
    }

    [Fact]
    public async Task WriteFailurePreventsPostAndLockIsExclusive()
    {
        await using var f = new Fixture(); await f.Start();
        await Assert.ThrowsAnyAsync<IOException>(() => MutationJournal.OpenAsync(f.Pathname));
        Directory.CreateDirectory(f.Pathname);
        Assert.Equal(SingleAttemptOutcome.NotSent, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(0, f.Posts);
    }

    [Fact]
    public async Task LegacyQueueCannotShareTheJournalPath()
    {
        await using var f = new Fixture(); await f.Start();
        Assert.Throws<ArgumentException>(() => new MutationSubmissionCoordinator(f.Client, f.Journal, new OfflineOutboxStore(f.Pathname)));
        Assert.Equal(0, f.Posts);
    }

    [Fact]
    public async Task CleanupFailureKeepsKnownSuccessAndHolds()
    {
        await using var f = new Fixture(); await f.Start();
        f.Business = _ => {
            File.Delete(f.Pathname);
            Directory.CreateDirectory(f.Pathname);
            return Task.FromResult(Reply(HttpStatusCode.OK, Success));
        };
        var result = await f.Coordinator.SubmitAsync(MutationKind.Entry, Request());
        Assert.Equal(SingleAttemptOutcome.Success, result.Outcome);
        Assert.Equal(1, result.Response!.SessionId);
        Assert.True((await f.Coordinator.InspectAsync()).IsHeld);
        Assert.Equal(SingleAttemptOutcome.NotSent, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        Assert.Equal(1, f.Posts);
    }

    [Fact]
    public async Task ConcurrentSubmissionDoesNotSendSecondPost()
    {
        await using var f = new Fixture(); await f.Start();
        var entered = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var release = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        f.Business = async _ => { entered.SetResult(); await release.Task; return Reply(HttpStatusCode.OK, Success); };
        var first = f.Coordinator.SubmitAsync(MutationKind.Entry, Request());
        await entered.Task;
        Assert.Equal(SingleAttemptOutcome.NotSent, (await f.Coordinator.SubmitAsync(MutationKind.Entry, Request())).Outcome);
        release.SetResult();
        Assert.Equal(SingleAttemptOutcome.Success, (await first).Outcome);
        Assert.Equal(1, f.Posts);
    }
}
