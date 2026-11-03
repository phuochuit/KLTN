using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Media;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;

namespace LumiPark.Desktop;

public sealed class ParkingMapWindow : Window
{
    private readonly AuthServices services;
    private readonly ParkingMapState map;
    private readonly Action<MutationHold> holdChanged;
    private readonly Action<bool> dispatchPendingChanged;
    private readonly long sessionGeneration;
    private readonly Func<bool> isCurrent;
    private readonly ComboBox floor = new() { MinWidth = 130, Margin = new Thickness(0, 0, 8, 0) };
    private readonly ComboBox zone = new() { MinWidth = 130, Margin = new Thickness(0, 0, 8, 0) };
    private readonly ComboBox vehicle = new() { MinWidth = 125, Margin = new Thickness(0, 0, 8, 0) };
    private readonly ListBox rows = new() { MinHeight = 300 };
    private readonly ObservableCollection<ParkingSlotResponse> mapRows = [];
    private ICollectionView mapView = null!;
    private readonly TextBlock summary = SurfaceUi.Text("Đang tải sơ đồ…", 13, FontWeights.SemiBold);
    private readonly TextBlock state = SurfaceUi.Text("");
    private readonly TextBlock details = SurfaceUi.Text("Chọn một ô để xem thông tin.");
    private readonly ComboBox sessions = new() { MinHeight = 40, DisplayMemberPath = nameof(UnassignedSessionResponse.PlateNumber) };
    private readonly Button dispatch = SurfaceUi.Button("Điều phối xe vào ô", (_, _) => { });
    private bool loading;
    private bool writing;
    private bool closed;
    private MutationHold currentHold = new(false, "");
    private IReadOnlyList<UnassignedSessionResponse> currentSessions = [];

    public ParkingMapWindow(AuthServices services, Action<MutationHold> holdChanged, Action<bool> dispatchPendingChanged, long sessionGeneration, Func<bool> isCurrent)
    {
        this.services = services; this.holdChanged = holdChanged; this.dispatchPendingChanged = dispatchPendingChanged; this.sessionGeneration = sessionGeneration; this.isCurrent = isCurrent;
        map = new ParkingMapState(services.Client);
        Title = "Sơ đồ bãi xe"; SurfaceUi.Configure(this, 1050, 720, 700, 520);
        var root = new DockPanel { Margin = new Thickness(20) };
        var header = new StackPanel();
        header.Children.Add(SurfaceUi.Text("Sơ đồ bãi xe", 22, FontWeights.Bold));
        header.Children.Add(summary);
        var controls = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 8, 0, 10) };
        SetupFilter(floor, "Tất cả tầng"); SetupFilter(zone, "Tất cả khu");
        vehicle.Items.Add("Tất cả loại xe"); vehicle.Items.Add("Ô tô"); vehicle.Items.Add("Xe máy"); vehicle.SelectedIndex = 0;
        floor.SelectionChanged += (_, _) => Render(); zone.SelectionChanged += (_, _) => Render(); vehicle.SelectionChanged += (_, _) => Render();
        controls.Children.Add(floor); controls.Children.Add(zone); controls.Children.Add(vehicle);
        var refresh = SurfaceUi.Button("Làm mới", async (_, _) => await RefreshAsync()); controls.Children.Add(refresh);
        header.Children.Add(controls); header.Children.Add(state); DockPanel.SetDock(header, Dock.Top); root.Children.Add(header);
        var right = new StackPanel { Width = 340, Margin = new Thickness(14, 0, 0, 0) };
        right.Children.Add(SurfaceUi.Text("Chi tiết ô", 16, FontWeights.SemiBold)); right.Children.Add(details);
        right.Children.Add(SurfaceUi.Text("Xe vừa vào chưa được gán", 14, FontWeights.SemiBold)); right.Children.Add(sessions);
        dispatch.IsEnabled = false; dispatch.Click += Dispatch_Click; right.Children.Add(dispatch);
        DockPanel.SetDock(right, Dock.Right); root.Children.Add(right);
        rows.SelectionChanged += (_, _) => SelectionChanged();
        rows.SetValue(ScrollViewer.VerticalScrollBarVisibilityProperty, ScrollBarVisibility.Auto);
        VirtualizingPanel.SetIsVirtualizing(rows, true); VirtualizingPanel.SetVirtualizationMode(rows, VirtualizationMode.Recycling);
        mapView = CollectionViewSource.GetDefaultView(mapRows); rows.ItemsSource = mapView;
        var template = new DataTemplate(typeof(ParkingSlotResponse));
        var border = new FrameworkElementFactory(typeof(Border)); border.SetValue(Border.PaddingProperty, new Thickness(12)); border.SetValue(Border.MarginProperty, new Thickness(3)); border.SetValue(Border.BackgroundProperty, SurfaceUi.TokenBrush("Surface", Brushes.White));
        var text = new FrameworkElementFactory(typeof(TextBlock)); text.SetBinding(TextBlock.TextProperty, new System.Windows.Data.Binding { Converter = new SlotDisplayConverter() });
        border.AppendChild(text); template.VisualTree = border; rows.ItemTemplate = template;
        root.Children.Add(rows); Content = root;
        Loaded += async (_, _) => { await RefreshHoldAsync(); await LoadSessionsAsync(); await RefreshAsync(); };
        Closing += (_, e) => { if (writing) e.Cancel = true; };
        Closed += (_, _) => { closed = true; map.Dispose(); };
    }

    private static void SetupFilter(ComboBox filter, string all) { filter.Items.Add(all); filter.SelectedIndex = 0; }

    private async Task RefreshAsync()
    {
        if (loading || !IsCurrentSession()) return;
        loading = true;
        try
        {
            var snapshot = await map.RefreshAsync();
            if (!IsCurrentSession()) return;
            if (snapshot is null) { state.Text = map.StaleWarning ?? "Chưa thể xác minh sơ đồ bãi xe."; summary.Text = "Chưa có dữ liệu sơ đồ."; return; }
            PopulateFilters(floor, snapshot.Slots.Select(s => s.Floor ?? "Tầng B1"));
            PopulateFilters(zone, snapshot.Slots.Select(s => s.ZoneName));
            summary.Text = $"Tổng {snapshot.Summary.Total} ô · Trống {snapshot.Summary.Available} · Đang đỗ {snapshot.Summary.Occupied} · Cư dân {snapshot.Summary.Reserved} · Đỗ nhờ {snapshot.Summary.Borrowed} · Quá giờ {snapshot.Summary.Overdue} · Cập nhật {snapshot.LoadedAt.ToLocalTime():HH:mm:ss}";
            state.Text = map.StaleWarning ?? ""; SyncRows(snapshot.Slots);
        }
        finally { loading = false; }
    }

    private void PopulateFilters(ComboBox control, IEnumerable<string> values)
    {
        string selected = control.SelectedItem?.ToString() ?? control.Items[0].ToString()!;
        control.Items.Clear(); control.Items.Add(control == floor ? "Tất cả tầng" : "Tất cả khu");
        foreach (var item in values.Where(v => !string.IsNullOrWhiteSpace(v)).Distinct().OrderBy(v => v)) control.Items.Add(item);
        control.SelectedItem = control.Items.Contains(selected) ? selected : control.Items[0];
    }

    private void Render()
    {
        var kind = vehicle.SelectedIndex switch { 1 => ParkingVehicleFilter.Car, 2 => ParkingVehicleFilter.Motorbike, _ => ParkingVehicleFilter.All };
        var visible = map.Filter(floor.SelectedIndex > 0 ? floor.SelectedItem?.ToString() : null,
            zone.SelectedIndex > 0 ? zone.SelectedItem?.ToString() : null, kind).Select(s => s.Id).ToHashSet();
        mapView.Filter = item => item is ParkingSlotResponse slot && visible.Contains(slot.Id);
        mapView.Refresh();
    }

    private void SyncRows(IReadOnlyList<ParkingSlotResponse> slots)
    {
        long? selectedId = (rows.SelectedItem as ParkingSlotResponse)?.Id;
        var ids = slots.Select(s => s.Id).ToHashSet();
        for (int index = mapRows.Count - 1; index >= 0; index--)
            if (!ids.Contains(mapRows[index].Id)) mapRows.RemoveAt(index);
        for (int target = 0; target < slots.Count; target++)
        {
            var next = slots[target];
            int current = -1;
            for (int i = target; i < mapRows.Count; i++)
                if (mapRows[i].Id == next.Id) { current = i; break; }
            if (current < 0) mapRows.Insert(target, next);
            else
            {
                if (current != target) mapRows.Move(current, target);
                if (mapRows[target] != next) mapRows[target] = next;
            }
        }
        Render();
        if (selectedId is { } id) rows.SelectedItem = mapRows.FirstOrDefault(s => s.Id == id);
    }

    private async Task LoadSessionsAsync()
    {
        try
        {
            var available = await services.Client.GetRecentUnassignedSessionsAsync();
            if (!IsCurrentSession()) return;
            currentSessions = available.Where(s => s.SessionId > 0).ToArray();
            sessions.ItemsSource = currentSessions;
        }
        catch (Exception) { if (IsCurrentSession()) state.Text = "Không thể tải danh sách xe vừa vào. Vui lòng làm mới để thử lại."; }
    }

    private void SelectionChanged()
    {
        if (rows.SelectedItem is not ParkingSlotResponse slot) { details.Text = "Chọn một ô để xem thông tin."; dispatch.IsEnabled = false; return; }
        details.Text = $"{slot.SlotCode} · {slot.Floor ?? "Tầng B1"} · {slot.ZoneName}\nTrạng thái: {slot.StatusDescription ?? slot.Status}\nLoại xe: {DisplayAllowedVehicleType(slot.AllowedVehicleType)}\nĐã gán: {Value(slot.AssignedPlate)}\nĐang đỗ: {Value(slot.OccupiedPlate)} · từ {slot.OccupiedEntryTime?.ToString("dd/MM HH:mm") ?? "—"}\nĐỗ nhờ: {Value(slot.BorrowedPlate)} · đến {slot.BorrowedUntil?.ToString("dd/MM HH:mm") ?? "—"}\nQuá giờ: {(slot.Overdue ? "Có" : "Không")}";
        dispatch.IsEnabled = !writing && sessions.SelectedItem is UnassignedSessionResponse session && session.SessionId > 0
            && slot.Id > 0 && !string.IsNullOrWhiteSpace(slot.SlotCode) && !currentHold.IsHeld;
        sessions.SelectionChanged -= SessionSelectionChanged;
        sessions.SelectionChanged += SessionSelectionChanged;
        static string Value(string? value) => string.IsNullOrWhiteSpace(value) ? "Không" : value;
    }

    private void SessionSelectionChanged(object sender, SelectionChangedEventArgs e) => SelectionChanged();

    internal static string DisplayAllowedVehicleType(string? value) => value switch
    {
        "BICYCLE_ELECTRIC_BICYCLE" => "Xe đạp / xe đạp điện",
        "MOTORBIKE" => "Xe máy / xe máy điện",
        "LARGE_MOTORBIKE" => "Xe mô tô phân khối lớn",
        "CAR" => "Ô tô 4-5 chỗ",
        "CAR_6_7" => "Ô tô 6-7 chỗ",
        "CAR_8_9" => "Ô tô 8-9 chỗ",
        _ => "Dùng chung"
    };

    private async void Dispatch_Click(object sender, RoutedEventArgs e)
    {
        if (writing || rows.SelectedItem is not ParkingSlotResponse slot || sessions.SelectedItem is not UnassignedSessionResponse session
            || slot.Id <= 0 || session.SessionId <= 0 || !currentSessions.Any(s => s.SessionId == session.SessionId)
            || map.Snapshot?.Slots.All(s => s.Id != slot.Id) != false || !IsCurrentSession()) return;
        writing = true; dispatch.IsEnabled = false; state.Text = "Đang xử lý yêu cầu điều phối…"; dispatchPendingChanged(true);
        try
        {
            await RefreshHoldAsync();
            if (!IsCurrentSession()) return;
            if (currentHold.IsHeld)
            { state.Text = "Thao tác đang tạm dừng để kiểm tra trạng thái trước đó."; return; }
            await LoadSessionsAsync();
            if (!IsCurrentSession()) return;
            if (!currentSessions.Any(s => s.SessionId == session.SessionId))
            { state.Text = "Xe vừa được xử lý hoặc không còn trong danh sách. Hãy chọn lại."; return; }
            var result = await services.Mutations.SubmitDispatchAsync(slot.Id, session.SessionId);
            if (!IsCurrentSession()) return;
            var hold = await services.Mutations.InspectAsync(); currentHold = hold;
            if (IsCurrentSession()) holdChanged(hold);
            state.Text = result.Outcome switch
            {
                SingleAttemptOutcome.Success when hold.IsHeld => "Máy chủ đã xác nhận điều phối; thao tác đang được giữ để kiểm tra an toàn.",
                SingleAttemptOutcome.Success => "Đã điều phối xe vào ô.",
                SingleAttemptOutcome.Rejected => result.Message ?? "Yêu cầu điều phối bị từ chối.",
                SingleAttemptOutcome.Unknown => "Chưa xác định kết quả điều phối. Không gửi lại yêu cầu; cần kiểm tra trạng thái.",
                _ => result.Message ?? "Yêu cầu chưa được gửi. Kiểm tra lựa chọn và trạng thái thao tác đang chờ."
            };
            if (result.Outcome == SingleAttemptOutcome.Success)
            {
                string outcome = state.Text;
                await LoadSessionsAsync(); await RefreshAsync();
                if (IsCurrentSession()) state.Text = map.StaleWarning is null ? outcome : $"{outcome} {map.StaleWarning}";
            }
        }
        finally
        {
            try { currentHold = await services.Mutations.InspectAsync(); if (IsCurrentSession()) holdChanged(currentHold); }
            catch { currentHold = new(true, "Không thể xác minh thao tác đang chờ."); if (IsCurrentSession()) holdChanged(currentHold); }
            if (IsCurrentSession()) dispatchPendingChanged(false);
            writing = false;
            if (IsCurrentSession()) SelectionChanged();
        }
    }

    private async Task RefreshHoldAsync()
    {
        try { currentHold = await services.Mutations.InspectAsync(); if (IsCurrentSession()) holdChanged(currentHold); }
        catch { currentHold = new(true, "Không thể xác minh thao tác đang chờ."); if (IsCurrentSession()) holdChanged(currentHold); }
        SelectionChanged();
    }
    private bool IsCurrentSession() => !closed && !Dispatcher.HasShutdownStarted && isCurrent() && sessionGeneration >= 0;
}

internal sealed class SlotDisplayConverter : System.Windows.Data.IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
    {
        if (value is not ParkingSlotResponse slot) return "";
        return $"{slot.SlotCode}   ·   {slot.ZoneName} / {slot.Floor ?? "Tầng B1"}   ·   {slot.StatusDescription ?? slot.Status}   ·   {ParkingMapWindow.DisplayAllowedVehicleType(slot.AllowedVehicleType)}";
    }
    public object ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture) => throw new NotSupportedException();
}

public sealed class HealthWindow : Window
{
    private readonly HealthSurface surface;
    private readonly Func<bool> isCurrent;
    private readonly TextBlock results = SurfaceUi.Text("Chưa kiểm tra", 14);
    private readonly Button refresh = SurfaceUi.Button("Kiểm tra lại", async (_, _) => await Task.CompletedTask);

    public HealthWindow(AuthServices services, Func<bool> isCurrent)
    {
        surface = new HealthSurface(services); this.isCurrent = isCurrent;
        Title = "Tình trạng dịch vụ"; SurfaceUi.Configure(this, 720, 520, 600, 420);
        var panel = SurfaceUi.Root();
        panel.Children.Add(SurfaceUi.Text("Tình trạng dịch vụ", 22, FontWeights.Bold));
        panel.Children.Add(SurfaceUi.Text("Chỉ xem thông tin. Hàng chờ không được gửi lại từ cửa sổ này."));
        panel.Children.Add(results);
        refresh.Click += async (_, _) => await RefreshAsync(); panel.Children.Add(refresh); Content = panel;
        Loaded += async (_, _) => await RefreshAsync(); Closed += (_, _) => surface.Dispose();
    }

    private async Task RefreshAsync()
    {
        if (!isCurrent()) return;
        refresh.IsEnabled = false; results.Text = "Đang kiểm tra…";
        var snapshot = await surface.RefreshAsync();
        if (!isCurrent() || snapshot is null) { results.Text = "Không thể xác minh dữ liệu."; refresh.IsEnabled = false; return; }
        static string Health(bool? value) => value switch { true => "Đang hoạt động", false => "Chưa hoạt động", _ => "Chưa xác minh" };
        var queue = snapshot.LegacyPendingCount is { } count ? count.ToString() : "Chưa xác minh";
        var journal = snapshot.JournalReview is null ? "Chưa xác minh" : snapshot.JournalReview.IsHeld ? "Cần kiểm tra trước khi tiếp tục" : "Không có thao tác cần kiểm tra";
        results.Text = $"Spring Web: {Health(snapshot.SpringHealthy)}\nHệ thống nhận dạng: {Health(snapshot.AiHealthy)}\nThao tác đang chờ gửi: {queue}\nTình trạng thao tác trước: {journal}\nKiểm tra lúc {snapshot.CheckedAt.ToLocalTime():HH:mm:ss}";
        refresh.IsEnabled = true;
    }
}
