using ParkingGateDesktop;

namespace LumiPark.Desktop.Services;

public enum ParkingVehicleFilter { All, Car, Motorbike }

public sealed record ParkingMapSummary(int Total, int Available, int Occupied, int Reserved, int Borrowed, int Overdue);

public sealed record ParkingMapSnapshot(
    IReadOnlyList<ParkingSlotResponse> Slots,
    DateTimeOffset LoadedAt,
    ParkingMapSummary Summary);

/// <summary>Read-only map snapshot and filters. Refresh never affects mutation-journal state.</summary>
public sealed class ParkingMapState : IDisposable
{
    private readonly ParkingApiClient _client;
    private readonly object _sync = new();
    private Task<ParkingMapSnapshot?>? _refresh;
    private long _generation;
    private bool _disposed;

    public ParkingMapState(ParkingApiClient client) => _client = client ?? throw new ArgumentNullException(nameof(client));

    public ParkingMapSnapshot? Snapshot { get; private set; }
    public string? StaleWarning { get; private set; }

    public Task<ParkingMapSnapshot?> RefreshAsync()
    {
        lock (_sync)
        {
            if (_disposed) return Task.FromResult<ParkingMapSnapshot?>(null);
            if (_refresh is not null) return _refresh;
            var completion = new TaskCompletionSource<ParkingMapSnapshot?>(TaskCreationOptions.RunContinuationsAsynchronously);
            _refresh = completion.Task;
            _ = RefreshCoreAsync(_generation, completion);
            return completion.Task;
        }
    }

    private async Task RefreshCoreAsync(long generation, TaskCompletionSource<ParkingMapSnapshot?> completion)
    {
        try
        {
            var slots = await _client.GetSlotsAsync();
            var snapshot = new ParkingMapSnapshot(slots,
                DateTimeOffset.Now,
                new ParkingMapSummary(slots.Count,
                    slots.Count(s => s.Status == "AVAILABLE"),
                    slots.Count(s => s.Status.StartsWith("OCCUPIED", StringComparison.Ordinal)),
                    slots.Count(s => s.Status == "RESERVED_EMPTY"),
                    slots.Count(s => !string.IsNullOrWhiteSpace(s.BorrowedPlate)),
                    slots.Count(s => s.Overdue)));
            lock (_sync)
            {
                if (_disposed || generation != _generation) { completion.TrySetResult(Snapshot); return; }
                Snapshot = snapshot;
                StaleWarning = null;
                completion.TrySetResult(snapshot);
            }
        }
        catch
        {
            lock (_sync)
            {
                if (!_disposed && generation == _generation)
                    StaleWarning = Snapshot is null ? "Chưa thể tải sơ đồ bãi xe. Dữ liệu hiện chưa được xác minh." :
                        "Không thể làm mới sơ đồ. Đang hiển thị dữ liệu cũ.";
                completion.TrySetResult(Snapshot);
            }
        }
        finally
        {
            lock (_sync) if (ReferenceEquals(_refresh, completion.Task)) _refresh = null;
        }
    }

    public IReadOnlyList<ParkingSlotResponse> Filter(string? floor, string? zone, ParkingVehicleFilter vehicle)
    {
        var slots = Snapshot?.Slots ?? Array.Empty<ParkingSlotResponse>();
        return slots.Where(slot =>
            (string.IsNullOrEmpty(floor) || (slot.Floor ?? "Tầng B1") == floor) &&
            (string.IsNullOrEmpty(zone) || slot.ZoneName == zone) &&
            (vehicle == ParkingVehicleFilter.All ||
                (vehicle == ParkingVehicleFilter.Car && slot.AllowedVehicleType != "MOTORBIKE") ||
                (vehicle == ParkingVehicleFilter.Motorbike && slot.AllowedVehicleType == "MOTORBIKE")))
            .ToArray();
    }

    public void Dispose()
    {
        lock (_sync) { _disposed = true; _generation++; }
    }
}
