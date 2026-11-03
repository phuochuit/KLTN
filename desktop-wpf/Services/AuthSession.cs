using System.ComponentModel;
using System.Runtime.CompilerServices;
using ParkingGateDesktop;

namespace LumiPark.Desktop.Services;

public sealed class AuthServices : IDisposable
{
    public ParkingApiClient Client { get; }
    public MutationJournal Journal { get; }
    public OfflineOutboxStore Legacy { get; }
    public MutationSubmissionCoordinator Mutations { get; }
    public GateStationSettingsStore SettingsStore { get; }
    public AuthServices(ParkingApiClient client, MutationJournal journal, OfflineOutboxStore legacy, GateStationSettingsStore? settingsStore = null)
    { Client = client; Journal = journal; Legacy = legacy; Mutations = new(client, journal, legacy); SettingsStore = settingsStore ?? new GateStationSettingsStore(); }
    public void Dispose() { Client.Dispose(); Journal.DisposeAsync().AsTask().GetAwaiter().GetResult(); }
}

public sealed class AuthSession : INotifyPropertyChanged, IDisposable
{
    private readonly Func<Task<AuthServices>> factory;
    private readonly SemaphoreSlim transition = new(1, 1);
    private AuthServices? services;
    private bool busy, disposed, logoutRequested, isLogoutSummary;
    private int polling, generation;
    private long summaryGeneration;
    private OperatorProfile? profile;
    private string error = "", connection = "Chưa kết nối";
    public AuthSession(Func<Task<AuthServices>> factory) => this.factory = factory;
    public event PropertyChangedEventHandler? PropertyChanged;
    public Func<ShiftSummary, bool, Task>? ShiftSummaryRequested { get; set; }
    public Func<bool, Task>? ShiftSummaryFailed { get; set; }
    public string Username { get; set; } = "";
    public string BaseUrl { get; set; } = "http://localhost:8080";
    public bool IsBusy => busy;
    public bool IsLogoutSummary => isLogoutSummary;
    public bool IsLeavingSession => logoutRequested || disposed;
    public bool CanEditCredentials => !busy;
    public bool LoginVisible => profile is null;
    public bool WorkspaceVisible => profile is not null;
    public OperatorProfile? Profile => profile;
    public AuthServices? ActiveServices => services;
    public ParkingApiClient? Client => services?.Client;
    public string OperatorName => profile?.FullName ?? "";
    public string ShiftText => profile?.ActiveShift is { } shift
        ? $"{shift.Name} · {shift.StartsAt.ToLocalTime():HH:mm}–{shift.EndsAt.ToLocalTime():HH:mm}" : "Không có ca đang hoạt động";
    public string Error => error;
    public string Connection => connection;
    public Func<bool> CanLeaveWorkspace { get; set; } = () => true;
    public Task<ShiftSummary?> GetShiftSummaryAsync(bool previous) => services?.Client.GetShiftSummaryAsync(previous) ?? Task.FromResult<ShiftSummary?>(null);

    public async Task<bool> RunInActiveSessionAsync(AuthServices active, Func<Task> operation)
    {
        ArgumentNullException.ThrowIfNull(active); ArgumentNullException.ThrowIfNull(operation);
        await transition.WaitAsync();
        try
        {
            if (disposed || logoutRequested || !ReferenceEquals(services, active) || profile is null) return false;
            busy = true; Notify();
            try { await operation(); return !disposed && ReferenceEquals(services, active); }
            finally { busy = false; Notify(); }
        }
        finally { transition.Release(); }
    }

    public async Task LoginAsync(string username, string password)
    {
        string submittedUsername = username;
        string submittedPassword = password;
        string submittedBaseUrl = BaseUrl;
        if (disposed || string.IsNullOrWhiteSpace(submittedUsername) || string.IsNullOrEmpty(submittedPassword))
        { if (!disposed) { error = "Vui lòng nhập tên đăng nhập và mật khẩu."; Notify(); } return; }
        if (busy && !WorkspaceVisible) return;
        await transition.WaitAsync();
        AuthServices? created = null;
        try
        {
            if (disposed || WorkspaceVisible) return;
            busy = true; error = ""; Notify();
            int version = generation;
            created = await factory();
            if (disposed || version != generation) return;
            created.Client.BaseUrl = submittedBaseUrl;
            await created.Client.LoginAsync(submittedUsername.Trim(), submittedPassword);
            var current = await created.Client.ValidateSessionAsync();
            if (disposed || version != generation) return;
            services = created; created = null; profile = current; connection = "Đã xác minh";
        }
        catch (Exception) { if (!disposed) error = "Không thể đăng nhập. Vui lòng kiểm tra thông tin và kết nối."; }
        finally
        {
            created?.Dispose(); busy = false; Notify(); transition.Release();
        }
        if (services is { } active && profile is not null) await RequestSummaryAsync(active, true);
    }

    public async Task PollSessionAsync()
    {
        var active = services;
        if (disposed || busy || active is null || profile is null || Interlocked.CompareExchange(ref polling, 1, 0) != 0) return;
        int version = generation;
        try
        {
            var current = await active.Client.ValidateSessionAsync();
            if (disposed || version != generation) return;
            var previousShift = profile.ActiveShift?.Id;
            profile = current; connection = "Đã xác minh"; Notify();
            if (previousShift is { } oldId && current.ActiveShift?.Id != oldId)
                await RequestSummaryAsync(active, true);
        }
        catch (TransientServerException)
        { if (!disposed && version == generation) { connection = "Chưa xác minh · mất kết nối"; Notify(); } }
        catch (DesktopAuthenticationException)
        { if (!disposed && version == generation) { EndSession(); error = "Phiên làm việc đã kết thúc. Vui lòng đăng nhập lại."; Notify(); } }
        catch (Exception)
        { if (!disposed && version == generation) { connection = "Chưa xác minh · mất kết nối"; Notify(); } }
        finally { Interlocked.Exchange(ref polling, 0); }
    }

    public async Task LogoutAsync()
    {
        if (disposed || !CanLeaveWorkspace()) return;
        logoutRequested = true; Interlocked.Increment(ref summaryGeneration); Notify();
        await transition.WaitAsync();
        try
        {
            if (disposed || services is null || !CanLeaveWorkspace()) return;
            busy = true; Notify();
            await WaitForPollAsync();
            var active = services;
            if (active is null) return;
            isLogoutSummary = true;
            await RequestSummaryAsync(active, false);
            isLogoutSummary = false;
            try { await active.Client.LogoutAsync(); } catch (Exception) { }
            active.Dispose(); services = null; generation++; profile = null; connection = "Chưa kết nối";
        }
        finally { isLogoutSummary = false; logoutRequested = false; busy = false; Notify(); transition.Release(); }
    }

    public async Task<bool> CloseAsync()
    {
        logoutRequested = true; Interlocked.Increment(ref summaryGeneration); Notify();
        await transition.WaitAsync();
        try
        {
            if (disposed) return true;
            if (!CanLeaveWorkspace()) return false;
            if (services is not null)
            {
                busy = true; Notify();
                await WaitForPollAsync();
                var active = services;
                if (active is not null)
                {
                    try { await active.Client.LogoutAsync(); } catch (Exception) { }
                    active.Dispose(); services = null;
                }
            }
            disposed = true; generation++; profile = null; connection = "Chưa kết nối";
            return true;
        }
        finally { logoutRequested = false; busy = false; Notify(); transition.Release(); }
    }

    private async Task RequestSummaryAsync(AuthServices active, bool previous)
    {
        if (ShiftSummaryRequested is null) return;
        var version = Volatile.Read(ref summaryGeneration);
        try
        {
            var summary = await active.Client.GetShiftSummaryAsync(previous);
            if (summary is not null && version == Volatile.Read(ref summaryGeneration) && ReferenceEquals(services, active)
                && ShiftSummaryRequested is { } callback) await callback(summary, previous);
        }
        catch (Exception)
        {
            if (version == Volatile.Read(ref summaryGeneration) && ReferenceEquals(services, active) && ShiftSummaryFailed is { } failed)
                try { await failed(previous); } catch (Exception) { }
        }
    }

    private async Task WaitForPollAsync()
    {
        while (Volatile.Read(ref polling) != 0) await Task.Delay(10);
    }

    private void EndSession()
    { generation++; profile = null; connection = "Chưa kết nối"; services?.Dispose(); services = null; }
    public void Dispose()
    {
        if (disposed) return;
        disposed = true; generation++; profile = null; connection = "Chưa kết nối";
        // Explicit LogoutAsync owns normal shutdown; this fallback only releases local resources.
        services?.Dispose(); services = null;
    }
    private void Notify([CallerMemberName] string? ignored = null)
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(null));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CanEditCredentials)));
    }
}
