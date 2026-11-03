using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace LumiPark.Desktop.Tests;

public sealed class AuthSessionLifetimeTests
{
    [Fact]
    public async Task LogoutInvalidatesAnInFlightInitialSummaryAndOnlyRequestsTheCurrentSummary()
    {
        var folder = Path.Combine(Path.GetTempPath(), "wpf-summary-s3a-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(folder);
        var transport = new DemoTransport();
        var release = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        transport.ShiftSummaryWait = release.Task;
        var legacy = new OfflineOutboxStore(Path.Combine(folder, "legacy.json"));
        var journal = await MutationJournal.OpenAsync(Path.Combine(folder, "journal.json"));
        var services = new AuthServices(new ParkingApiClient(transport, legacy, new GateStationSettings()), journal, legacy,
            new GateStationSettingsStore(Path.Combine(folder, "station-settings.json")));
        var session = new AuthSession(() => Task.FromResult(services));
        var presentations = new List<bool>();
        session.ShiftSummaryRequested = (_, previous) => { presentations.Add(previous); return Task.CompletedTask; };
        try
        {
            var login = session.LoginAsync("demo", "demo");
            await transport.ShiftSummaryRequestStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
            var logout = session.LogoutAsync();
            Assert.True(session.IsLeavingSession);
            release.TrySetResult();
            await Task.WhenAll(login, logout).WaitAsync(TimeSpan.FromSeconds(5));
            Assert.Equal(new[] { false }, presentations);
        }
        finally
        {
            session.Dispose();
            if (Directory.Exists(folder)) Directory.Delete(folder, true);
        }
    }

    [Fact]
    public async Task LogoutWaitsForActiveSettingsSaveBeforeTheNextSessionCanLoadIt()
    {
        var folder = Path.Combine(Path.GetTempPath(), "wpf-session-s3a-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(folder);
        var store = new GateStationSettingsStore(Path.Combine(folder, "station-settings.json"));
        var sequence = 0;
        var session = new AuthSession(async () =>
        {
            var legacy = new OfflineOutboxStore(Path.Combine(folder, $"legacy-{++sequence}.json"));
            var journal = await MutationJournal.OpenAsync(Path.Combine(folder, $"journal-{sequence}.json"));
            return new AuthServices(new ParkingApiClient(new DemoTransport(), legacy, store.Load()), journal, legacy, store);
        });
        try
        {
            await session.LoginAsync("demo", "demo");
            var oldSession = Assert.IsType<AuthServices>(session.ActiveServices);
            var started = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            var release = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            var settings = new GateStationSettings { VehicleCameraSource = "rtsp://camera.example/stream", FaceCameraSource = "2", CameraFrameCount = 12, BarrierAutoCloseSeconds = 9 };
            var save = session.RunInActiveSessionAsync(oldSession, async () =>
            {
                started.TrySetResult();
                await release.Task.WaitAsync(TimeSpan.FromSeconds(5));
                await Task.Run(() => oldSession.SettingsStore.Save(settings));
                oldSession.Client.StationSettings = settings;
            });
            await started.Task.WaitAsync(TimeSpan.FromSeconds(5));
            var logout = session.LogoutAsync();
            Assert.True(session.IsLeavingSession);
            release.TrySetResult();
            Assert.True(await save.WaitAsync(TimeSpan.FromSeconds(5)));
            await logout.WaitAsync(TimeSpan.FromSeconds(5));

            await session.LoginAsync("demo", "demo");
            Assert.Equal("rtsp://camera.example/stream", session.Client!.StationSettings.VehicleCameraSource);
            Assert.Equal(12, session.Client.StationSettings.CameraFrameCount);
        }
        finally
        {
            session.Dispose();
            if (Directory.Exists(folder)) Directory.Delete(folder, true);
        }
    }
}
