namespace LumiPark.Desktop.Services;

public sealed record HealthSnapshot(bool? SpringHealthy, bool? AiHealthy, int? LegacyPendingCount,
    MutationHold? JournalReview, DateTimeOffset CheckedAt);

/// <summary>Read-only health/queue/journal view; it never flushes or changes pending operations.</summary>
public sealed class HealthSurface : IDisposable
{
    private readonly AuthServices _services;
    private readonly object _sync = new();
    private Task<HealthSnapshot?>? _refresh;
    private long _generation;
    private bool _disposed;

    public HealthSurface(AuthServices services) => _services = services ?? throw new ArgumentNullException(nameof(services));
    public HealthSnapshot? Snapshot { get; private set; }

    public Task<HealthSnapshot?> RefreshAsync()
    {
        lock (_sync)
        {
            if (_disposed) return Task.FromResult<HealthSnapshot?>(null);
            if (_refresh is not null) return _refresh;
            var completion = new TaskCompletionSource<HealthSnapshot?>(TaskCreationOptions.RunContinuationsAsynchronously);
            _refresh = completion.Task;
            _ = RefreshCoreAsync(_generation, completion);
            return completion.Task;
        }
    }

    private async Task RefreshCoreAsync(long generation, TaskCompletionSource<HealthSnapshot?> completion)
    {
        try
        {
            var spring = ReadHealthAsync(() => _services.Client.HealthAsync());
            var ai = ReadHealthAsync(() => _services.Client.AiHealthAsync());
            var queue = ReadCountAsync(async () =>
            {
                var snapshot = await _services.Legacy.InspectAsync();
                return snapshot.RequiresReview ? null : snapshot.PendingCount;
            });
            var journal = ReadJournalAsync(() => _services.Mutations.InspectAsync());
            await Task.WhenAll(spring, ai, queue, journal);
            var snapshot = new HealthSnapshot(await spring, await ai, await queue, await journal, DateTimeOffset.Now);
            lock (_sync)
            {
                if (_disposed || generation != _generation) { completion.TrySetResult(null); return; }
                Snapshot = snapshot;
                completion.TrySetResult(snapshot);
            }
        }
        catch
        {
            completion.TrySetResult(null);
        }
        finally { lock (_sync) if (ReferenceEquals(_refresh, completion.Task)) _refresh = null; }
    }

    private static async Task<bool?> ReadHealthAsync(Func<Task<bool>> read)
    {
        try { return await read(); }
        catch { return null; }
    }

    private static async Task<int?> ReadCountAsync(Func<Task<int?>> read)
    {
        try { return await read(); }
        catch { return null; }
    }

    private static async Task<MutationHold?> ReadJournalAsync(Func<Task<MutationHold>> read)
    {
        try { return await read(); }
        catch { return null; }
    }

    public void Dispose()
    {
        lock (_sync) { _disposed = true; _generation++; }
    }
}
