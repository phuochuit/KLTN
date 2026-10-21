using ParkingGateDesktop;
using Xunit;

namespace ParkingGateDesktop.Tests;

public sealed class OfflineOutboxStoreTest : IDisposable
{
    private readonly string _directory = Path.Combine(Path.GetTempPath(),
        "parking-outbox-tests-" + Guid.NewGuid().ToString("N"));
    private string PathName => Path.Combine(_directory, "offline-outbox.json");

    [Fact]
    public async Task WritesAtomicallyAndDoesNotDuplicateOperationId()
    {
        var store = new OfflineOutboxStore(PathName);
        ParkingRequest request = Request(Guid.NewGuid().ToString());

        Assert.Equal(1, await store.EnqueueAsync(OfflineOperationType.Entry, request));
        Assert.Equal(1, await store.EnqueueAsync(OfflineOperationType.Entry, request));

        IReadOnlyList<OfflineOperation> items = await store.ReadAsync();
        Assert.Single(items);
        Assert.Equal(request.OperationId, items[0].OperationId);
        Assert.True(File.Exists(PathName));
        Assert.False(File.Exists(PathName + ".tmp"));
    }

    [Fact]
    public async Task RefusesToPersistBase64FaceImage()
    {
        var store = new OfflineOutboxStore(PathName);
        ParkingRequest request = Request(Guid.NewGuid().ToString()) with {
            RealtimeFaceImageBase64 = "data:image/jpeg;base64,secret-image"
        };

        InvalidOperationException error = await Assert.ThrowsAsync<InvalidOperationException>(
            () => store.EnqueueAsync(OfflineOperationType.Entry, request));

        Assert.Contains("Không lưu ảnh", error.Message);
        Assert.False(File.Exists(PathName));
    }

    [Fact]
    public async Task QuarantinesCorruptFileInsteadOfOverwritingIt()
    {
        Directory.CreateDirectory(_directory);
        await File.WriteAllTextAsync(PathName, "not-json");
        var store = new OfflineOutboxStore(PathName);

        IReadOnlyList<OfflineOperation> items = await store.ReadAsync();

        Assert.Empty(items);
        Assert.False(File.Exists(PathName));
        Assert.Single(Directory.GetFiles(_directory, "offline-outbox.json.corrupt-*"));
    }

    [Fact]
    public async Task RecordsFailureWithoutLosingPayload()
    {
        var store = new OfflineOutboxStore(PathName);
        ParkingRequest request = Request(Guid.NewGuid().ToString());
        await store.EnqueueAsync(OfflineOperationType.ExitConfirm, request);

        await store.RecordFailureAsync(request.OperationId!, new string('x', 700));

        OfflineOperation item = Assert.Single(await store.ReadAsync());
        Assert.Equal(500, item.LastError!.Length);
        Assert.NotNull(item.LastAttemptAt);
        Assert.Equal(request.PlateNumber, item.Request.PlateNumber);
    }

    private static ParkingRequest Request(string operationId) => new(
        "59A112345", "CARD001", "MOTORBIKE", false, null, true, 0.9,
        "", "vehicle-evidence", "face-evidence", "", "", null, null, operationId);

    public void Dispose()
    {
        if (Directory.Exists(_directory)) Directory.Delete(_directory, true);
    }
}
