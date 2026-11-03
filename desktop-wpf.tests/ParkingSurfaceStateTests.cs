using System.Collections.Concurrent;
using System.Net;
using System.Text;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace LumiPark.Desktop.Tests;

public sealed class ParkingSurfaceStateTests
{
    [Fact]
    public async Task MapRefreshFailureKeepsSnapshotAndCarFilterIncludesEveryNonMotorbikeType()
    {
        var handler = new SurfaceHandler { FailMapAfterFirst = true };
        using var fixture = await SurfaceFixture.Create(handler);
        using var map = new ParkingMapState(fixture.Client);

        var first = await map.RefreshAsync();
        Assert.NotNull(first);
        Assert.Equal(new ParkingMapSummary(3, 1, 1, 1, 1, 1), first!.Summary);
        Assert.Equal(new long[] { 1, 3 }, map.Filter(null, null, ParkingVehicleFilter.Car).Select(s => s.Id));

        var refreshed = await map.RefreshAsync();

        Assert.Same(first, refreshed);
        Assert.Equal(first.LoadedAt, map.Snapshot!.LoadedAt);
        Assert.Contains("dữ liệu cũ", map.StaleWarning!, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task ConcurrentMapRefreshesShareOneReadAndDisposedStateDiscardsLateResult()
    {
        var handler = new SurfaceHandler { DelayMap = true };
        using var fixture = await SurfaceFixture.Create(handler);
        var map = new ParkingMapState(fixture.Client);

        var first = map.RefreshAsync();
        await handler.MapStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        var second = map.RefreshAsync();
        Assert.Same(first, second);
        Assert.Equal(1, handler.MapReads);
        map.Dispose();
        handler.ReleaseMap.TrySetResult();

        Assert.Null(await first);
        Assert.Null(map.Snapshot);
    }

    [Fact]
    public async Task HealthRefreshIsReadOnlyAndExposesUnverifiedFailuresInsteadOfZeroOrHealthy()
    {
        var handler = new SurfaceHandler { FailHealthReads = true };
        using var fixture = await SurfaceFixture.Create(handler);
        var journal = await MutationJournal.OpenAsync(Path.Combine(fixture.Directory, "journal.json"));
        using var services = new AuthServices(fixture.Client, journal,
            new OfflineOutboxStore(Path.Combine(fixture.Directory, "legacy.json")));
        using var health = new HealthSurface(services);

        var snapshot = await health.RefreshAsync();

        Assert.NotNull(snapshot);
        Assert.Null(snapshot!.SpringHealthy);
        Assert.Null(snapshot.AiHealthy);
        Assert.Equal(0, snapshot.LegacyPendingCount);
        Assert.NotNull(snapshot.JournalReview);
        Assert.False(snapshot.JournalReview!.IsHeld);
        Assert.DoesNotContain(handler.Paths, path => path.Contains("flush", StringComparison.OrdinalIgnoreCase));
        Assert.Equal(1, handler.Methods.Count(method => method == HttpMethod.Post)); // login only
        await health.RefreshAsync();
        Assert.Equal(4, handler.HealthReads);
    }

    [Fact]
    public async Task HealthInspectLeavesCorruptLegacyQueueUntouchedAndShowsCountUnverifiedAndHeld()
    {
        var handler = new SurfaceHandler();
        using var fixture = await SurfaceFixture.Create(handler);
        string legacyPath = Path.Combine(fixture.Directory, "legacy.json");
        byte[] corrupt = Encoding.UTF8.GetBytes("{not-valid-json");
        await File.WriteAllBytesAsync(legacyPath, corrupt);
        var journal = await MutationJournal.OpenAsync(Path.Combine(fixture.Directory, "journal.json"));
        using var services = new AuthServices(fixture.Client, journal, new OfflineOutboxStore(legacyPath));
        using var health = new HealthSurface(services);

        var snapshot = await health.RefreshAsync();

        Assert.NotNull(snapshot);
        Assert.Null(snapshot!.LegacyPendingCount);
        Assert.True(snapshot.JournalReview?.IsHeld);
        Assert.True(File.Exists(legacyPath));
        Assert.Equal(corrupt, await File.ReadAllBytesAsync(legacyPath));
        Assert.Empty(Directory.GetFiles(fixture.Directory, "legacy.json.corrupt-*"));
        Assert.DoesNotContain(handler.Paths, path => path.Contains("flush", StringComparison.OrdinalIgnoreCase));
        Assert.Equal(1, handler.Methods.Count(method => method == HttpMethod.Post)); // login only
    }

    private sealed class SurfaceFixture : IDisposable
    {
        private SurfaceFixture(string directory, ParkingApiClient client) { Directory = directory; Client = client; }
        public string Directory { get; }
        public ParkingApiClient Client { get; }
        public static async Task<SurfaceFixture> Create(SurfaceHandler handler)
        {
            string directory = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../.cache/test-temp", Guid.NewGuid().ToString("N")));
            System.IO.Directory.CreateDirectory(directory);
            var client = new ParkingApiClient(handler, new OfflineOutboxStore(Path.Combine(directory, "legacy.json")), new GateStationSettings())
            { BaseUrl = "https://example.invalid" };
            await client.LoginAsync("operator", "password");
            return new(directory, client);
        }
        public void Dispose() => Client.Dispose();
    }

    private sealed class SurfaceHandler : HttpMessageHandler
    {
        private int _mapReads;
        private int _healthReads;
        public bool FailMapAfterFirst { get; init; }
        public bool DelayMap { get; init; }
        public bool FailHealthReads { get; init; }
        public int MapReads => Volatile.Read(ref _mapReads);
        public int HealthReads => Volatile.Read(ref _healthReads);
        public TaskCompletionSource MapStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource ReleaseMap { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public ConcurrentQueue<string> Paths { get; } = new();
        public ConcurrentQueue<HttpMethod> Methods { get; } = new();

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            string path = request.RequestUri!.AbsolutePath;
            Paths.Enqueue(path); Methods.Enqueue(request.Method);
            if (path == "/api/auth/login")
                return Json(HttpStatusCode.OK, "{\"accessToken\":\"token\",\"tokenType\":\"Bearer\",\"expiresIn\":900,\"refreshToken\":\"refresh\",\"sessionId\":\"test\",\"absoluteExpiresAt\":\"2099-01-01T00:00:00Z\",\"username\":\"operator\",\"fullName\":\"Operator\"}");
            if (path == "/api/parking/slots")
            {
                int read = Interlocked.Increment(ref _mapReads);
                if (DelayMap) { MapStarted.TrySetResult(); await ReleaseMap.Task.WaitAsync(token); }
                if (FailMapAfterFirst && read > 1) throw new HttpRequestException("read failed");
                return Json(HttpStatusCode.OK, """
                    [
                      {"id":1,"slotCode":"A1","zoneName":"A","floor":"B1","allowedVehicleType":"CAR_6_7","status":"AVAILABLE","borrowedPlate":null,"overdue":false},
                      {"id":2,"slotCode":"A2","zoneName":"A","floor":"B1","allowedVehicleType":"MOTORBIKE","status":"OCCUPIED_OPEN","borrowedPlate":"ABC123","overdue":true},
                      {"id":3,"slotCode":"B1","zoneName":"B","floor":null,"allowedVehicleType":"BICYCLE_ELECTRIC_BICYCLE","status":"RESERVED_EMPTY","borrowedPlate":null,"overdue":false}
                    ]
                    """);
            }
            if (path == "/api/parking/health" || path == "/api/ai/health")
            {
                Interlocked.Increment(ref _healthReads);
                if (FailHealthReads) throw new HttpRequestException("health unavailable");
                return Json(HttpStatusCode.OK, "{}");
            }
            return new(HttpStatusCode.NotFound);
        }

        private static HttpResponseMessage Json(HttpStatusCode status, string body) =>
            new(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
    }
}
