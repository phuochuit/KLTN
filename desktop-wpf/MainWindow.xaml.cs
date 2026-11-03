using System.ComponentModel;
using System.IO;
using System.Net.Http;
using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Threading;
using Microsoft.Win32;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;

namespace LumiPark.Desktop;

public partial class MainWindow : Window
{
    private readonly AuthSession model;
    private readonly DemoTransport? demo;
    private readonly DispatcherTimer heartbeat = new() { Interval = TimeSpan.FromSeconds(2) };
    private readonly SemaphoreSlim summaryGate = new(1, 1);
    private readonly HashSet<long> shownShiftSummaryIds = [];
    private AuthServices? workspaceIdentity;
    private GateWorkspace? workspace;
    private SimulatedBarrierController? barrier;
    private CancellationTokenSource? barrierAutoClose;
    private long barrierGeneration;
    private bool closeApproved, closeInProgress, syncingPlate, syncingMember, busy, barrierBusy;
    private bool summaryPending;
    private long summaryGeneration;
    private DemoToolsWindow? demoToolsWindow;
    private Window? activeSessionDialog;
    private TaskCompletionSource? activeDialogClosed;
    private string observedVehicleEvidenceId = "";
    private long vehicleDecodeGeneration;
    private long workspaceGeneration;
    private bool dispatchPending;
    private MutationHold? sharedMutationHold;
    private ParkingMapWindow? parkingMapWindow;
    private HealthWindow? healthWindow;
    private MenuItem CompactFaceFileMenuItem => (MenuItem)CompactSourcesButton.ContextMenu.Items[4];
    public bool IsDemo => demo is not null;
    public MainWindow(AuthSession model, DemoTransport? demo, bool bootstrap)
    {
        this.model = model; this.demo = demo;
        InitializeComponent(); DataContext = model;
        var area = SystemParameters.WorkArea;
        MaxWidth = area.Width; MaxHeight = area.Height; Width = Math.Min(1440, area.Width); Height = Math.Min(940, area.Height);
        MinWidth = Math.Min(900, area.Width); MinHeight = Math.Min(580, area.Height);
        WindowStartupLocation = WindowStartupLocation.Manual;
        if (IsDemo) { DemoBanner.Visibility = Visibility.Visible; DemoToolsButton.Visibility = Visibility.Visible; }
        heartbeat.Tick += Heartbeat_Tick; model.PropertyChanged += ModelChanged;
        model.ShiftSummaryRequested = ShowShiftSummaryAsync;
        model.ShiftSummaryFailed = _ => { OperationStatus.Text = "Không tải được tổng kết ca; phiên vẫn tiếp tục."; return Task.CompletedTask; };
        if (bootstrap) Loaded += async (_, _) => await Safe(() => model.LoginAsync("demo", "demo"));
    }
    private async void Login_Click(object sender, RoutedEventArgs e)
    { var password = PasswordInput.Password; PasswordInput.Clear(); await Safe(() => model.LoginAsync(model.Username, password)); }
    private async void Logout_Click(object sender, RoutedEventArgs e) { CancelAutoClose(); await Safe(() => model.LogoutAsync()); }
    private async void Heartbeat_Tick(object? sender, EventArgs e) => await Safe(() => model.PollSessionAsync());
    private async void ModelChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (model.IsLeavingSession) Interlocked.Increment(ref summaryGeneration);
        LoginButton.IsEnabled = !model.IsBusy;
        LogoutButton.IsEnabled = !dispatchPending && !model.IsLeavingSession;
        if (model.WorkspaceVisible) { heartbeat.Start(); try { await EnsureWorkspaceAsync(); } catch (Exception ex) { ShowFailure(ex); } }
        else { heartbeat.Stop(); DropWorkspace(); }
        if (workspace is not null) RefreshWorkspace();
        CurrentSummaryButton.IsEnabled = workspace is not null && !model.IsBusy && !model.IsLeavingSession;
    }
    private async Task EnsureWorkspaceAsync()
    {
        var current = model.ActiveServices;
        if (current is null || ReferenceEquals(current, workspaceIdentity)) return;
        DropWorkspace(); workspaceIdentity = current; workspace = new GateWorkspace(current.Client, current.Mutations);
        barrier = new SimulatedBarrierController(); barrier.StatusChanged += BarrierStatusChanged; RefreshBarrier();
        model.CanLeaveWorkspace = () => !dispatchPending && (workspace?.CanLeaveWorkspace ?? true);
        sharedMutationHold = null;
        var generation = Interlocked.Increment(ref workspaceGeneration);
        workspace.PropertyChanged += WorkspaceChanged; workspace.TransactionConfirmed += TransactionConfirmed;
        Ops.DataContext = workspace;
        await Safe(async () => { await workspace.InitializeAsync(); RefreshWorkspace(); });
        await RefreshSharedHoldAsync(current, generation);
    }
    private void DropWorkspace()
    {
        shownShiftSummaryIds.Clear();
        var dialog = activeSessionDialog; activeSessionDialog = null; activeDialogClosed?.TrySetResult(); activeDialogClosed = null;
        try { dialog?.Close(); } catch (InvalidOperationException) { }
        if (demoToolsWindow is { } tools) { demoToolsWindow = null; tools.Closed -= DemoToolsClosed; tools.Close(); }
        if (parkingMapWindow is { } mapWindow) { parkingMapWindow = null; mapWindow.Close(); }
        if (healthWindow is { } statusWindow) { healthWindow = null; statusWindow.Close(); }
        Interlocked.Increment(ref workspaceGeneration); dispatchPending = false; sharedMutationHold = null;
        if (workspace is not null) { workspace.PropertyChanged -= WorkspaceChanged; workspace.TransactionConfirmed -= TransactionConfirmed; workspace.Invalidate(); }
        CancelAutoClose();
        if (barrier is not null) { barrier.StatusChanged -= BarrierStatusChanged; barrier.Dispose(); barrier = null; }
        barrierBusy = false;
        workspace = null; workspaceIdentity = null; Ops.DataContext = null; model.CanLeaveWorkspace = () => true;
        observedVehicleEvidenceId = ""; Interlocked.Increment(ref vehicleDecodeGeneration);
        VehicleImage.Source = null; FaceImage.Source = null;
    }
    private void WorkspaceChanged(object? sender, PropertyChangedEventArgs e) => RefreshWorkspace();
    private void RefreshWorkspace()
    {
        var w = workspace; if (w is null) return;
        if (!syncingPlate && PlateBox.Text != w.Plate) { syncingPlate = true; PlateBox.Text = w.Plate; syncingPlate = false; }
        var currentEvidenceId = w.VehicleEvidenceId;
        if (!string.Equals(observedVehicleEvidenceId, currentEvidenceId, StringComparison.Ordinal))
        {
            observedVehicleEvidenceId = currentEvidenceId;
            Interlocked.Increment(ref vehicleDecodeGeneration);
            VehicleImage.Source = null;
        }
        VehicleTypeText.Text = $"Loại xe · {DisplayVehicleType(w.VehicleType)}";
        EvidenceButton.Visibility = w.Direction == GateDirection.Exit ? Visibility.Visible : Visibility.Collapsed;
        FaceFileButton.Visibility = w.CanVerifyFaceFile ? Visibility.Visible : Visibility.Collapsed;
        CompactFaceFileMenuItem.Visibility = w.CanVerifyFaceFile ? Visibility.Visible : Visibility.Collapsed;
        FaceFileReason.Visibility = w.CanVerifyFaceFile ? Visibility.Collapsed : Visibility.Visible;
        FaceFileReason.Text = w.FaceFileUnavailableReason;
        OperationStatus.Text = w.Status;
        PreviewFeeText.Text = w.Direction == GateDirection.Exit && w.Preview is { } preview
            ? $"Phí xem trước · {preview.Fee.ToString("N0", CultureInfo.GetCultureInfo("vi-VN"))} ₫" : "";
        bool canEdit = w.IsInitialized && !busy && !model.IsBusy && !model.IsLeavingSession && !w.IsHeld && !w.WritePending && !w.CanNextContext;
        EntryModeButton.IsEnabled = ExitModeButton.IsEnabled = MediaActions.IsEnabled = CompactMediaActions.IsEnabled = LookupButton.IsEnabled = MemberPicker.IsEnabled = canEdit;
        PlateBox.IsReadOnly = !canEdit;
        bool sharedHeld = sharedMutationHold?.IsHeld == true;
        HoldIcon.Visibility = w.IsHeld || sharedHeld ? Visibility.Visible : Visibility.Collapsed;
        HoldText.Text = sharedHeld
            ? "Thao tác tạm dừng để kiểm tra trạng thái trước đó. Không gửi lại yêu cầu chưa rõ kết quả."
            : w.IsHeld ? "Đang tạm dừng thao tác. Liên hệ quản trị viên để kiểm tra trạng thái trước đó." : "";
        NextButton.IsEnabled = w.CanNextContext;
        MainAction.IsEnabled = canEdit && !dispatchPending && sharedMutationHold?.IsHeld != true && !string.IsNullOrWhiteSpace(w.Plate);
        CorrectionButton.IsEnabled = canEdit
            && model.Profile?.ActiveShift is not null && !string.IsNullOrWhiteSpace(w.VehicleEvidenceId);
        EvidenceButton.IsEnabled = !busy && !w.IsHeld && !w.WritePending && !string.IsNullOrWhiteSpace(w.VehicleEvidenceId);
        MainAction.Content = w.Direction == GateDirection.Entry ? "Xác nhận xe vào" : w.Preview is null ? "Xem trước phí xe ra" : w.Preview.Fee <= 0 ? "Xác nhận xe ra" : "Xác nhận thu và xe ra";
        if (w.Lookup is { } lookup)
        {
            OwnerText.Text = lookup.Registered ? $"{lookup.OwnerName} · Căn hộ {lookup.ApartmentNumber} · {lookup.OwnerPhone}" : "Khách vãng lai";
            PackageText.Text = $"{w.LookupSummary}"; PackageWarning.Text = w.PackageWarning;
            var members = lookup.AuthorizedMembers ?? [];
            var selected = w.MemberId;
            syncingMember = true; MemberPicker.ItemsSource = members; MemberPicker.SelectedItem = members.FirstOrDefault(m => m.Id == selected); syncingMember = false;
            LookupStatus.Text = lookup.Registered ? "Đã tra cứu cư dân" : "Đã tra cứu · Khách vãng lai";
        }
        else { OwnerText.Text = "Chưa có thông tin tra cứu"; PackageText.Text = ""; PackageWarning.Text = ""; MemberPicker.ItemsSource = null; LookupStatus.Text = "Thông tin chủ xe và gói gửi xe"; }
        if (string.IsNullOrWhiteSpace(w.FaceBase64)) FaceImage.Source = null;
        FaceState.Text = w.FaceVerified ? "Đã xác minh" : string.IsNullOrWhiteSpace(w.FaceBase64) ? "Chưa ghi nhận" : "Đã ghi nhận · chưa xác minh";
        BusyText.Visibility = busy || w.WritePending ? Visibility.Visible : Visibility.Collapsed;
        BarrierOpenButton.IsEnabled = barrier is not null && !busy && !barrierBusy && !w.IsHeld && !w.WritePending && barrier.Status.Position != BarrierPosition.Open;
        BarrierCloseButton.IsEnabled = barrier is not null && !busy && !barrierBusy && !w.IsHeld && !w.WritePending && barrier.Status.Position == BarrierPosition.Open;
    }
    private async Task SetImageAsync(string data, bool face, GateWorkspace expected)
    {
        var revision = expected.Revision;
        var evidenceId = face ? "" : expected.VehicleEvidenceId;
        var decodeGeneration = Volatile.Read(ref vehicleDecodeGeneration);
        if (!face && (string.IsNullOrWhiteSpace(evidenceId) || !string.Equals(observedVehicleEvidenceId, evidenceId, StringComparison.Ordinal))) return;
        var image = await Task.Run(() => FrozenImageDecoder.DecodeBase64(data, 1200, 900));
        if (!ReferenceEquals(workspace, expected)) return;
        if (face)
        {
            if (expected.Revision != revision) return;
            FaceImage.Source = image;
        }
        else if (expected.VehicleEvidenceId == evidenceId && observedVehicleEvidenceId == evidenceId
            && Volatile.Read(ref vehicleDecodeGeneration) == decodeGeneration)
        {
            VehicleImage.Source = image;
            MediaScroll.ScrollToTop();
        }
    }
    private void EntryMode_Click(object sender, RoutedEventArgs e) { workspace?.SetDirection(GateDirection.Entry); ReceiptText.Text = ""; }
    private void ExitMode_Click(object sender, RoutedEventArgs e) { workspace?.SetDirection(GateDirection.Exit); ReceiptText.Text = ""; }
    private void Plate_TextChanged(object sender, TextChangedEventArgs e) { if (!syncingPlate && workspace is not null) workspace.SetPlate(PlateBox.Text); }
    private void Member_SelectionChanged(object sender, SelectionChangedEventArgs e) { if (!syncingMember && workspace is not null) workspace.SetMember((MemberPicker.SelectedItem as AuthorizedMemberResponse)?.Id); }
    private async void Lookup_Click(object sender, RoutedEventArgs e) => await RunBusy(async w => await w.LookupAsync());
    private async void VehicleCamera_Click(object sender, RoutedEventArgs e) => await RunBusy(async w => { var result = await w.RecognizeVehicleCameraAsync(); if (result is not null) await SetImageAsync(result.AnnotatedImageBase64, false, w); });
    private async void VehicleFile_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Filter = "Ảnh và video|*.jpg;*.jpeg;*.png;*.bmp;*.mp4;*.m4v;*.avi;*.mov;*.mkv;*.wmv;*.webm|Tệp ảnh|*.jpg;*.jpeg;*.png;*.bmp|Tệp video|*.mp4;*.m4v;*.avi;*.mov;*.mkv;*.wmv;*.webm" };
        if (dialog.ShowDialog(this) != true) return;
        await RunBusy(async w => { bool video = IsVideo(dialog.FileName); var result = video ? await w.RecognizeVideoAsync(dialog.FileName) : await w.RecognizeImageAsync(dialog.FileName); if (result is not null) await SetImageAsync(result.AnnotatedImageBase64, false, w); });
    }
    private async void CombinedFile_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Filter = "Tệp ảnh xe|*.jpg;*.jpeg;*.png;*.bmp" };
        if (dialog.ShowDialog(this) != true) return;
        await RunBusy(async w => { var services = workspaceIdentity ?? throw new InvalidOperationException("Phiên làm việc đã kết thúc."); var operation = w.Direction == GateDirection.Entry ? "ENTRY" : "EXIT"; var result = await w.RecognizeAndCaptureAsync(() => services.Client.RecognizeImageAsync(dialog.FileName, operation)); if (result is { } pair) { await SetImageAsync(pair.Vehicle.AnnotatedImageBase64, false, w); await SetImageAsync(pair.Face.RealtimeImageBase64, true, w); } });
    }
    private async void FaceCamera_Click(object sender, RoutedEventArgs e) => await RunBusy(async w => { var r = await w.VerifyFaceCameraAsync(); if (r is not null) await SetImageAsync(r.RealtimeImageBase64, true, w); });
    private async void FaceFile_Click(object sender, RoutedEventArgs e)
    {
        if (workspace?.CanVerifyFaceFile != true) return;
        var dialog = new OpenFileDialog { Filter = "Ảnh và video|*.jpg;*.jpeg;*.png;*.bmp;*.mp4;*.m4v;*.avi;*.mov;*.mkv;*.wmv;*.webm|Tệp ảnh|*.jpg;*.jpeg;*.png;*.bmp|Tệp video|*.mp4;*.m4v;*.avi;*.mov;*.mkv;*.wmv;*.webm" };
        if (dialog.ShowDialog(this) != true) return;
        await RunBusy(async w => { var r = await w.VerifyFaceFileAsync(dialog.FileName, IsVideo(dialog.FileName)); if (r is not null) await SetImageAsync(r.RealtimeImageBase64, true, w); });
    }
    private static bool IsVideo(string path) => Path.GetExtension(path).ToLowerInvariant() is ".mp4" or ".m4v" or ".avi" or ".mov" or ".mkv" or ".wmv" or ".webm";
    private async void MainAction_Click(object sender, RoutedEventArgs e)
    {
        var w = workspace; if (w is null) return;
        if (w.Direction == GateDirection.Entry) { await RunBusy(async current => await current.SubmitEntryAsync()); return; }
        if (w.Preview is null) { await RunBusy(async current => await current.PreviewExitAsync()); return; }
        if (w.Preview.Fee <= 0)
        {
            try { var freeTicket = w.CreateConfirmationTicket(); await RunBusy(async current => await current.ConfirmExitAsync(freeTicket)); }
            catch (Exception ex) { ShowFailure(ex); }
            return;
        }
        var dialog = new PaymentPreviewWindow(w.Preview.Fee) { Owner = this };
        if (dialog.ShowDialog() != true) return;
        try { var ticket = w.CreateConfirmationTicket(dialog.Method, dialog.Reference); await RunBusy(async current => await current.ConfirmExitAsync(ticket)); }
        catch (Exception ex) { ShowFailure(ex); }
    }
    private void TransactionConfirmed(object? sender, GateTransactionConfirmed e)
    {
        var response = e.Response;
        var exitAmount = e.Direction == GateDirection.Exit ? $"Phí xe ra {response.Fee.ToString("N0", CultureInfo.GetCultureInfo("vi-VN"))} ₫" : "";
        ReceiptText.Text = string.Join(" · ", new[] { response.Message, exitAmount, response.ReceiptNumber is null ? "" : $"Biên nhận {response.ReceiptNumber}", response.Warning ? "Có cảnh báo từ máy chủ" : "" }.Where(x => !string.IsNullOrWhiteSpace(x)));
        if (!ReferenceEquals(sender, workspace) || !ReferenceEquals(workspaceIdentity, model.ActiveServices)) return;
        if (BarrierAutoOpenPolicy.ShouldOpenAfterConfirmation(e)) _ = OpenBarrierAfterConfirmationAsync(workspaceIdentity, barrier, e);
    }
    private async Task OpenBarrierAfterConfirmationAsync(AuthServices? identity, SimulatedBarrierController? controller, GateTransactionConfirmed confirmation)
    {
        if (identity is null || controller is null || !ReferenceEquals(identity, model.ActiveServices) || confirmation.Response.Status is not ("OPEN" or "COMPLETED")) return;
        CancelAutoClose(); var generation = Interlocked.Increment(ref barrierGeneration);
        CancellationTokenSource? source = null;
        try
        {
            barrierBusy = true; RefreshBarrier();
            try { await controller.OpenAsync("Giao dịch đã được xác nhận"); }
            finally { if (ReferenceEquals(controller, barrier)) { barrierBusy = false; RefreshBarrier(); } }
            if (generation != Volatile.Read(ref barrierGeneration) || !ReferenceEquals(identity, model.ActiveServices)
                || !ReferenceEquals(controller, barrier)) return;
            source = new CancellationTokenSource(); barrierAutoClose = source;
            await Task.Delay(TimeSpan.FromSeconds(identity.Client.StationSettings.BarrierAutoCloseSeconds), source.Token);
            if (generation == Volatile.Read(ref barrierGeneration) && ReferenceEquals(identity, model.ActiveServices) && ReferenceEquals(controller, barrier))
            {
                barrierBusy = true; RefreshBarrier();
                try { await controller.CloseAsync("Tự đóng theo cài đặt trạm", source.Token); }
                finally { if (ReferenceEquals(controller, barrier)) { barrierBusy = false; RefreshBarrier(); } }
            }
        }
        catch (OperationCanceledException) { }
        catch (ObjectDisposedException) { }
        catch (Exception ex) { if (ReferenceEquals(identity, model.ActiveServices)) ShowFailure(ex); }
        finally { if (generation == Volatile.Read(ref barrierGeneration)) barrierAutoClose = null; source?.Dispose(); if (ReferenceEquals(controller, barrier)) RefreshBarrier(); }
    }
    private void CancelAutoClose() { Interlocked.Increment(ref barrierGeneration); barrierAutoClose?.Cancel(); barrierAutoClose?.Dispose(); barrierAutoClose = null; }
    private void BarrierStatusChanged(BarrierStatus status)
    {
        if (Dispatcher.HasShutdownStarted || Dispatcher.HasShutdownFinished) return;
        Dispatcher.BeginInvoke(new Action(() => { if (barrier is not null) RefreshBarrier(); }));
    }
    private void RefreshBarrier()
    {
        if (!Dispatcher.CheckAccess()) { Dispatcher.BeginInvoke(new Action(RefreshBarrier)); return; }
        if (barrier is null) { BarrierStatusText.Text = "MÔ PHỎNG · ĐANG ĐÓNG"; return; }
        BarrierStatusText.Text = $"MÔ PHỎNG · {barrier.Status.Message.Replace("Mô phỏng: ", "", StringComparison.Ordinal)}";
        BarrierOpenButton.IsEnabled = workspace is not null && !busy && !barrierBusy && !workspace.IsHeld && !workspace.WritePending && barrier.Status.Position != BarrierPosition.Open;
        BarrierCloseButton.IsEnabled = workspace is not null && !busy && !barrierBusy && !workspace.IsHeld && !workspace.WritePending && barrier.Status.Position == BarrierPosition.Open;
    }
    private async void BarrierOpen_Click(object sender, RoutedEventArgs e)
    {
        var controller = barrier; if (controller is null || workspace is null || workspace.IsHeld || busy || barrierBusy) return;
        CancelAutoClose(); barrierBusy = true; RefreshBarrier();
        try { await controller.OpenAsync("Mở thủ công bởi nhân viên"); }
        catch (Exception ex) { ShowFailure(ex); }
        finally { if (ReferenceEquals(controller, barrier)) { barrierBusy = false; RefreshBarrier(); } }
    }
    private async void BarrierClose_Click(object sender, RoutedEventArgs e)
    {
        var controller = barrier; if (controller is null || workspace is null || workspace.IsHeld || busy || barrierBusy) return;
        CancelAutoClose(); barrierBusy = true; RefreshBarrier();
        try { await controller.CloseAsync("Đóng thủ công bởi nhân viên"); }
        catch (Exception ex) { ShowFailure(ex); }
        finally { if (ReferenceEquals(controller, barrier)) { barrierBusy = false; RefreshBarrier(); } }
    }
    private void Correction_Click(object sender, RoutedEventArgs e)
    {
        var w = workspace; if (w is null || w.IsHeld || w.WritePending || busy || string.IsNullOrWhiteSpace(w.VehicleEvidenceId)) return;
        if (model.Profile?.ActiveShift is null) { OperationStatus.Text = "Hiệu chỉnh chỉ khả dụng khi đang có ca hoạt động."; return; }
        var revision = w.Revision; var identity = workspaceIdentity;
        var dialog = new ManualCorrectionWindow(w.Plate, w.VehicleType, IsDemo) { Owner = this };
        if (ShowSessionDialog(dialog, identity) == true && ReferenceEquals(w, workspace)
            && ReferenceEquals(identity, model.ActiveServices) && revision == w.Revision && model.Profile?.ActiveShift is not null)
            w.ApplyCorrection(dialog.CorrectedPlate, dialog.ReasonCode, dialog.ReasonText, dialog.VehicleType);
    }
    private async void Evidence_Click(object sender, RoutedEventArgs e)
    {
        var w = workspace; var identity = workspaceIdentity;
        if (w is null || w.Direction != GateDirection.Exit || identity is null || w.IsHeld || w.WritePending || busy || string.IsNullOrWhiteSpace(w.VehicleEvidenceId)) return;
        var revision = w.Revision; var plate = w.Plate; var type = w.VehicleType; var vehicleEvidence = w.VehicleEvidenceId; var faceEvidence = w.FaceEvidenceId;
        try
        {
            var result = await identity.Client.GetEvidenceComparisonAsync(plate, type, vehicleEvidence, faceEvidence);
            if (!ReferenceEquals(w, workspace) || !ReferenceEquals(identity, model.ActiveServices) || revision != w.Revision) return;
            ShowSessionDialog(new EvidenceComparisonWindow(result, IsDemo) { Owner = this }, identity);
        }
        catch (Exception ex) { if (ReferenceEquals(identity, model.ActiveServices)) ShowFailure(ex); }
    }

    private void ParkingMap_Click(object sender, RoutedEventArgs e)
    {
        var identity = workspaceIdentity;
        if (identity is null || !ReferenceEquals(identity, model.ActiveServices)) return;
        if (parkingMapWindow is { IsVisible: true } open) { open.Activate(); return; }
        var generation = Volatile.Read(ref workspaceGeneration);
        var window = new ParkingMapWindow(identity, ApplySharedHold, value =>
        {
            dispatchPending = value;
            LogoutButton.IsEnabled = !value && !model.IsLeavingSession;
            model.CanLeaveWorkspace = () => !dispatchPending && (workspace?.CanLeaveWorkspace ?? true);
        }, generation,
            () => ReferenceEquals(identity, model.ActiveServices) && generation == Volatile.Read(ref workspaceGeneration));
        parkingMapWindow = window; window.Owner = this;
        window.Closed += (_, _) => { if (ReferenceEquals(parkingMapWindow, window)) parkingMapWindow = null; };
        window.Show();
    }

    private async void Health_Click(object sender, RoutedEventArgs e)
    {
        var identity = workspaceIdentity;
        if (identity is null || !ReferenceEquals(identity, model.ActiveServices)) return;
        if (healthWindow is { IsVisible: true } open) { open.Activate(); return; }
        var generation = Volatile.Read(ref workspaceGeneration);
        var window = new HealthWindow(identity, () => ReferenceEquals(identity, model.ActiveServices)
            && generation == Volatile.Read(ref workspaceGeneration)) { Owner = this };
        healthWindow = window; window.Closed += (_, _) => { if (ReferenceEquals(healthWindow, window)) healthWindow = null; };
        window.Show();
        await RefreshSharedHoldAsync(identity, generation);
    }

    private void ApplySharedHold(MutationHold hold)
    {
        sharedMutationHold = hold;
        workspace?.ApplyCoordinatorHold(hold);
        RefreshWorkspace();
    }

    private async Task RefreshSharedHoldAsync(AuthServices identity, long generation)
    {
        MutationHold hold;
        try { hold = await identity.Mutations.InspectAsync(); }
        catch { hold = new(true, "Không thể xác minh thao tác đang chờ."); }
        if (ReferenceEquals(identity, model.ActiveServices) && generation == Volatile.Read(ref workspaceGeneration)) ApplySharedHold(hold);
    }
    private async void Settings_Click(object sender, RoutedEventArgs e)
    {
        var identity = workspaceIdentity; if (identity is null || !ReferenceEquals(identity, model.ActiveServices)) return;
        var dialog = new StationSettingsWindow(identity.Client.StationSettings.Copy(), IsDemo) { Owner = this };
        if (ShowSessionDialog(dialog, identity) != true || dialog.Settings is not { } settings) return;
        try
        {
            bool saved = await model.RunInActiveSessionAsync(identity, async () =>
            {
                await Task.Run(() => identity.SettingsStore.Save(settings));
                identity.Client.StationSettings = settings;
            });
            if (!saved) return;
        }
        catch (Exception ex) { if (ReferenceEquals(identity, model.ActiveServices)) ShowFailure(ex); }
    }
    private async void CurrentSummary_Click(object sender, RoutedEventArgs e)
    {
        var identity = workspaceIdentity; if (summaryPending || identity is null || !ReferenceEquals(identity, model.ActiveServices) || model.IsLeavingSession) return;
        summaryPending = true; CurrentSummaryButton.IsEnabled = false; var requestVersion = Volatile.Read(ref summaryGeneration);
        try { var summary = await identity.Client.GetShiftSummaryAsync(false); if (!ReferenceEquals(identity, model.ActiveServices) || requestVersion != Volatile.Read(ref summaryGeneration) || model.IsLeavingSession) return;
            if (summary is null) { OperationStatus.Text = "Máy chủ chưa có dữ liệu tổng kết cho ca hiện tại."; return; }
            await PresentShiftSummaryAsync(summary, false, identity, true);
        }
        catch (Exception) { if (ReferenceEquals(identity, model.ActiveServices)) OperationStatus.Text = "Không tải được tổng kết ca. Đăng xuất vẫn có thể tiếp tục."; }
        finally { summaryPending = false; CurrentSummaryButton.IsEnabled = workspace is not null && !model.IsBusy && !model.IsLeavingSession; }
    }
    private async Task ShowShiftSummaryAsync(ShiftSummary summary, bool previous)
    {
        var identity = model.ActiveServices; if (identity is null) return;
        await PresentShiftSummaryAsync(summary, previous, identity, false);
    }
    private async Task PresentShiftSummaryAsync(ShiftSummary summary, bool previous, AuthServices identity, bool allowRepeat)
    {
        var version = Volatile.Read(ref summaryGeneration);
        await summaryGate.WaitAsync();
        try
        {
            if (activeSessionDialog is not null && activeDialogClosed is { } activeClosed)
                await activeClosed.Task;
            bool logoutPresentation = model.IsLogoutSummary;
            if (version != Volatile.Read(ref summaryGeneration) || !ReferenceEquals(identity, model.ActiveServices)
                || (model.IsLeavingSession && !logoutPresentation)) return;
            if (!allowRepeat && shownShiftSummaryIds.Contains(summary.ShiftId)) return;
            int? queueCount = null;
            try { queueCount = await identity.Client.GetOfflineQueueCountAsync(); } catch (Exception) { }
            if (version != Volatile.Read(ref summaryGeneration) || !ReferenceEquals(identity, model.ActiveServices)
                || (model.IsLeavingSession && !model.IsLogoutSummary)) return;
            ShowSessionDialog(new ShiftSummaryWindow(summary, queueCount, previous, IsDemo) { Owner = this }, identity);
            if (ReferenceEquals(identity, model.ActiveServices) && (!model.IsLeavingSession || model.IsLogoutSummary))
            { identity.Client.MarkShiftSummaryShown(summary.ShiftId); shownShiftSummaryIds.Add(summary.ShiftId); }
        }
        finally { summaryGate.Release(); }
    }
    private bool? ShowSessionDialog(Window dialog, AuthServices? identity)
    {
        if (identity is null || !ReferenceEquals(identity, model.ActiveServices)) return false;
        activeSessionDialog = dialog;
        activeDialogClosed = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        try { return dialog.ShowDialog(); }
        finally
        {
            if (ReferenceEquals(activeSessionDialog, dialog))
            { activeSessionDialog = null; activeDialogClosed?.TrySetResult(); activeDialogClosed = null; }
        }
    }
    private static string DisplayVehicleType(string value) => value switch
    { "MOTORBIKE" => "Xe máy", "LARGE_MOTORBIKE" => "Xe máy phân khối lớn", "CAR" => "Ô tô", "CAR_6_7" => "Ô tô 6–7 chỗ", "CAR_8_9" => "Ô tô 8–9 chỗ", _ => "Chưa xác định" };
    private void Next_Click(object sender, RoutedEventArgs e) { if (workspace?.NextContext() == true) { VehicleImage.Source = null; FaceImage.Source = null; ReceiptText.Text = ""; } }
    private async Task RunBusy(Func<GateWorkspace, Task> action)
    {
        var expected = workspace;
        if (expected is null || busy) return; busy = true; RefreshWorkspace();
        try { await action(expected); if (ReferenceEquals(workspace, expected)) RefreshWorkspace(); }
        catch (Exception ex) { if (ReferenceEquals(workspace, expected)) ShowFailure(ex); }
        finally { busy = false; if (ReferenceEquals(workspace, expected)) RefreshWorkspace(); }
    }
    private async Task Safe(Func<Task> action)
    { try { await action(); } catch (Exception ex) { ShowFailure(ex); } }
    private void ShowFailure(Exception ex)
    { OperationStatus.Text = ex is HttpRequestException ? "Không thể kết nối. Kiểm tra mạng hoặc liên hệ quản trị viên." : "Không thể hoàn tất thao tác. Kiểm tra thông tin và thử lại nếu trạng thái đã rõ."; }
    private async void OnClosing(object? sender, CancelEventArgs e)
    {
        if (closeApproved) return; e.Cancel = true; if (closeInProgress) return;
        closeInProgress = true; heartbeat.Stop(); CancelAutoClose();
        try
        {
            bool closed = await model.CloseAsync();
            if (closed) { closeApproved = true; _ = Dispatcher.BeginInvoke(new Action(Close)); } else if (model.WorkspaceVisible) heartbeat.Start();
        }
        catch (Exception ex) { ShowFailure(ex); if (model.WorkspaceVisible) heartbeat.Start(); }
        finally { closeInProgress = false; }
    }
    private void OnClosed(object? sender, EventArgs e)
    { heartbeat.Stop(); heartbeat.Tick -= Heartbeat_Tick; model.PropertyChanged -= ModelChanged; model.ShiftSummaryRequested = null; model.ShiftSummaryFailed = null; DropWorkspace(); model.Dispose(); }
    private void DemoTools_Click(object sender, RoutedEventArgs e)
    {
        if (demo is null || workspaceIdentity is null) return;
        if (demoToolsWindow is { IsVisible: true } existing) { existing.Activate(); return; }
        var tools = new DemoToolsWindow(demo) { Owner = this }; demoToolsWindow = tools; tools.Closed += DemoToolsClosed; tools.Show();
    }
    private void DemoToolsClosed(object? sender, EventArgs e)
    { if (sender is DemoToolsWindow tools && ReferenceEquals(sender, demoToolsWindow)) { tools.Closed -= DemoToolsClosed; demoToolsWindow = null; } }
    private void WorkspaceHost_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        bool compact = e.NewSize.Height < 650;
        WorkspaceTitlePanel.Visibility = compact ? Visibility.Collapsed : Visibility.Visible;
        CompactMediaActions.Visibility = compact ? Visibility.Visible : Visibility.Collapsed;
        MediaActions.Visibility = compact ? Visibility.Collapsed : Visibility.Visible;
        VehicleTypeText.Visibility = compact ? Visibility.Collapsed : Visibility.Visible;
        CompactFaceFileMenuItem.Visibility = workspace?.CanVerifyFaceFile == true ? Visibility.Visible : Visibility.Collapsed;
        UpdateVehicleImageBounds(compact);
    }
    private void MediaScroll_SizeChanged(object sender, SizeChangedEventArgs e)
    { MediaScrollContent.MinHeight = 0; UpdateVehicleImageBounds(WorkspaceHost.ActualHeight < 650); }

    private void UpdateVehicleImageBounds(bool compact)
    {
        if (compact)
        {
            double height = Math.Max(130, Math.Min(310, MediaScroll.ActualHeight - 110));
            VehicleImageViewport.MinHeight = 130;
            VehicleImageViewport.MaxHeight = height;
            VehicleImageViewport.Height = height;
        }
        else
        {
            VehicleImageViewport.Height = double.NaN;
            VehicleImageViewport.MinHeight = 190;
            VehicleImageViewport.MaxHeight = double.PositiveInfinity;
        }
    }

    private void CompactSources_Click(object sender, RoutedEventArgs e)
    {
        CompactFaceFileMenuItem.Visibility = workspace?.CanVerifyFaceFile == true ? Visibility.Visible : Visibility.Collapsed;
        CompactSourcesButton.ContextMenu.PlacementTarget = CompactSourcesButton;
        CompactSourcesButton.ContextMenu.IsOpen = true;
    }
}
