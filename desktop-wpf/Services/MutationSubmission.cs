using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using ParkingGateDesktop;

namespace LumiPark.Desktop.Services;

public enum MutationKind { Entry, ExitConfirm, Dispatch }
public sealed record MutationHold(bool IsHeld, string Reason);
internal sealed record MutationMetadata(Guid OperationId, MutationKind Kind, DateTimeOffset Time, string Origin, string ActorFingerprint);

// This journal contains no request body and deliberately offers no replay or public resolution operation.
public sealed class MutationJournal : IAsyncDisposable
{
    private readonly string _path;
    private readonly FileStream _ownership;
    internal string StoragePath => _path;

    private MutationJournal(string path, FileStream ownership) { _path = path; _ownership = ownership; }

    public static Task<MutationJournal> OpenAsync(string path) => Task.Run(() =>
    {
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("An explicit journal path is required.", nameof(path));
        string fullPath = Path.GetFullPath(path);
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);
        var ownership = new FileStream(fullPath + ".lock", FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None, 1, FileOptions.Asynchronous);
        return new MutationJournal(fullPath, ownership);
    });

    internal async Task<MutationHold> InspectAsync()
    {
        try
        {
            await using var stream = await Task.Run(() => new FileStream(_path, FileMode.Open, FileAccess.Read, FileShare.Read, 4096, FileOptions.Asynchronous));
            var metadata = await JsonSerializer.DeserializeAsync<MutationMetadata>(stream);
            if (metadata is null || metadata.OperationId == Guid.Empty || !Enum.IsDefined(metadata.Kind)
                || metadata.Time == default || !Uri.TryCreate(metadata.Origin, UriKind.Absolute, out var origin)
                || (origin.Scheme != Uri.UriSchemeHttp && origin.Scheme != Uri.UriSchemeHttps)
                || metadata.ActorFingerprint is null || metadata.ActorFingerprint.Length != 64
                || !metadata.ActorFingerprint.All(Uri.IsHexDigit))
                return new(true, "Journal requires review.");
            return new(true, "An earlier operation requires review.");
        }
        catch (FileNotFoundException) { return new(false, ""); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException or NotSupportedException)
        { return new(true, "Journal requires review."); }
    }

    internal async Task WriteAsync(MutationMetadata metadata)
    {
        await using var stream = await Task.Run(() => new FileStream(_path, FileMode.CreateNew, FileAccess.Write, FileShare.Read, 4096, FileOptions.Asynchronous | FileOptions.WriteThrough));
        await JsonSerializer.SerializeAsync(stream, metadata);
        await stream.FlushAsync();
        await Task.Run(() => stream.Flush(flushToDisk: true));
    }

    internal async Task RetireKnownAsync(Guid id)
    {
        await using (var stream = await Task.Run(() => new FileStream(_path, FileMode.Open, FileAccess.Read, FileShare.Read, 4096, FileOptions.Asynchronous)))
        {
            var metadata = await JsonSerializer.DeserializeAsync<MutationMetadata>(stream);
            if (metadata?.OperationId != id) throw new IOException("Journal operation does not match.");
        }
        await Task.Run(() => File.Delete(_path));
    }

    public async ValueTask DisposeAsync() => await _ownership.DisposeAsync();
}

public sealed class MutationSubmissionCoordinator
{
    private readonly ParkingApiClient _client;
    private readonly MutationJournal _journal;
    private readonly OfflineOutboxStore _legacy;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private bool _cleanupFailed;

    public MutationSubmissionCoordinator(ParkingApiClient client, MutationJournal journal, OfflineOutboxStore legacy)
    {
        ArgumentNullException.ThrowIfNull(client);
        ArgumentNullException.ThrowIfNull(journal);
        ArgumentNullException.ThrowIfNull(legacy);
        if (string.Equals(journal.StoragePath, legacy.StoragePath, StringComparison.OrdinalIgnoreCase)
            || string.Equals(journal.StoragePath + ".lock", legacy.StoragePath, StringComparison.OrdinalIgnoreCase))
            throw new ArgumentException("The journal and legacy queue must use separate paths.");
        _client = client; _journal = journal; _legacy = legacy;
    }

    public async Task<MutationHold> InspectAsync()
    {
        await _gate.WaitAsync();
        try { return await InspectUnsafeAsync(); }
        finally { _gate.Release(); }
    }

    private async Task<MutationHold> InspectUnsafeAsync()
    {
        if (_cleanupFailed) return new(true, "Journal cleanup requires review.");
        var own = await _journal.InspectAsync();
        if (own.IsHeld) return own;
        try
        {
            var legacy = await Task.Run(() => _legacy.InspectAsync());
            return legacy.PendingCount != 0 || legacy.RequiresReview
                ? new(true, "Legacy queue requires review.") : new(false, "");
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException)
        { return new(true, "Legacy queue cannot be inspected."); }
    }

    public async Task<SingleAttemptResult> SubmitAsync(MutationKind kind, ParkingRequest request)
    {
        if (!await _gate.WaitAsync(0)) return new(SingleAttemptOutcome.NotSent, Message: "An operation is in progress.");
        try
        {
            var hold = await InspectUnsafeAsync();
            if (hold.IsHeld) return new(SingleAttemptOutcome.NotSent, Message: hold.Reason);
            if (kind is not (MutationKind.Entry or MutationKind.ExitConfirm) || request is null || _client.CurrentOperator is null
                || !Uri.TryCreate(_client.BaseUrl, UriKind.Absolute, out var origin)
                || (origin.Scheme != Uri.UriSchemeHttp && origin.Scheme != Uri.UriSchemeHttps)
                || !string.IsNullOrEmpty(origin.UserInfo))
                return new(SingleAttemptOutcome.NotSent, Message: "Invalid operation or session.");
            Guid id = Guid.NewGuid();
            var immutable = request with { OperationId = id.ToString() };
            string fingerprint = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(_client.CurrentOperator.Username)));
            var metadata = new MutationMetadata(id, kind, DateTimeOffset.UtcNow, origin.GetLeftPart(UriPartial.Authority), fingerprint);
            try { await _journal.WriteAsync(metadata); }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException)
            {
                if ((await _journal.InspectAsync()).IsHeld) _cleanupFailed = true;
                return new(SingleAttemptOutcome.NotSent, Message: "Journal could not be written.");
            }
            var result = kind switch
            {
                MutationKind.Entry => await _client.EntrySingleAttemptAsync(immutable),
                MutationKind.ExitConfirm => await _client.ConfirmExitSingleAttemptAsync(immutable),
                _ => new SingleAttemptResult(SingleAttemptOutcome.NotSent, Message: "Invalid operation or session.")
            };
            if (result.Outcome is SingleAttemptOutcome.Success or SingleAttemptOutcome.Rejected or SingleAttemptOutcome.NotSent)
            {
                try { await _journal.RetireKnownAsync(id); }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException or System.Security.SecurityException)
                { _cleanupFailed = true; }
            }
            return result;
        }
        finally { _gate.Release(); }
    }

    public async Task<SingleAttemptResult> SubmitDispatchAsync(long slotId, long sessionId)
    {
        if (slotId <= 0 || sessionId <= 0)
            return new(SingleAttemptOutcome.NotSent, Message: "Thông tin điều phối không hợp lệ.");
        if (!await _gate.WaitAsync(0)) return new(SingleAttemptOutcome.NotSent, Message: "An operation is in progress.");
        try
        {
            var hold = await InspectUnsafeAsync();
            if (hold.IsHeld) return new(SingleAttemptOutcome.NotSent, Message: hold.Reason);
            if (_client.CurrentOperator is null
                || !Uri.TryCreate(_client.BaseUrl, UriKind.Absolute, out var origin)
                || (origin.Scheme != Uri.UriSchemeHttp && origin.Scheme != Uri.UriSchemeHttps)
                || !string.IsNullOrEmpty(origin.UserInfo))
                return new(SingleAttemptOutcome.NotSent, Message: "Phiên làm việc không hợp lệ.");

            var id = Guid.NewGuid();
            var metadata = new MutationMetadata(id, MutationKind.Dispatch, DateTimeOffset.UtcNow,
                origin.GetLeftPart(UriPartial.Authority),
                Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(_client.CurrentOperator.Username))));
            try { await _journal.WriteAsync(metadata); }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException)
            {
                if ((await _journal.InspectAsync()).IsHeld) _cleanupFailed = true;
                return new(SingleAttemptOutcome.NotSent, Message: "Không thể ghi nhận thao tác an toàn.");
            }

            var result = await _client.DispatchSessionSingleAttemptAsync(slotId, sessionId);
            if (result.Outcome is SingleAttemptOutcome.Success or SingleAttemptOutcome.Rejected or SingleAttemptOutcome.NotSent)
            {
                try { await _journal.RetireKnownAsync(id); }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException or System.Security.SecurityException)
                { _cleanupFailed = true; }
            }
            return result;
        }
        finally { _gate.Release(); }
    }
}
