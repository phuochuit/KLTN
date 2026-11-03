using System.Net.Http;
using System.IO;
using System.Text.Json;
using ParkingGateDesktop;

namespace LumiPark.Desktop.Services;

public static class AuthComposition
{
    public static async Task<AuthServices> ProductionAsync()
    {
        var settingsStore = new GateStationSettingsStore();
        var settings = await Task.Run(settingsStore.Load);
        var legacy = new OfflineOutboxStore();
        var journal = await MutationJournal.OpenAsync(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LumiPark", "Desktop", "pending-mutation.json"));
        try { return new AuthServices(new ParkingApiClient(new HttpClientHandler { AllowAutoRedirect = false }, legacy, settings, true), journal, legacy, settingsStore); }
        catch { await journal.DisposeAsync(); throw; }
    }

    public static async Task<AuthServices> DemoAsync(string fixtureDirectory, DemoTransport transport)
    {
        var settings = await Task.Run(() =>
        {
            var settingsPath = Path.Combine(fixtureDirectory, "station-settings.json");
            if (!File.Exists(settingsPath)) return new GateStationSettings();
            var result = JsonSerializer.Deserialize<GateStationSettings>(File.ReadAllText(settingsPath)) ?? new GateStationSettings();
            result.Validate();
            return result;
        });
        var settingsStore = new GateStationSettingsStore(Path.Combine(fixtureDirectory, "station-settings.json"));
        var legacy = new OfflineOutboxStore(Path.Combine(fixtureDirectory, "legacy-outbox.json"));
        var journal = await MutationJournal.OpenAsync(Path.Combine(fixtureDirectory, "pending-mutation.json"));
        try { return new AuthServices(new ParkingApiClient(transport, legacy, settings, true), journal, legacy, settingsStore); }
        catch { await journal.DisposeAsync(); throw; }
    }

    // Demo fixtures must be a descendant of this checkout's .artifacts directory, without links.
    public static string ValidateFixturePath(string value, string projectRoot)
    {
        if (string.IsNullOrWhiteSpace(value)) throw new ArgumentException("Thiếu thư mục dữ liệu trình diễn.");
        var root = Path.GetFullPath(Path.Combine(projectRoot, ".artifacts"));
        var path = Path.GetFullPath(value);
        if (!path.StartsWith(root + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
            throw new ArgumentException("Thư mục trình diễn phải nằm trong .artifacts của dự án.");
        for (var dir = new DirectoryInfo(path); dir is not null; dir = dir.Parent)
            if (dir.Exists && (dir.Attributes & FileAttributes.ReparsePoint) != 0)
                throw new ArgumentException("Thư mục trình diễn không được chứa liên kết.");
        return path;
    }
}
