using System.Globalization;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;

namespace LumiPark.Desktop;

internal static class SurfaceUi
{
    public static Brush TokenBrush(string key, Brush fallback) => Application.Current?.TryFindResource(key) as Brush ?? fallback;
    public static void Configure(Window window, double width, double height, double minWidth, double minHeight)
    {
        var area = SystemParameters.WorkArea;
        window.Width = Math.Min(width, area.Width); window.Height = Math.Min(height, area.Height);
        window.MinWidth = Math.Min(minWidth, area.Width); window.MinHeight = Math.Min(minHeight, area.Height);
        window.MaxWidth = area.Width; window.MaxHeight = area.Height;
        window.FontFamily = Application.Current?.TryFindResource("AppFont") as FontFamily ?? new FontFamily("Segoe UI");
        window.Background = Application.Current?.TryFindResource("Surface") as Brush ?? Brushes.White;
        window.Foreground = Application.Current?.TryFindResource("Ink") as Brush ?? Brushes.Black;
        window.WindowStartupLocation = WindowStartupLocation.CenterOwner;
    }
    public static TextBlock Text(string text, double size = 14, FontWeight? weight = null) => new()
    { Text = text, FontSize = size, FontWeight = weight ?? FontWeights.Normal, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 4) };
    public static Button Button(string text, RoutedEventHandler handler)
    { var button = new Button { Content = text, MinHeight = 42, Padding = new Thickness(14, 8, 14, 8), Margin = new Thickness(5, 0, 0, 0) }; button.Click += handler; return button; }
    public static StackPanel Root() => new() { Margin = new Thickness(22) };
    public static TextBox Field(string value = "") => new() { Text = value, MinHeight = 42, Padding = new Thickness(10, 8, 10, 8), Margin = new Thickness(0, 3, 0, 9) };
    public static StackPanel Labeled(string label, Control input)
    {
        AutomationProperties.SetName(input, label);
        var panel = new StackPanel(); var caption = new Label { Content = label, Target = input, Padding = new Thickness(0), FontWeight = FontWeights.SemiBold };
        panel.Children.Add(caption); panel.Children.Add(input); return panel;
    }
    public static void AddActions(Panel root, params UIElement[] buttons)
    { var actions = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 14, 0, 0) }; foreach (var button in buttons) actions.Children.Add(button); root.Children.Add(actions); }
}

public sealed class ManualCorrectionWindow : Window
{
    private static readonly (string Code, string Label)[] Reasons =
    [
        ("PLATE_NOT_DETECTED", "AI không đọc được biển số"), ("PLATE_CORRECTION", "AI đọc sai biển số"),
        ("VEHICLE_TYPE_CORRECTION", "AI nhận sai loại xe"), ("FACE_REVIEW", "Kết quả khuôn mặt cần xem xét"),
        ("FACE_FAILED", "Đối chiếu khuôn mặt thất bại"), ("REGISTRATION_IMAGE_MISSING", "Thiếu ảnh đăng ký khuôn mặt")
    ];
    private readonly TextBox plate;
    private readonly ComboBox reason, vehicle;
    private readonly TextBox detail;
    private readonly TextBlock error;
    public string CorrectedPlate => plate.Text.Trim();
    public string ReasonCode => (reason.SelectedItem as ComboItem)?.Code ?? "";
    public string ReasonText => detail.Text.Trim();
    public string VehicleType => (vehicle.SelectedItem as ComboItem)?.Code ?? "";

    public ManualCorrectionWindow(string currentPlate, string currentType, bool demo = false)
    {
        Title = "Hiệu chỉnh thông tin lượt xe"; Owner = Application.Current?.MainWindow;
        SurfaceUi.Configure(this, 520, 620, 460, 560);
        var root = SurfaceUi.Root();
        if (demo) root.Children.Add(SurfaceUi.Text("CHẾ ĐỘ TRÌNH DIỄN · DỮ LIỆU MINH HỌA", 12, FontWeights.Bold));
        root.Children.Add(SurfaceUi.Text("Hiệu chỉnh có lưu lý do và bằng chứng ban đầu. Quyền chấp thuận do máy chủ kiểm tra.", 13));
        plate = SurfaceUi.Field(currentPlate); plate.MaxLength = 32; root.Children.Add(SurfaceUi.Labeled("Biển số đã hiệu chỉnh", plate));
        reason = new ComboBox { MinHeight = 42, Margin = new Thickness(0, 3, 0, 9), ItemsSource = Reasons.Select(x => new ComboItem(x.Code, x.Label)).ToArray(), DisplayMemberPath = "Label" };
        reason.SelectedIndex = 0; root.Children.Add(SurfaceUi.Labeled("Lý do xử lý", reason));
        vehicle = new ComboBox { MinHeight = 42, Margin = new Thickness(0, 3, 0, 9), ItemsSource = new[]
        { new ComboItem("MOTORBIKE", "Xe máy"), new ComboItem("LARGE_MOTORBIKE", "Xe máy phân khối lớn"), new ComboItem("CAR", "Ô tô"), new ComboItem("CAR_6_7", "Ô tô 6–7 chỗ"), new ComboItem("CAR_8_9", "Ô tô 8–9 chỗ") }, DisplayMemberPath = "Label" };
        vehicle.SelectedItem = vehicle.Items.Cast<ComboItem>().FirstOrDefault(x => x.Code == currentType);
        root.Children.Add(SurfaceUi.Labeled("Loại xe", vehicle));
        detail = SurfaceUi.Field(); detail.AcceptsReturn = true; detail.TextWrapping = TextWrapping.Wrap; detail.MaxLength = 500; detail.Height = 110;
        root.Children.Add(SurfaceUi.Labeled("Mô tả lý do (10–500 ký tự)", detail));
        error = SurfaceUi.Text("", 12); error.Foreground = SurfaceUi.TokenBrush("Danger", Brushes.Firebrick); AutomationProperties.SetLiveSetting(error, AutomationLiveSetting.Polite); root.Children.Add(error);
        SurfaceUi.AddActions(root, SurfaceUi.Button("Hủy", (_, _) => { DialogResult = false; }), SurfaceUi.Button("Áp dụng hiệu chỉnh", Apply));
        Content = new ScrollViewer { Content = root, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
    }
    private void Apply(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(CorrectedPlate) || ReasonCode.Length == 0 || VehicleType.Length == 0 || ReasonText.Length is < 10 or > 500)
        { error.Text = "Nhập biển số, chọn lý do và loại xe, đồng thời mô tả từ 10 đến 500 ký tự."; return; }
        DialogResult = true;
    }
    private sealed record ComboItem(string Code, string Label);
}

public sealed class StationSettingsWindow : Window
{
    private readonly TextBox vehicleSource, faceSource, frames, closeSeconds;
    private readonly TextBlock error;
    public GateStationSettings? Settings { get; private set; }
    public StationSettingsWindow(GateStationSettings current, bool demo = false)
    {
        Title = "Cài đặt trạm"; SurfaceUi.Configure(this, 540, 590, 480, 520);
        var root = SurfaceUi.Root();
        if (demo) root.Children.Add(SurfaceUi.Text("CHẾ ĐỘ TRÌNH DIỄN · CÀI ĐẶT LƯU TRONG THƯ MỤC MINH HỌA", 12, FontWeights.Bold));
        root.Children.Add(SurfaceUi.Text("Nhập chỉ số camera USB hoặc địa chỉ luồng RTSP/HTTP(S) do camera cung cấp.", 13));
        vehicleSource = SurfaceUi.Field(current.VehicleCameraSource); faceSource = SurfaceUi.Field(current.FaceCameraSource);
        frames = SurfaceUi.Field(current.CameraFrameCount.ToString(CultureInfo.InvariantCulture)); closeSeconds = SurfaceUi.Field(current.BarrierAutoCloseSeconds.ToString(CultureInfo.InvariantCulture));
        root.Children.Add(SurfaceUi.Labeled("Camera xe · chỉ số USB hoặc rtsp/http/https", vehicleSource));
        root.Children.Add(SurfaceUi.Labeled("Camera khuôn mặt · chỉ số USB hoặc rtsp/http/https", faceSource));
        root.Children.Add(SurfaceUi.Labeled("Số khung hình (1–60)", frames));
        root.Children.Add(SurfaceUi.Labeled("Tự đóng mô phỏng sau (1–120 giây)", closeSeconds));
        root.Children.Add(SurfaceUi.Text("Barrier: chỉ có mô phỏng, không điều khiển phần cứng.", 13, FontWeights.SemiBold));
        error = SurfaceUi.Text("", 12); error.Foreground = SurfaceUi.TokenBrush("Danger", Brushes.Firebrick); AutomationProperties.SetLiveSetting(error, AutomationLiveSetting.Polite); root.Children.Add(error);
        SurfaceUi.AddActions(root, SurfaceUi.Button("Hủy", (_, _) => { DialogResult = false; }), SurfaceUi.Button("Lưu", Save));
        Content = new ScrollViewer { Content = root, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
    }
    private void Save(object sender, RoutedEventArgs e)
    {
        try
        {
            var settings = new GateStationSettings { VehicleCameraSource = vehicleSource.Text.Trim(), FaceCameraSource = faceSource.Text.Trim(),
                CameraFrameCount = int.Parse(frames.Text, CultureInfo.InvariantCulture), BarrierAutoCloseSeconds = int.Parse(closeSeconds.Text, CultureInfo.InvariantCulture), BarrierDriver = "SIMULATOR" };
            settings.Validate(); Settings = settings; DialogResult = true;
        }
        catch (Exception ex) when (ex is ArgumentException or FormatException or OverflowException) { error.Text = ex.Message; }
    }
}

public sealed class ShiftSummaryWindow : Window
{
    public ShiftSummaryWindow(ShiftSummary summary, int? legacyQueueCount, bool previous, bool demo = false)
    {
        Title = previous ? "Tổng kết ca trước" : "Tổng kết ca hiện tại"; SurfaceUi.Configure(this, 650, 700, 500, 520);
        var root = SurfaceUi.Root();
        if (demo) root.Children.Add(SurfaceUi.Text("DỮ LIỆU MINH HỌA · KHÔNG PHẢI SỐ LIỆU THẬT", 12, FontWeights.Bold));
        root.Children.Add(SurfaceUi.Text(previous ? "BÁO CÁO BÀN GIAO CA TRƯỚC" : "BÁO CÁO CA HIỆN TẠI", 20, FontWeights.Bold));
        void Add(string label, string value) => root.Children.Add(SurfaceUi.Text($"{label}:  {value}", 14));
        string money(decimal value) => $"{value.ToString("N0", CultureInfo.GetCultureInfo("vi-VN"))} ₫";
        Add("Ca", summary.ShiftName); Add("Nhân viên", summary.OperatorName);
        Add("Thời gian", $"{summary.StartsAt.ToLocalTime():dd/MM/yyyy HH:mm} – {summary.EndsAt.ToLocalTime():dd/MM/yyyy HH:mm}"); Add("Nhiệm vụ", summary.Duty);
        Add("Tổng thu", money(summary.TotalRevenue)); Add("Tiền mặt", money(summary.CashRevenue)); Add("Chuyển khoản", money(summary.BankTransferRevenue)); Add("POS", money(summary.PosRevenue));
        Add("Xe vào / ra", $"{summary.VehicleEntries:N0} / {summary.VehicleExits:N0}"); Add("Xe đang trong bãi", summary.VehiclesCurrentlyInside.ToString("N0"));
        Add("Ô sử dụng / còn trống", $"{summary.OccupiedSlots:N0} / {summary.AvailableSlots:N0}"); Add("Chờ đối soát", summary.PendingReconciliation.ToString("N0")); Add("Xử lý thủ công", summary.ManualOverrides.ToString("N0"));
        Add("Tác vụ cũ còn chờ đồng bộ", legacyQueueCount?.ToString("N0") ?? "Chưa tải được số liệu");
        SurfaceUi.AddActions(root, SurfaceUi.Button(previous ? "Đã xem · tiếp tục" : "Đóng", (_, _) => Close()));
        Content = new ScrollViewer { Content = root, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
    }
}

public sealed class EvidenceComparisonWindow : Window
{
    private readonly EvidenceComparisonResponse data;
    public EvidenceComparisonWindow(EvidenceComparisonResponse data, bool demo = false)
    {
        this.data = data; Title = $"Đối chiếu ảnh vào / ra · Lượt #{data.SessionId}"; SurfaceUi.Configure(this, 1160, 820, 760, 560);
        var root = new DockPanel { Margin = new Thickness(18) };
        var header = new StackPanel(); DockPanel.SetDock(header, Dock.Top);
        if (demo) header.Children.Add(SurfaceUi.Text("DỮ LIỆU MINH HỌA · KHÔNG PHẢI HỒ SƠ THẬT", 12, FontWeights.Bold));
        header.Children.Add(SurfaceUi.Text($"{Status(data.MatchStatus)} · Biển số vào {data.EntryPlate}", 18, FontWeights.Bold));
        header.Children.Add(SurfaceUi.Text($"{data.SummaryMessage} · {SessionStatus(data.SessionStatus)} · Vào {data.EntryTime:dd/MM/yyyy HH:mm} · Ra {(data.ExitTime?.ToString("dd/MM/yyyy HH:mm") ?? "—")}", 13));
        header.Children.Add(SurfaceUi.Text($"Biển số {(data.PlateMatched ? "khớp" : "lệch")}: {data.EntryPlate} / {data.ExitPlate}   ·   Loại xe {(data.VehicleTypeMatched ? "khớp" : "lệch")}: {Vehicle(data.EntryVehicleType)} / {Vehicle(data.ExitVehicleType)}   ·   Khuôn mặt {(data.FaceMatched ? "đạt" : "xem xét")}: {FaceDecision(data.EntryFaceDecision)} / {FaceDecision(data.ExitFaceDecision)}", 13));
        root.Children.Add(header);
        var cards = new UniformGrid { Rows = 2, Columns = 2, Margin = new Thickness(0, 14, 0, 0) };
        cards.Children.Add(Card("Ảnh xe lúc vào", data.EntryVehicleImageBase64, Join(data.EntryPlate, Vehicle(data.EntryVehicleType), Confidence("Phát hiện", data.EntryDetectionConfidence), Confidence("Nhận dạng biển số", data.EntryOcrConfidence), Confidence("Phân loại xe", data.EntryVehicleConfidence), data.EntryTime.ToString("dd/MM/yyyy HH:mm"))));
        cards.Children.Add(Card("Ảnh xe lúc ra", data.ExitVehicleImageBase64, Join(data.ExitPlate, Vehicle(data.ExitVehicleType), Confidence("Phát hiện", data.ExitDetectionConfidence), Confidence("Nhận dạng biển số", data.ExitOcrConfidence), Confidence("Phân loại xe", data.ExitVehicleConfidence), data.ExitTime?.ToString("dd/MM/yyyy HH:mm"))));
        cards.Children.Add(Card("Khuôn mặt lúc vào", data.EntryFaceImageBase64, Join(FaceDecision(data.EntryFaceDecision), Confidence("Độ tương đồng", data.EntryFaceSimilarity))));
        cards.Children.Add(Card("Khuôn mặt lúc ra", data.ExitFaceImageBase64, Join(FaceDecision(data.ExitFaceDecision), Confidence("Độ tương đồng", data.ExitFaceSimilarity))));
        root.Children.Add(new ScrollViewer { Content = cards, VerticalScrollBarVisibility = ScrollBarVisibility.Auto }); Content = root;
        Loaded += async (_, _) => await LoadImagesAsync();
        Closed += (_, _) => { foreach (var (image, _) in images) image.Source = null; images.Clear(); };
    }
    private readonly List<(Image Image, string Data)> images = [];
    private Border Card(string title, string base64, string details)
    {
        var stack = new StackPanel { Margin = new Thickness(8) }; stack.Children.Add(SurfaceUi.Text(title, 14, FontWeights.Bold));
        var image = new Image { Stretch = Stretch.Uniform, MaxHeight = 220, Cursor = Cursors.Hand };
        image.MouseLeftButtonUp += (_, _) => { if (image.Source is BitmapSource bitmap) new EvidenceZoomWindow(bitmap, title) { Owner = this }.ShowDialog(); };
        images.Add((image, base64)); stack.Children.Add(new Border { Background = new SolidColorBrush(Color.FromRgb(233, 237, 247)), MinHeight = 130, Child = image });
        if (string.IsNullOrWhiteSpace(base64)) stack.Children.Add(SurfaceUi.Text("Không có ảnh bằng chứng.", 12));
        stack.Children.Add(SurfaceUi.Text(details, 12));
        return new Border { BorderBrush = new SolidColorBrush(Color.FromRgb(220, 227, 242)), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(12), Margin = new Thickness(5), Padding = new Thickness(8), Child = stack };
    }
    private async Task LoadImagesAsync()
    {
        var pending = images.ToArray();
        foreach (var (target, encoded) in pending)
        {
            if (string.IsNullOrWhiteSpace(encoded)) continue;
            var bitmap = await Task.Run(() => FrozenImageDecoder.DecodeBase64(encoded));
            if (!IsVisible) return;
            target.Source = bitmap;
        }
    }
    private static string Status(string value) => value switch { "MATCH" => "ĐỐI CHIẾU KHỚP", "NO_MATCH" => "CẢNH BÁO KHÔNG KHỚP", _ => "CẦN NHÂN VIÊN KIỂM TRA" };
    private static string SessionStatus(string value) => value switch { "OPEN" => "Đang gửi xe", "COMPLETED" => "Đã hoàn tất lượt", "CANCELLED" => "Đã hủy", _ => "Trạng thái lượt xe khác" };
    private static string FaceDecision(string value) => value switch { "PASS" => "Đạt xác minh", "REVIEW" => "Cần xem xét", "FAIL" or "FAILED" => "Không đạt", "NOT_EVALUATED" => "Chưa đánh giá", _ => "Chưa có kết quả" };
    private static string Vehicle(string value) => value switch { "MOTORBIKE" => "Xe máy", "LARGE_MOTORBIKE" => "Xe máy phân khối lớn", "CAR" => "Ô tô", "CAR_6_7" => "Ô tô 6–7 chỗ", "CAR_8_9" => "Ô tô 8–9 chỗ", _ => "Không xác định" };
    private static string Confidence(string label, double? value) => value.HasValue ? $"{label} {value.Value:P1}" : "";
    private static string Join(params string?[] values) => string.Join(" · ", values.Where(value => !string.IsNullOrWhiteSpace(value)));
}

public sealed class EvidenceZoomWindow : Window
{
    private readonly Image image;
    private readonly TextBlock zoomLabel;
    public ScrollViewer ImageScrollViewer { get; }
    public double ZoomFactor => zoom;
    private double zoom = 1;
    public EvidenceZoomWindow(BitmapSource source, string title)
    {
        Title = $"Phóng to · {title}"; SurfaceUi.Configure(this, 1000, 760, 520, 400);
        image = new Image { Source = source, Stretch = Stretch.None, LayoutTransform = new ScaleTransform(1, 1) };
        ImageScrollViewer = new ScrollViewer { Content = image, HorizontalScrollBarVisibility = ScrollBarVisibility.Auto, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Background = Brushes.DimGray };
        var root = new DockPanel(); var toolbar = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(8) }; DockPanel.SetDock(toolbar, Dock.Top);
        zoomLabel = SurfaceUi.Text("100%", 13, FontWeights.SemiBold);
        toolbar.Children.Add(SurfaceUi.Button("−", (_, _) => Change(-.25))); toolbar.Children.Add(zoomLabel); toolbar.Children.Add(SurfaceUi.Button("Đặt lại 100%", (_, _) => SetZoom(1))); toolbar.Children.Add(SurfaceUi.Button("+", (_, _) => Change(.25))); root.Children.Add(toolbar); root.Children.Add(ImageScrollViewer); Content = root;
        PreviewKeyDown += (_, e) => { if (e.Key is Key.Add or Key.OemPlus) { Change(.25); e.Handled = true; } else if (e.Key is Key.Subtract or Key.OemMinus) { Change(-.25); e.Handled = true; } else if (e.Key == Key.D0) { SetZoom(1); e.Handled = true; } else if (e.Key == Key.Escape) { Close(); e.Handled = true; } };
    }
    private void Change(double delta) => SetZoom(zoom + delta);
    public void SetZoom(double value)
    {
        zoom = Math.Clamp(value, .4, 4);
        ((ScaleTransform)image.LayoutTransform).ScaleX = zoom; ((ScaleTransform)image.LayoutTransform).ScaleY = zoom;
        zoomLabel.Text = $"{zoom:P0}";
    }
}
