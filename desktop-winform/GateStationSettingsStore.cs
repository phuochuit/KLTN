using System.Text.Json;

namespace ParkingGateDesktop;

public sealed class GateStationSettingsStore
{
    private static readonly JsonSerializerOptions Json = new() { WriteIndented = true };
    private readonly string _path;

    public GateStationSettingsStore(string? path = null)
    {
        _path = path ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ParkingGateDesktop", "station-settings.json");
    }

    public GateStationSettings Load()
    {
        try
        {
            if (!File.Exists(_path)) return FromEnvironment();
            var settings = JsonSerializer.Deserialize<GateStationSettings>(File.ReadAllText(_path))
                ?? FromEnvironment();
            settings.Validate();
            return settings;
        }
        catch (Exception ex) when (ex is IOException or JsonException or ArgumentException)
        {
            return FromEnvironment();
        }
    }

    public void Save(GateStationSettings settings)
    {
        settings.Validate();
        string? directory = Path.GetDirectoryName(_path);
        if (!string.IsNullOrWhiteSpace(directory)) Directory.CreateDirectory(directory);
        string temporary = _path + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(settings, Json));
        File.Move(temporary, _path, true);
    }

    private static GateStationSettings FromEnvironment() => new()
    {
        VehicleCameraSource = Environment.GetEnvironmentVariable("PARKING_VEHICLE_CAMERA_SOURCE")
            ?? Environment.GetEnvironmentVariable("PARKING_VEHICLE_CAMERA_INDEX") ?? "0",
        FaceCameraSource = Environment.GetEnvironmentVariable("PARKING_FACE_CAMERA_SOURCE")
            ?? Environment.GetEnvironmentVariable("PARKING_FACE_CAMERA_INDEX") ?? "0"
    };
}
