using System.Text.Json;
using System.Text.Json.Serialization;

namespace ParkingGateDesktop;

public enum OfflineOperationType
{
    Entry,
    ExitConfirm
}

public sealed record OfflineOperation(
    [property: JsonPropertyName("operationId")] string OperationId,
    [property: JsonPropertyName("type")] OfflineOperationType Type,
    [property: JsonPropertyName("request")] ParkingRequest Request,
    [property: JsonPropertyName("createdAt")] DateTimeOffset CreatedAt,
    [property: JsonPropertyName("lastAttemptAt")] DateTimeOffset? LastAttemptAt = null,
    [property: JsonPropertyName("lastError")] string? LastError = null);

public sealed class OfflineOutboxStore
{
    private readonly SemaphoreSlim _gate = new(1, 1);
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web) { WriteIndented = true };
    private readonly string _path;

    public OfflineOutboxStore(string? path = null)
    {
        _path = path ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ParkingGateDesktop", "offline-outbox.json");
    }

    public async Task<int> EnqueueAsync(OfflineOperationType type, ParkingRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.OperationId))
            throw new InvalidOperationException("Thao tác ngoại tuyến phải có mã nhận diện.");
        if (!string.IsNullOrWhiteSpace(request.RealtimeFaceImageBase64))
            throw new InvalidOperationException(
                "Không lưu ảnh khuôn mặt vào hàng đợi ngoại tuyến. Vui lòng kết nối lại và thực hiện lại.");

        await _gate.WaitAsync();
        try
        {
            List<OfflineOperation> items = await ReadUnsafeAsync();
            if (items.All(item => !string.Equals(item.OperationId, request.OperationId,
                    StringComparison.OrdinalIgnoreCase)))
                items.Add(new OfflineOperation(request.OperationId, type, request,
                    DateTimeOffset.UtcNow));
            await WriteUnsafeAsync(items);
            return items.Count;
        }
        finally { _gate.Release(); }
    }

    public async Task<IReadOnlyList<OfflineOperation>> ReadAsync()
    {
        await _gate.WaitAsync();
        try { return (await ReadUnsafeAsync()).ToList(); }
        finally { _gate.Release(); }
    }

    public async Task RemoveAsync(string operationId)
    {
        await _gate.WaitAsync();
        try
        {
            List<OfflineOperation> items = await ReadUnsafeAsync();
            items.RemoveAll(item => string.Equals(item.OperationId, operationId,
                StringComparison.OrdinalIgnoreCase));
            await WriteUnsafeAsync(items);
        }
        finally { _gate.Release(); }
    }

    public async Task RecordFailureAsync(string operationId, string message)
    {
        await _gate.WaitAsync();
        try
        {
            List<OfflineOperation> items = await ReadUnsafeAsync();
            int index = items.FindIndex(item => string.Equals(item.OperationId, operationId,
                StringComparison.OrdinalIgnoreCase));
            if (index >= 0)
                items[index] = items[index] with {
                    LastAttemptAt = DateTimeOffset.UtcNow,
                    LastError = Truncate(message, 500)
                };
            await WriteUnsafeAsync(items);
        }
        finally { _gate.Release(); }
    }

    private async Task<List<OfflineOperation>> ReadUnsafeAsync()
    {
        if (!File.Exists(_path)) return new List<OfflineOperation>();
        try
        {
            await using FileStream stream = File.OpenRead(_path);
            return await JsonSerializer.DeserializeAsync<List<OfflineOperation>>(stream, _json)
                ?? new List<OfflineOperation>();
        }
        catch (JsonException)
        {
            string backup = _path + ".corrupt-" + DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            File.Move(_path, backup, true);
            return new List<OfflineOperation>();
        }
    }

    private async Task WriteUnsafeAsync(List<OfflineOperation> items)
    {
        string? directory = Path.GetDirectoryName(_path);
        if (directory is null) throw new InvalidOperationException("Đường dẫn outbox không hợp lệ.");
        Directory.CreateDirectory(directory);
        string temporary = _path + ".tmp";
        await using (FileStream stream = new(temporary, FileMode.Create, FileAccess.Write,
            FileShare.None, 4096, FileOptions.WriteThrough))
        {
            await JsonSerializer.SerializeAsync(stream, items, _json);
            await stream.FlushAsync();
        }
        File.Move(temporary, _path, true);
    }

    private static string Truncate(string value, int length) =>
        value.Length <= length ? value : value[..length];
}
