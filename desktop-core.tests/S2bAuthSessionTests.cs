using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace DesktopCore.Tests;

public class S2bAuthSessionTests
{
    [Fact]
    public async Task StartupAndBlankInputDoNotInvokeFactory()
    {
        int calls = 0;
        using var session = new AuthSession(() => { calls++; throw new Exception(); });
        Assert.False(session.WorkspaceVisible);
        await session.LoginAsync("", "");
        Assert.Equal(0, calls);
    }

    private static string Fixture() => Path.Combine(Path.GetTempPath(), "lumipark-auth-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public async Task DemoLoginFailureSuccessAndReloginUseSameHandler()
    {
        var handler = new DemoTransport(); var path = Fixture();
        using var session = new AuthSession(() => AuthComposition.DemoAsync(path, handler)) { BaseUrl = "https://demo.invalid" };
        await session.LoginAsync("demo", "wrong");
        Assert.True(session.LoginVisible);
        Assert.NotEmpty(session.Error);
        await session.LoginAsync("demo", "demo");
        Assert.Equal("demo", session.Profile?.Username);
        Assert.NotNull(session.Profile?.ActiveShift);
        await session.LogoutAsync();
        Assert.True(session.LoginVisible);
        await session.LoginAsync("demo", "demo");
        Assert.True(session.WorkspaceVisible);
    }

    [Fact]
    public async Task DuplicateLoginIsSingleFlight()
    {
        var ready = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        int calls = 0; var handler = new DemoTransport(); var path = Fixture();
        using var session = new AuthSession(async () => { Interlocked.Increment(ref calls); await ready.Task; return await AuthComposition.DemoAsync(path, handler); }) { BaseUrl = "https://demo.invalid" };
        var first = session.LoginAsync("demo", "demo");
        await session.LoginAsync("demo", "demo");
        ready.SetResult(); await first;
        Assert.Equal(1, calls);
        Assert.True(session.WorkspaceVisible);
    }

    [Fact]
    public async Task TransientSessionFailureRetainsSessionAndRevocationEndsIt()
    {
        var fake = new DemoTransport(); var handler = new SwitchableHandler(fake); var path = Fixture();
        using var session = new AuthSession(async () => {
            var legacy = new OfflineOutboxStore(Path.Combine(path, "legacy.json"));
            return new AuthServices(new ParkingApiClient(handler, legacy, new GateStationSettings()), await MutationJournal.OpenAsync(Path.Combine(path, "pending.json")), legacy);
        }) { BaseUrl = "https://demo.invalid" };
        await session.LoginAsync("demo", "demo");
        handler.Fail = true; await session.PollSessionAsync();
        Assert.True(session.WorkspaceVisible);
        Assert.StartsWith("Chưa xác minh", session.Connection);
        handler.Fail = false; fake.SessionValid = false; await session.PollSessionAsync();
        Assert.True(session.LoginVisible);
        int count = handler.Requests; await session.PollSessionAsync(); Assert.Equal(count, handler.Requests);
    }

    [Fact]
    public async Task LogoutAndDisposePreventFurtherPollsAndHonorVeto()
    {
        var fake = new DemoTransport(); var handler = new SwitchableHandler(fake); var path = Fixture();
        var session = new AuthSession(async () => {
            var legacy = new OfflineOutboxStore(Path.Combine(path, "legacy.json"));
            return new AuthServices(new ParkingApiClient(handler, legacy, new GateStationSettings()), await MutationJournal.OpenAsync(Path.Combine(path, "pending.json")), legacy);
        }) { BaseUrl = "https://demo.invalid" };
        await session.LoginAsync("demo", "demo");
        session.CanLeaveWorkspace = () => false; await session.LogoutAsync(); Assert.True(session.WorkspaceVisible);
        session.CanLeaveWorkspace = () => true; await session.LogoutAsync();
        int count = handler.Requests; await session.PollSessionAsync(); Assert.Equal(count, handler.Requests);
        session.Dispose(); await session.PollSessionAsync(); Assert.Equal(count, handler.Requests);
    }

    [Fact]
    public async Task DemoUsesOnlyExplicitFixtureAndRejectsUnsafePath()
    {
        var path = Fixture(); var handler = new DemoTransport();
        using var session = new AuthSession(() => AuthComposition.DemoAsync(path, handler)) { BaseUrl = "https://demo.invalid" };
        Assert.False(Directory.Exists(path));
        await session.LoginAsync("demo", "demo");
        Assert.True(File.Exists(Path.Combine(path, "pending-mutation.json.lock")));
        Assert.False(File.Exists(Path.Combine(path, "legacy-outbox.json")));
        Assert.Throws<ArgumentException>(() => AuthComposition.ValidateFixturePath(Path.Combine(path, "outside"), Path.Combine(path, "project")));
    }

    [Fact]
    public async Task CloseDuringLoginDoesNotInstallSession()
    {
        var ready = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var path = Fixture(); var fake = new DemoTransport();
        var session = new AuthSession(async () => { await ready.Task; return await AuthComposition.DemoAsync(path, fake); }) { BaseUrl = "https://demo.invalid" };
        var login = session.LoginAsync("demo", "demo");
        var close = session.CloseAsync(); ready.SetResult(); await login;
        Assert.True(await close);
        Assert.True(session.LoginVisible);
        await session.PollSessionAsync();
    }

    [Fact]
    public async Task LoginUsesSubmittedOriginEvenIfFactoryIsDelayed()
    {
        var ready = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var recorder = new RecordingHandler(new DemoTransport()); var path = Fixture();
        var session = new AuthSession(async () =>
        {
            await ready.Task;
            var legacy = new OfflineOutboxStore(Path.Combine(path, "legacy.json"));
            return new AuthServices(new ParkingApiClient(recorder, legacy, new GateStationSettings()),
                await MutationJournal.OpenAsync(Path.Combine(path, "pending.json")), legacy);
        }) { BaseUrl = "https://first.invalid" };
        var login = session.LoginAsync("demo", "demo");
        session.BaseUrl = "https://changed.invalid";
        ready.SetResult(); await login;
        Assert.All(recorder.RequestUris, uri => Assert.Equal("first.invalid", uri.Host));
        session.Dispose();
    }

    [Fact]
    public async Task ReloginWaitsUntilLogoutHasReleasedJournal()
    {
        var gate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var handler = new DemoTransport(); int factoryCalls = 0; var path = Fixture();
        var session = new AuthSession(() => { factoryCalls++; return AuthComposition.DemoAsync(path, handler); }) { BaseUrl = "https://demo.invalid" };
        await session.LoginAsync("demo", "demo"); handler.LogoutWait = gate.Task;
        var logout = session.LogoutAsync(); await Task.Yield();
        var relogin = session.LoginAsync("demo", "demo"); await Task.Delay(20);
        Assert.Equal(1, factoryCalls); Assert.True(session.IsBusy);
        gate.SetResult(); await Task.WhenAll(logout, relogin);
        Assert.Equal(2, factoryCalls); Assert.True(session.WorkspaceVisible);
        session.Dispose();
    }

    [Fact]
    public async Task OptInRefreshNetworkFailureRetainsAuthenticationButLegacyDefaultClearsIt()
    {
        using var legacy = CreateClient(new RefreshFailureHandler(new DemoTransport { AccessTokenLifetimeSeconds = 1 }), false);
        await legacy.LoginAsync("demo", "demo");
        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => legacy.ValidateSessionAsync());
        Assert.False(legacy.IsAuthenticated);

        using var optedIn = CreateClient(new RefreshFailureHandler(new DemoTransport { AccessTokenLifetimeSeconds = 1 }), true);
        await optedIn.LoginAsync("demo", "demo");
        var ex = await Assert.ThrowsAsync<TransientServerException>(() => optedIn.ValidateSessionAsync());
        Assert.True(TransientServerException.IsTransient(ex.StatusCode)); Assert.True(optedIn.IsAuthenticated);
    }

    [Fact]
    public async Task OptInStillClearsAuthenticationOnInvalidRefresh()
    {
        using var client = CreateClient(new DemoTransport { AccessTokenLifetimeSeconds = 1, RefreshStatus = System.Net.HttpStatusCode.Unauthorized }, true);
        await client.LoginAsync("demo", "demo");
        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => client.ValidateSessionAsync());
        Assert.False(client.IsAuthenticated);
    }

    [Fact]
    public async Task OptInRetainsOnRefreshServerErrorButClearsMalformedRefresh()
    {
        using var outage = CreateClient(new DemoTransport { AccessTokenLifetimeSeconds = 1, RefreshStatus = System.Net.HttpStatusCode.NotImplemented }, true);
        await outage.LoginAsync("demo", "demo");
        await Assert.ThrowsAsync<TransientServerException>(() => outage.ValidateSessionAsync());
        Assert.True(outage.IsAuthenticated);

        using var malformed = CreateClient(new DemoTransport { AccessTokenLifetimeSeconds = 1, MalformedRefresh = true }, true);
        await malformed.LoginAsync("demo", "demo");
        await Assert.ThrowsAsync<DesktopAuthenticationException>(() => malformed.ValidateSessionAsync());
        Assert.False(malformed.IsAuthenticated);
    }

    private static ParkingApiClient CreateClient(HttpMessageHandler handler, bool preserve) => new(handler,
        new OfflineOutboxStore(Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString(), "outbox.json")),
        new GateStationSettings(), preserve) { BaseUrl = "https://demo.invalid" };

    private sealed class RecordingHandler : DelegatingHandler
    {
        public List<Uri> RequestUris { get; } = new();
        public RecordingHandler(HttpMessageHandler inner) : base(inner) { }
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        { RequestUris.Add(request.RequestUri!); return base.SendAsync(request, cancellationToken); }
    }

    private sealed class RefreshFailureHandler : DelegatingHandler
    {
        public RefreshFailureHandler(HttpMessageHandler inner) : base(inner) { }
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (request.RequestUri?.AbsolutePath == "/api/auth/refresh") throw new HttpRequestException("in-memory synthetic outage");
            return base.SendAsync(request, cancellationToken);
        }
    }

    private sealed class SwitchableHandler : HttpMessageHandler
    {
        private readonly HttpMessageInvoker inner;
        public bool Fail { get; set; }
        public int Requests { get; private set; }
        public SwitchableHandler(DemoTransport handler) => inner = new HttpMessageInvoker(handler, false);
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Requests++;
            if (Fail) throw new HttpRequestException("synthetic transient error");
            return inner.SendAsync(request, cancellationToken);
        }
    }
}
