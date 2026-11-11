using System.Drawing;

namespace ParkingGateDesktop;

public sealed class MainForm : Form
{
    public event Action? LogoutCompleted;
    public static event Action? OnSharedMapUpdated;

    public static void NotifyMapUpdated()
    {
        OnSharedMapUpdated?.Invoke();
    }

    private readonly ParkingApiClient _parkingApi;
    private readonly GateStationSettingsStore _settingsStore;
    private readonly IBarrierController _barrier;
    private GateStationSettings _stationSettings;
    private readonly TextBox _parkingUrl = new() { Text = "http://localhost:8080", Width = 190 };
    private readonly Label _connection = new() { Text = "Chưa kiểm tra kết nối", AutoSize = true, ForeColor = Color.DarkOrange };
    private readonly System.Windows.Forms.Timer _sessionHeartbeat = new() { Interval = 2000 };
    private readonly System.Windows.Forms.Timer _outboxTimer = new() { Interval = 15000 };
    private readonly System.Windows.Forms.Timer _clockTimer = new() { Interval = 1000 };
    private readonly System.Windows.Forms.Timer _mapResizeTimer = new() { Interval = 150 };
    private readonly Label _mapSummary = new()
    {
        Dock = DockStyle.Fill,
        AutoSize = false,
        Text = "Đang tải số lượng vị trí...",
        TextAlign = ContentAlignment.MiddleRight,
        ForeColor = LumiTheme.Ink,
        Font = LumiTheme.AppFont(9f, FontStyle.Bold),
        Padding = new Padding(8, 0, 8, 0)
    };
    private readonly Label _operatorName = new() { AutoSize = true, Font = new Font("Segoe UI", 10, FontStyle.Bold), ForeColor = Color.FromArgb(23, 54, 93) };
    private readonly Label _operatorShift = new() { AutoSize = true, ForeColor = Color.DimGray };
    private readonly Label _currentTime = new() { AutoSize = true, ForeColor = Color.FromArgb(23, 54, 93) };
    private readonly PictureBox _vehiclePreview = PreviewBox();
    private readonly PictureBox _facePreview = PreviewBox();
    private readonly Label _result = new() { Dock = DockStyle.Fill, AutoSize = false, Padding = new Padding(16), Font = new Font("Segoe UI", 10.5f), BorderStyle = BorderStyle.FixedSingle };
    private readonly Label _aiStatus = new() { Text = "Chọn ảnh hoặc video để đọc biển số", AutoSize = false, Dock = DockStyle.Fill, Height = 44, ForeColor = Color.DimGray, Margin = new Padding(10, 8, 10, 4), TextAlign = ContentAlignment.MiddleLeft };
    private readonly Label _faceStatus = new() { Text = "Chưa chụp khuôn mặt", AutoSize = false, Dock = DockStyle.Fill, Height = 44, ForeColor = Color.DimGray, Padding = new Padding(8, 4, 8, 4), TextAlign = ContentAlignment.MiddleLeft };
    private readonly TextBox _entryPlate = PlateBox();
    private readonly CheckBox _entryNoPlate = new() {
        Text = "Xe không có biển số", AutoSize = true, ForeColor = Color.DarkOrange
    };
    private readonly TextBox _exitPlate = PlateBox();
    private readonly TextBox _exitStoredVehicleType = new() { Width = 320, ReadOnly = true, Text = "Chưa tra cứu lượt gửi" };
    private readonly ComboBox _vehicleType = new() { Width = 320, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Label _residentInfo = new() {
        Width = 320, Height = 95, AutoSize = false, Padding = new Padding(10),
        BorderStyle = BorderStyle.FixedSingle, BackColor = Color.FromArgb(245, 247, 250),
        Text = "Chưa tra cứu cư dân"
    };
    private readonly Button _editAiResult = new() { Text = "CHỈNH SỬA BIỂN SỐ / LOẠI XE", AutoSize = true, Enabled = false };
    private readonly Button _compareEvidenceBtn = new() { Text = "ĐỐI CHIẾU ẢNH VÀO / RA", AutoSize = true };
    private readonly Label _editAiHint = new() { Text = "Chỉ sử dụng sau khi đã có ảnh nhận diện.", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(10, 12, 0, 0), Visible = false };
    private readonly Label _aiWarningBanner = new() { AutoSize = true, ForeColor = Color.FromArgb(180, 83, 9), BackColor = Color.FromArgb(255, 251, 235), Padding = new Padding(8, 4, 8, 4), Visible = false, Margin = new Padding(10, 6, 0, 0) };
    private readonly Panel _faceBorderPanel = new() { Dock = DockStyle.Fill, Padding = new Padding(3), BackColor = Color.FromArgb(203, 213, 225) };
    private readonly TabControl _tabs = new() { Dock = DockStyle.Fill };
    private readonly ComboBox _entryMember = new() { Width = 320, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _exitMember = new() { Width = 320, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _cboExitPaymentMethod = new() { Width = 320, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly TextBox _txtExitPaymentRef = new() { Width = 320, PlaceholderText = "Mã giao dịch / tham chiếu (nếu có)" };

    public enum FaceFocusState { Neutral, Verified, Warning, Rejected }
    private void SetFaceFocusState(FaceFocusState state)
    {
        _faceBorderPanel.BackColor = state switch
        {
            FaceFocusState.Verified => Color.FromArgb(22, 163, 74),
            FaceFocusState.Warning  => Color.FromArgb(217, 119, 6),
            FaceFocusState.Rejected => Color.FromArgb(220, 38, 38),
            _ => Color.FromArgb(203, 213, 225)
        };
    }
    private readonly FlowLayoutPanel _mapPanel = new() { Dock = DockStyle.Fill, AutoScroll = true, WrapContents = true };
    private readonly Label _barrierState = new() { Text = "BARRIER: ĐANG ĐÓNG", AutoSize = true, ForeColor = Color.Firebrick, Font = new Font("Segoe UI", 10, FontStyle.Bold), Margin = new Padding(8, 17, 5, 0) };
    private bool _entryFaceVerified, _exitFaceVerified;
    private bool _entryResidentRegistered;
    private double? _entryFaceSimilarity, _exitFaceSimilarity;
    private long? _entryVerifiedMemberId, _exitVerifiedMemberId;
    private string _entryGuestFaceBase64 = "", _latestFaceImageBase64 = "";
    private bool _exitGuestFaceAvailable;
    private string _entryVehicleEvidenceId = "", _exitVehicleEvidenceId = "";
    private string _entryFaceEvidenceId = "", _exitFaceEvidenceId = "";
    private string _entryOverrideReasonCode = "", _entryOverrideReasonText = "";
    private string _exitOverrideReasonCode = "", _exitOverrideReasonText = "";
    private ParkingResponse? _lastPreview;
    private string? _exitLinkedSessionId;
    private string _exitLinkedPlate = "";
    private string _entryDetectedType = "UNKNOWN", _exitDetectedType = "UNKNOWN";
    private string _entryVehicleFamily = "";
    private List<VehicleTypeDto> _vehicleTypeCatalog = new();
    private bool _syncingVehicleType;
    private string _savedEntryPlate = "", _savedEntryVehicleType = "UNKNOWN";
    private bool _changingNoPlate;
    private bool _checkingSession;
    private bool _syncingOutbox;
    private bool _vehicleBusy, _faceBusy;
    private long? _activeShiftId;

    public MainForm(ParkingApiClient parkingApi, GateStationSettingsStore settingsStore, IBarrierController barrier)
    {
        _parkingApi = parkingApi;
        _settingsStore = settingsStore;
        _barrier = barrier;
        _faceBorderPanel.Controls.Add(_facePreview);
        _cboExitPaymentMethod.Items.AddRange(new object[] { "Tiền mặt (Tự động)", "Chuyển khoản (Ngân hàng)", "Máy POS / Ví điện tử" });
        _cboExitPaymentMethod.SelectedIndex = 0;
        _vehicleType.DisplayMember = nameof(VehicleTypeDto.DisplayName);
        _exitPlate.TextChanged += (_, _) =>
        {
            _lastPreview = null;
            _exitStoredVehicleType.Text = "Chưa tra cứu lượt gửi";
        };
        _vehicleType.SelectedIndexChanged += (_, _) =>
        {
            if (_syncingVehicleType || _vehicleType.SelectedItem is not VehicleTypeDto selected) return;
            string selectedFamily = VehicleFamily(selected);
            if (!string.IsNullOrWhiteSpace(_entryVehicleFamily)
                    && !string.Equals(selectedFamily, _entryVehicleFamily, StringComparison.OrdinalIgnoreCase))
                return;
            _entryDetectedType = selected.Code;
            _entryVehicleFamily = selectedFamily;
            _editAiHint.Visible = true;
            _editAiHint.Text = "Nhân viên đã xác nhận loại xe trong đúng nhóm phương tiện.";
        };
        _stationSettings = settingsStore.Load();
        _parkingApi.StationSettings = _stationSettings;
        _barrier.StatusChanged += BarrierStatusChanged;
        _parkingApi.OperatorProfileChanged += UpdateOperatorHeader;
        _mapResizeTimer.Tick += (_, _) =>
        {
            _mapResizeTimer.Stop();
            ResizeSlots(_mapPanel);
        };
        _mapPanel.Resize += (_, _) =>
        {
            _mapResizeTimer.Stop();
            _mapResizeTimer.Start();
        };

        OnSharedMapUpdated += () => {
            if (!this.IsDisposed && this.IsHandleCreated)
            {
                this.Invoke((MethodInvoker)async delegate {
                    try { await RefreshMapAsync(); } catch { }
                });
            }
        };

        Text = "LumiPark - Trạm gác";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(900, 650);
        Size = new Size(1320, 860);
        Font = LumiTheme.AppFont();
        BackColor = LumiTheme.Canvas;

        var root = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1, Padding = new Padding(22, 18, 22, 14), BackColor = LumiTheme.Canvas };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 136));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
        Controls.Add(root);
        root.Controls.Add(BuildHeader(), 0, 0);
        root.Controls.Add(BuildBody(), 0, 1);
        var editPanel = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.LeftToRight, BackColor = LumiTheme.Canvas, Padding = new Padding(0, 4, 0, 0) };
        editPanel.Controls.Add(_editAiResult);
        editPanel.Controls.Add(_compareEvidenceBtn);
        editPanel.Controls.Add(_editAiHint);
        root.Controls.Add(editPanel, 0, 2);
        void ApplyRootResponsiveLayout()
        {
            bool compact = ClientSize.Width < 1450;
            root.RowStyles[0].Height = compact ? 202 : 152;
            root.Padding = compact
                ? new Padding(14, 12, 14, 10)
                : new Padding(22, 18, 22, 14);
        }
        Resize += (_, _) => ApplyRootResponsiveLayout();
        ApplyRootResponsiveLayout();
        _editAiResult.Click += (_, _) => EditAiResult();
        _compareEvidenceBtn.Click += async (_, _) => await OpenEvidenceComparisonAsync();
        _vehiclePreview.Cursor = Cursors.Hand;
        _facePreview.Cursor = Cursors.Hand;
        _vehiclePreview.Click += (_, _) => {
            if (_vehiclePreview.Image != null)
                EvidenceComparisonDialog.ShowZoomDialog("Ảnh xe / biển số hiện tại", _vehiclePreview.Image, this);
        };
        _facePreview.Click += (_, _) => {
            if (_facePreview.Image != null)
                EvidenceComparisonDialog.ShowZoomDialog("Ảnh khuôn mặt hiện tại", _facePreview.Image, this);
        };
        _tabs.SelectedIndexChanged += (_, _) => UpdateManualEditState();

        this.Shown += async (_, _) =>
        {
            ConfigureApis();
            await Task.Yield();
            await Task.WhenAll(CheckServicesAsync(refreshMap: false), LoadVehicleTypeCatalogAsync());
            await Task.Delay(100);
            await RefreshMapAsync();
            if (_parkingApi.CurrentOperator is not null) UpdateOperatorHeader(_parkingApi.CurrentOperator);
            UpdateClock();
            _clockTimer.Start();
            _sessionHeartbeat.Start();
            _outboxTimer.Start();

            // Tu dong lam moi so do vi tri moi 5 giay de dong bo voi web.
            bool mapRefreshing = false;
            var mapTimer = new System.Windows.Forms.Timer { Interval = 5000 };
            mapTimer.Tick += async (_, _) =>
            {
                if (mapRefreshing) return;
                mapRefreshing = true;
                try { await RefreshMapAsync(); } catch { } finally { mapRefreshing = false; }
            };
            mapTimer.Start();
            FormClosed += (_, _) => mapTimer.Dispose();
        };
        _sessionHeartbeat.Tick += ValidateSessionHeartbeat;
        _outboxTimer.Tick += async (_, _) => await SyncOutboxQuietlyAsync();
        _clockTimer.Tick += (_, _) => UpdateClock();
        FormClosing += LogoutBeforeClose;
        FormClosed += (_, _) => {
            _parkingApi.OperatorProfileChanged -= UpdateOperatorHeader;
            _barrier.StatusChanged -= BarrierStatusChanged;
            _sessionHeartbeat.Dispose();
            _outboxTimer.Dispose();
            _clockTimer.Dispose();
            _mapResizeTimer.Dispose();
        };
        LumiTheme.Apply(this);
    }

    private Control BuildHeader()
    {
        var panel = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 2, ColumnCount = 2, BackColor = LumiTheme.Surface, Padding = new Padding(18, 10, 18, 9), Margin = new Padding(0, 0, 0, 10) };
        panel.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 58));
        panel.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 42));
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
        LumiTheme.StyleCard(panel);

        var brand = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false, BackColor = LumiTheme.Surface };
        brand.Controls.Add(new Label { Text = "LumiPark", AutoSize = true, Font = LumiTheme.AppFont(21, FontStyle.Bold), ForeColor = LumiTheme.Primary, Margin = new Padding(0, 4, 16, 0) });
        brand.Controls.Add(new Label { Text = "TRẠM GÁC", AutoSize = true, Font = LumiTheme.AppFont(9.5f, FontStyle.Bold), ForeColor = LumiTheme.Accent, BackColor = LumiTheme.SurfaceSoft, Padding = new Padding(12, 7, 12, 7), Margin = new Padding(0, 7, 0, 0) });
        panel.Controls.Add(brand, 0, 0);

        var operatorPanel = new FlowLayoutPanel {
            Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false,
            Margin = Padding.Empty, Padding = new Padding(0, 1, 0, 0), BackColor = LumiTheme.Surface
        };
        operatorPanel.Controls.Add(_operatorName);
        operatorPanel.Controls.Add(_operatorShift);
        operatorPanel.Controls.Add(_currentTime);
        _operatorName.Margin = Padding.Empty;
        _operatorShift.Margin = Padding.Empty;
        _currentTime.Margin = Padding.Empty;
        panel.Controls.Add(operatorPanel, 1, 0);

        var actions = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = true, AutoScroll = false, BackColor = LumiTheme.Surface, Padding = new Padding(0, 2, 0, 0) };
        actions.Controls.Add(new Label { Text = "Hệ thống", AutoSize = true, ForeColor = LumiTheme.Muted, Font = LumiTheme.AppFont(8.8f, FontStyle.Bold), Margin = new Padding(0, 13, 7, 0) });
        actions.Controls.Add(LumiTheme.InputFrame(_parkingUrl, 170));
        actions.Controls.Add(CreateButton("Kiểm tra", async (_, _) => await CheckServicesAsync()));
        actions.Controls.Add(CreateButton("Sơ đồ bãi", (_, _) => { ConfigureApis(); new ParkingMapForm(_parkingApi).Show(this); }));
        actions.Controls.Add(CreateButton("Cấu hình trạm", (_, _) => ShowStationSettings()));
        actions.Controls.Add(CreateButton("MỞ BARRIER", async (_, _) => await SetBarrierAsync(true, "Mở thủ công")));
        actions.Controls.Add(CreateButton("ĐÓNG BARRIER", async (_, _) => await SetBarrierAsync(false, "Đóng thủ công")));
        actions.Controls.Add(CreateButton("Đăng xuất", LogoutClicked));
        _barrierState.Margin = new Padding(12, 13, 6, 0);
        _connection.Margin = new Padding(4, 13, 0, 0);
        actions.Controls.Add(_barrierState);
        actions.Controls.Add(_connection);
        panel.Controls.Add(actions, 0, 1);
        panel.SetColumnSpan(actions, 2);
        panel.Resize += (_, _) =>
        {
            bool compact = panel.ClientSize.Width < 1400;
            panel.RowStyles[0].Height = 68;
            panel.RowStyles[1].Height = compact ? 104 : 52;
        };
        return panel;
    }

    private void UpdateOperatorHeader(OperatorProfile profile)
    {
        if (InvokeRequired) { BeginInvoke(() => UpdateOperatorHeader(profile)); return; }
        long? previousActiveShiftId = _activeShiftId;
        _activeShiftId = profile.ActiveShift?.Id;
        string displayName = string.IsNullOrWhiteSpace(profile.FullName) ? profile.Username : profile.FullName;
        _operatorName.Text = $"Nhân viên: {displayName}";
        _operatorShift.Text = profile.ActiveShift is null
            ? "Ca trực: Chưa có ca đang hoạt động"
            : $"Ca trực: {profile.ActiveShift.Name} ({profile.ActiveShift.StartsAt.ToLocalTime():HH:mm}–{profile.ActiveShift.EndsAt.ToLocalTime():HH:mm})";
        _operatorShift.ForeColor = profile.ActiveShift is null ? Color.Firebrick : Color.SeaGreen;
        if (_activeShiftId.HasValue && _activeShiftId != previousActiveShiftId)
            BeginInvoke((MethodInvoker)async delegate { await ShowShiftSummaryAsync(previous: true); });
    }

    private void UpdateClock() => _currentTime.Text = $"Thời gian: {DateTime.Now:dd/MM/yyyy HH:mm:ss}";

    private Control BuildBody()
    {
        var split = new SplitContainer { Dock = DockStyle.Fill, SplitterWidth = 14, BackColor = LumiTheme.Canvas, BorderStyle = BorderStyle.None };
        split.Resize += (_, _) =>
        {
            if (split.ClientSize.Width < 760) return;
            bool compact = split.ClientSize.Width < 1320;
            double ratio = compact ? 0.55 : 0.64;
            int minLeft = compact ? 380 : 560;
            int minRight = compact ? 360 : 420;
            int maximum = split.ClientSize.Width - minRight - split.SplitterWidth;
            if (maximum < minLeft) return;
            split.SplitterDistance = Math.Clamp((int)(split.ClientSize.Width * ratio), minLeft, maximum);
        };
        split.Panel1.BackColor = LumiTheme.Surface;
        split.Panel2.BackColor = LumiTheme.Surface;
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1, Padding = new Padding(14), BackColor = LumiTheme.Surface };
        left.RowStyles.Add(new RowStyle(SizeType.Percent, 68));
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
        left.RowStyles.Add(new RowStyle(SizeType.Percent, 32));
        left.Controls.Add(BuildCapturePreviews(), 0, 0);
        var media = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoScroll = true, FlowDirection = FlowDirection.LeftToRight, BackColor = LumiTheme.Surface };
        media.Controls.Add(CreateButton("XỬ LÝ SONG SONG", async (_, _) => await SelectAndProcessParallelAsync()));
        media.Controls.Add(new Label { Text = "Media xe và camera mặt sẽ chạy đồng thời", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(10, 17, 0, 0) });
        left.Controls.Add(media, 0, 1);
        var mapBox = new GroupBox { Text = "Sơ đồ vị trí đỗ", Dock = DockStyle.Fill, Padding = new Padding(8) };
        var mapLayout = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 2, ColumnCount = 1 };
        mapLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 36));
        mapLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        mapLayout.Controls.Add(_mapSummary, 0, 0);
        mapLayout.Controls.Add(_mapPanel, 0, 1);
        mapBox.Controls.Add(mapLayout);
        left.Controls.Add(mapBox, 0, 2);
        split.Panel1.Controls.Add(left);

        var right = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 2, ColumnCount = 1, Padding = new Padding(14), BackColor = LumiTheme.Surface };
        right.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 160));
        _tabs.TabPages.Add(BuildEntryTab());
        _tabs.TabPages.Add(BuildExitTab());
        right.Controls.Add(_tabs, 0, 0);
        right.Controls.Add(_result, 0, 1);
        split.Panel2.Controls.Add(right);
        return split;
    }

    private TabPage BuildEntryTab()
    {
        var tab = new TabPage("XE VÀO") { Padding = new Padding(18) };
        var flow = FormFlow();
        flow.Controls.Add(Field("Biển số nhận dạng", _entryPlate));
        _entryNoPlate.CheckedChanged += (_, _) => {
            if (_changingNoPlate) return;
            _entryPlate.ReadOnly = _entryNoPlate.Checked;
            if (_entryNoPlate.Checked) {
                if (!string.IsNullOrWhiteSpace(_entryPlate.Text))
                {
                    var choice = MessageBox.Show(this,
                        $"Ảnh hiện tại đã đọc được biển số {_entryPlate.Text}.\r\n\r\nBạn có chắc đây là xe không có biển số?",
                        "Xác nhận xe không có biển số",
                        MessageBoxButtons.YesNo, MessageBoxIcon.Question,
                        MessageBoxDefaultButton.Button2);
                    if (choice != DialogResult.Yes)
                    {
                        _changingNoPlate = true;
                        _entryNoPlate.Checked = false;
                        _entryPlate.ReadOnly = false;
                        _changingNoPlate = false;
                        return;
                    }
                }
                _savedEntryPlate = _entryPlate.Text;
                _savedEntryVehicleType = _entryDetectedType;
                _entryPlate.Text = "";
                SetEntryVehicleType("BICYCLE_ELECTRIC_BICYCLE");
                _residentInfo.Text = "XE KHÔNG BIỂN SỐ\r\nBắt buộc ảnh xe và khuôn mặt; hệ thống sẽ sinh mã lượt NPL.";
                _residentInfo.BackColor = Color.FromArgb(255, 247, 230);
            }
            else
            {
                if (string.IsNullOrWhiteSpace(_entryPlate.Text) && !string.IsNullOrWhiteSpace(_savedEntryPlate))
                    _entryPlate.Text = _savedEntryPlate;
                if (_savedEntryVehicleType != "UNKNOWN")
                {
                    SetEntryVehicleType(_savedEntryVehicleType);
                }
            }
        };
        flow.Controls.Add(_entryNoPlate);
        flow.Controls.Add(ComboField("Loại xe nhân viên xác nhận", _vehicleType));
        flow.Controls.Add(CreateButton("TRA CỨU CƯ DÂN", async (_, _) => await LookupResidentAsync()));
        flow.Controls.Add(ComboField("Người đang điều khiển", _entryMember));
        flow.Controls.Add(new Label { Text = "Cư dân / gói gửi xe", AutoSize = true, Font = new Font("Segoe UI", 10, FontStyle.Bold) });
        flow.Controls.Add(_residentInfo);
        flow.Controls.Add(CreateButton("XÁC NHẬN XE VÀO", async (_, _) => await EntryAsync()));
        tab.Controls.Add(flow);
        return tab;
    }

    private TabPage BuildExitTab()
    {
        var tab = new TabPage("XE RA") { Padding = new Padding(18) };
        var flow = FormFlow();
        flow.Controls.Add(Field("Biển số nhận dạng", _exitPlate));
        flow.Controls.Add(Field("Loại xe đã xác nhận lúc vào", _exitStoredVehicleType));
        flow.Controls.Add(CreateButton("TRA CỨU NGƯỜI ĐƯỢC PHÉP", async (_, _) => await LookupResidentAsync(false)));
        flow.Controls.Add(ComboField("Người đang lấy xe", _exitMember));
        flow.Controls.Add(ComboField("Phương thức thanh toán", _cboExitPaymentMethod));
        flow.Controls.Add(Field("Mã giao dịch / tham chiếu", _txtExitPaymentRef));
        flow.Controls.Add(CreateButton("ĐỐI CHIẾU ẢNH VÀO / RA", async (_, _) => await OpenEvidenceComparisonAsync()));
        flow.Controls.Add(CreateButton("XEM TRƯỚC PHÍ", async (_, _) => await PreviewExitAsync()));
        flow.Controls.Add(CreateButton("XÁC NHẬN XE RA", async (_, _) => await ConfirmExitAsync()));
        tab.Controls.Add(flow);
        return tab;
    }

    private async Task SelectAndRecognizeAsync(bool video)
    {
        using var dialog = new OpenFileDialog {
            Filter = video ? "Video|*.mp4;*.avi;*.mov;*.mkv;*.wmv;*.m4v;*.webm" : "Ảnh|*.jpg;*.jpeg;*.png;*.bmp;*.webp"
        };
        if (dialog.ShowDialog() != DialogResult.OK) return;
        await ProcessMediaAsync(dialog.FileName, video);
        await LookupResidentAsync(_tabs.SelectedIndex == 0);
    }

    private async Task SelectAndProcessParallelAsync()
    {
        using var dialog = new OpenFileDialog {
            Filter = "Ảnh hoặc video|*.jpg;*.jpeg;*.png;*.bmp;*.webp;*.mp4;*.avi;*.mov;*.mkv;*.wmv;*.m4v;*.webm"
        };
        if (dialog.ShowDialog() != DialogResult.OK) return;
        string extension = Path.GetExtension(dialog.FileName).ToLowerInvariant();
        bool video = extension is ".mp4" or ".avi" or ".mov" or ".mkv" or ".wmv" or ".m4v" or ".webm";
        await ProcessVehicleAndFaceAsync(() => ProcessMediaAsync(dialog.FileName, video));
    }

    private async Task ProcessMediaAsync(string path, bool video)
    {
        try
        {
            _result.Text = "";
            _result.BackColor = SystemColors.Control;
            ConfigureApis();
            SetVehicleBusy(true, video ? "Đang chọn frame tốt nhất từ video..." : "Đang nhận dạng ảnh xe...");
            if (!video) ShowLocalImage(path);
            var started = System.Diagnostics.Stopwatch.StartNew();
            AnprResponse response = video
                ? await _parkingApi.RecognizeVideoAsync(path, CurrentOperation())
                : await _parkingApi.RecognizeImageAsync(path, CurrentOperation());
            started.Stop();
            ApplyRecognition(response, started.Elapsed);
        }
        catch (Exception ex) { ShowError(ex); }
        finally { SetVehicleBusy(false, _aiStatus.Text); }
    }

    private async Task RecognizeVehicleCameraAsync()
    {
        try
        {
            ConfigureApis();
            SetVehicleBusy(true, "Đang chụp các frame biển số...");
            var started = System.Diagnostics.Stopwatch.StartNew();
            var response = await _parkingApi.RecognizeCameraAsync(CurrentOperation());
            started.Stop();
            ApplyRecognition(response, started.Elapsed);
        }
        catch (Exception ex) { ShowError(ex); }
        finally { SetVehicleBusy(false, _aiStatus.Text); }
    }

    private async Task CaptureBothCamerasAsync()
    {
        await ProcessVehicleAndFaceAsync(RecognizeVehicleCameraAsync);
    }

    private async Task ProcessVehicleAndFaceAsync(Func<Task> vehicleOperation)
    {
        bool entry = _tabs.SelectedIndex == 0;
        _latestFaceImageBase64 = "";
        await Task.WhenAll(vehicleOperation(), CaptureFaceSnapshotAsync(entry));
        await LookupResidentAsync(entry);
        var combo = entry ? _entryMember : _exitMember;
        if (combo.SelectedItem is AuthorizedMemberResponse || (!entry && _exitGuestFaceAvailable))
            await VerifyLatestCapturedFaceAsync(entry);
    }

    private void ApplyRecognition(AnprResponse response, TimeSpan elapsed)
    {
        if (_tabs.SelectedIndex == 0) {
            if (!_entryNoPlate.Checked) SetEntryVehicleFamilyFromRecognition(response.VehicleType);
            _entryVehicleEvidenceId = response.EvidenceId ?? "";
        } else {
            _exitDetectedType = response.VehicleType;
            _exitVehicleEvidenceId = response.EvidenceId ?? "";
        }
        if (_tabs.SelectedIndex == 0) _entryPlate.Text = response.PlateText;
        else _exitPlate.Text = response.PlateText;
        if (!string.IsNullOrWhiteSpace(response.AnnotatedImageBase64))
        {
            byte[] bytes = Convert.FromBase64String(response.AnnotatedImageBase64);
            using var stream = new MemoryStream(bytes);
            using var image = Image.FromStream(stream);
            _vehiclePreview.Image?.Dispose();
            _vehiclePreview.Image = new Bitmap(image);
        }
        bool lowConfidence = string.IsNullOrWhiteSpace(response.PlateText) || response.OcrConfidence < 0.45 || response.DetectionConfidence < 0.25;
        _aiStatus.ForeColor = lowConfidence ? Color.DarkOrange : Color.SeaGreen;
        _aiStatus.Text = string.IsNullOrWhiteSpace(response.PlateText)
            ? $"Chưa đọc rõ biển số ({elapsed.TotalSeconds:0.00}s). Vui lòng chụp lại hoặc nhập thủ công."
            : _tabs.SelectedIndex != 0
                ? $"Đã đọc biển số {response.PlateText}. Loại xe được lấy từ lượt gửi lúc vào. | {elapsed.TotalSeconds:0.00}s"
            : response.VehicleType == "UNKNOWN"
                ? $"Đã đọc biển số {response.PlateText}. Chưa xác định được nhóm xe, vui lòng chọn loại xe."
                : $"Đã đọc biển số {response.PlateText} | Đã xác định {DisplayVehicleFamily(response.VehicleType)}, vui lòng xác nhận loại xe | {elapsed.TotalSeconds:0.00}s";
        if (response.Quality?.Warnings != null && response.Quality.Warnings.Count > 0)
        {
            var msgs = response.Quality.Warnings
                .Select(w => string.IsNullOrWhiteSpace(w.Message) ? w.Code : w.Message)
                .Where(s => !string.IsNullOrWhiteSpace(s));
            string joined = string.Join(", ", msgs);
            if (!string.IsNullOrWhiteSpace(joined))
            {
                _aiWarningBanner.Text = "Hình ảnh cần kiểm tra: " + joined;
                _aiWarningBanner.Visible = true;
            }
            else
            {
                _aiWarningBanner.Text = "";
                _aiWarningBanner.Visible = false;
            }
        }
        else
        {
            _aiWarningBanner.Text = "";
            _aiWarningBanner.Visible = false;
        }
        _editAiHint.Visible = true;
        _editAiHint.Text = "Chỉ sử dụng sau khi đã có ảnh nhận diện.";
        UpdateManualEditState();
    }

    private async Task CheckServicesAsync(bool refreshMap = true)
    {
        try
        {
            ConfigureApis();
            var checks = await Task.WhenAll(_parkingApi.HealthAsync(), _parkingApi.AiHealthAsync());
            int pending = checks[0] ? await _parkingApi.FlushOutboxAsync() : -1;
            _connection.Text = pending > 0
                ? $"Còn {pending} thao tác cần gửi hoặc kiểm tra"
                : checks.All(x => x)
                    ? "Hệ thống đang hoạt động - không có thao tác chờ"
                    : "Hệ thống chưa sẵn sàng";
            _connection.ForeColor = pending > 0 ? Color.DarkOrange
                : checks.All(x => x) ? Color.SeaGreen : Color.Firebrick;
            if (refreshMap && checks.All(x => x)) await RefreshMapAsync();
        }
        catch (Exception ex) { ShowError(ex); }
    }

    private async Task SyncOutboxQuietlyAsync()
    {
        if (_syncingOutbox || !_parkingApi.IsAuthenticated) return;
        _syncingOutbox = true;
        try
        {
            int pending = await _parkingApi.FlushOutboxAsync();
            if (pending > 0)
            {
                _connection.Text = $"Còn {pending} thao tác cần gửi hoặc kiểm tra";
                _connection.ForeColor = Color.DarkOrange;
            }
            else if (_connection.ForeColor == Color.DarkOrange
                    && _connection.Text.Contains("thao tác", StringComparison.OrdinalIgnoreCase))
            {
                _connection.Text = "Đã đồng bộ hết thao tác đang chờ";
                _connection.ForeColor = Color.SeaGreen;
                NotifyMapUpdated();
            }
        }
        catch (DesktopAuthenticationException) { }
        catch (Exception)
        {
            // Giữ outbox trên đĩa; lần đồng bộ kế tiếp sẽ thử lại.
        }
        finally { _syncingOutbox = false; }
    }

    private async Task EntryAsync()
    {
        try
        {
            ConfigureApis();
            var entryRequest = Request(_entryPlate.Text);
            if (entryRequest == null) return;
            var response = await _parkingApi.EntryAsync(entryRequest);
            ShowParkingResult(response);
            if(!response.Warning || IsManual(true)) AutoOpenBarrier("Xe vào đã được xác nhận");
            NotifyMapUpdated();
            _exitPlate.Text = response.PlateNumber;
            _entryGuestFaceBase64 = "";

            if (!response.Warning || IsManual(true))
            {
                await Task.Delay(2000);
                ClearEntryForm();
            }
        }
        catch (Exception ex) { ShowError(ex); }
    }

    private async Task LookupResidentAsync(bool entry = true)
    {
        try
        {
            ConfigureApis();
            string plate = entry ? _entryPlate.Text : _exitPlate.Text;
            if (entry && _entryNoPlate.Checked)
            {
                _residentInfo.Text = "XE KHÔNG BIỂN SỐ\r\nBắt buộc ảnh xe và khuôn mặt; hệ thống sẽ sinh mã lượt NPL.";
                _residentInfo.BackColor = Color.FromArgb(255, 247, 230);
                _entryMember.DataSource = null;
                return;
            }
            string card = NormalizePlate(plate);
            if (string.IsNullOrWhiteSpace(plate))
            {
                _residentInfo.Text = "Nhập hoặc nhận diện biển số để tra cứu";
                _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
                return;
            }
            ResidentLookupResponse resident = await _parkingApi.LookupResidentAsync(plate, card);
            if (!entry)
            {
                _exitStoredVehicleType.Text = "Đang tra cứu loại xe lúc vào...";
                var openSession = await _parkingApi.GetOpenSessionAsync(plate);
                if (!string.Equals(NormalizePlate(_exitPlate.Text), NormalizePlate(plate), StringComparison.OrdinalIgnoreCase))
                    return;
                _exitStoredVehicleType.Text = openSession == null
                    ? "Không tìm thấy lượt xe đang gửi"
                    : DisplayVehicleType(openSession.VehicleType);
            }
            if (entry) _entryResidentRegistered = resident.Registered;
            ComboBox combo = entry ? _entryMember : _exitMember;
            combo.DataSource = resident.AuthorizedMembers?.ToList() ?? new List<AuthorizedMemberResponse>();
            if(!entry) _exitGuestFaceAvailable=resident.GuestEntryFaceAvailable;
            if(entry){_entryFaceVerified=false;_entryFaceSimilarity=null;_entryVerifiedMemberId=null;}else{_exitFaceVerified=false;_exitFaceSimilarity=null;_exitVerifiedMemberId=null;}
            if (resident.Registered && !string.IsNullOrWhiteSpace(resident.VehicleType))
            {
                if (entry) SetEntryVehicleType(resident.VehicleType);
                else _exitDetectedType = resident.VehicleType;
            }
            _residentInfo.BackColor = resident.Registered && resident.CardMatched
                ? Color.FromArgb(234, 245, 239) : Color.FromArgb(255, 247, 230);
            string pass = resident.PassType switch
            {
                "MONTHLY" => resident.PackageValid
                    ? $"Gói 30 ngày - hạn {resident.ValidUntil:dd/MM/yyyy}"
                    : "Gói 30 ngày không còn hiệu lực",
                "DAILY" => resident.PackageValid
                    ? $"Gói ngày - hạn {resident.ValidUntil:dd/MM/yyyy}"
                    : "Gói ngày không còn hiệu lực",
                "PER_VISIT" => "Chưa mua gói (dữ liệu cũ)",
                _ => "Chưa gán thẻ / gói"
            };
            _residentInfo.Text = resident.Registered
                ? $"Cư dân: {resident.OwnerName}\r\nCăn hộ: {Blank(resident.ApartmentNumber)}\r\nĐiện thoại: {Blank(resident.OwnerPhone)}\r\nGói: {pass}\r\n{resident.Message}"
                : $"KHÁCH VÃNG LAI\r\n{resident.Message}";
        }
        catch (Exception ex)
        {
            _residentInfo.BackColor = Color.MistyRose;
            _residentInfo.Text = "Không tra cứu được cư dân\r\n" + ex.Message;
            if (!entry) _exitStoredVehicleType.Text = "Chưa tra cứu được lượt gửi. Vui lòng thử lại.";
        }
    }

    private async Task PreviewExitAsync()
    {
        try
        {
            ConfigureApis();
            var previewRequest = BuildGateRequest(_exitPlate.Text, includePayment: false);
            if (previewRequest == null) return;
            _lastPreview = await _parkingApi.PreviewExitAsync(previewRequest);
            _exitStoredVehicleType.Text = DisplayVehicleType(_lastPreview.VehicleType);
            ShowParkingResult(_lastPreview);
        }
        catch (Exception ex) when (IsMissingOpenSession(ex)) { await LinkNoPlateSessionAsync(); }
        catch (Exception ex) { ShowError(ex); }
    }

    private static bool IsMissingOpenSession(Exception ex) =>
        ex.Message.Contains("Không tìm thấy lượt xe vào", StringComparison.OrdinalIgnoreCase);

    /// <summary>Mã phiên đang nối với biển hiện tại, nếu nhân viên đã chọn lượt đỗ.</summary>
    private long? LinkedExitSessionId(string normalizedPlate) =>
        long.TryParse(_exitLinkedSessionId, out var id) && id > 0
        && string.Equals(_exitLinkedPlate, normalizedPlate, StringComparison.OrdinalIgnoreCase)
            ? id : null;

    private async Task LinkNoPlateSessionAsync()
    {
        List<ExitCandidateResponse> candidates;
        try { candidates = await _parkingApi.ExitCandidatesAsync(); }
        catch (Exception ex) { ShowError(ex); return; }
        if (candidates.Count == 0)
        {
            MessageBox.Show(this,
                "Không có xe nào đang đỗ bằng mã tạm để nối với biển vừa đọc.\n\n"
                + "Vui lòng kiểm tra lại biển số hoặc dùng ĐỐI CHIẾU ẢNH VÀO / RA.",
                "Không tìm thấy lượt xe vào", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        using var dialog = new ExitSessionPickerDialog(_exitPlate.Text, candidates);
        if (dialog.ShowDialog(this) != DialogResult.OK || dialog.Selected is null) return;
        _exitLinkedSessionId = dialog.Selected.SessionId.ToString();
        _exitLinkedPlate = NormalizePlate(_exitPlate.Text);
        _lastPreview = null;
        await PreviewExitAsync();
    }

    private async Task ConfirmExitAsync()
    {
        try
        {
            if (_lastPreview == null || !string.Equals(NormalizePlate(_lastPreview.PlateNumber ?? ""), NormalizePlate(_exitPlate.Text), StringComparison.OrdinalIgnoreCase))
            {
                await PreviewExitAsync();
                if (_lastPreview == null) return;
            }
            ConfigureApis();
            var exitRequest = BuildGateRequest(_exitPlate.Text, includePayment: true);
            if (exitRequest == null) return;
            var response = await _parkingApi.ConfirmExitAsync(exitRequest);
            ShowParkingResult(response);
            if (!response.Warning || IsManual(false)) AutoOpenBarrier("Xe ra đã được xác nhận và hoàn tất thanh toán");
            NotifyMapUpdated();
            _lastPreview = null;

            if (!response.Warning || IsManual(false))
            {
                await Task.Delay(2000);
                ClearExitForm();
            }
        }
        catch (Exception ex) { ShowError(ex); }
    }

    private async Task VerifyFaceAsync(bool entry)
    {
        try
        {
            ConfigureApis();
            SetFaceBusy(true, "Đang chụp và xác thực khuôn mặt...");
            var started = System.Diagnostics.Stopwatch.StartNew();
            var member = (entry ? _entryMember : _exitMember).SelectedItem as AuthorizedMemberResponse;
            var response = member != null
                ? await _parkingApi.VerifyCameraAsync(ResolveRegisteredMemberId(entry), entry ? "ENTRY" : "EXIT")
                : await _parkingApi.VerifyGuestCameraAsync(RequireExitVehicleEvidence());
            started.Stop();
            ApplyFaceVerification(response, entry, started.Elapsed);
        } catch(Exception ex){ShowFaceError(ex);}
        finally { SetFaceBusy(false, _faceStatus.Text); }
    }

    private async Task SelectFaceMediaAsync(bool video)
    {
        using var dialog = new OpenFileDialog {
            Filter = video ? "Video khuôn mặt|*.mp4;*.avi;*.mov;*.mkv;*.wmv;*.m4v;*.webm" : "Ảnh khuôn mặt|*.jpg;*.jpeg;*.png;*.bmp;*.webp"
        };
        if (dialog.ShowDialog() != DialogResult.OK) return;
        bool entry = _tabs.SelectedIndex == 0;
        try
        {
            ConfigureApis();
            SetFaceBusy(true, video ? "Đang chọn frame khuôn mặt tốt nhất..." : "Đang xác thực ảnh khuôn mặt...");
            var started = System.Diagnostics.Stopwatch.StartNew();
            var member = (entry ? _entryMember : _exitMember).SelectedItem as AuthorizedMemberResponse;
            FaceVerificationResponse response;
            if (member != null) {
                long familyMemberId = ResolveRegisteredMemberId(entry);
                response = video
                    ? await _parkingApi.VerifyFaceVideoAsync(familyMemberId, dialog.FileName, entry ? "ENTRY" : "EXIT")
                    : await _parkingApi.VerifyFaceImageAsync(familyMemberId, dialog.FileName, entry ? "ENTRY" : "EXIT");
            } else {
                string vehicleEvidenceId = RequireExitVehicleEvidence();
                long? linkedSessionId = LinkedExitSessionId(NormalizePlate(_exitPlate.Text));
                response = video
                    ? await _parkingApi.VerifyGuestFaceVideoAsync(vehicleEvidenceId, dialog.FileName, linkedSessionId)
                    : await _parkingApi.VerifyGuestFaceImageAsync(vehicleEvidenceId, dialog.FileName, linkedSessionId);
            }
            started.Stop();
            ApplyFaceVerification(response, entry, started.Elapsed);
        }
        catch (Exception ex) { ShowFaceError(ex); }
        finally { SetFaceBusy(false, _faceStatus.Text); }
    }

    private async Task CaptureOrVerifyFaceAsync(bool entry)
    {
        var combo = entry ? _entryMember : _exitMember;
        if (combo.SelectedItem is AuthorizedMemberResponse || (!entry && _exitGuestFaceAvailable))
            await VerifyFaceAsync(entry);
        else if (entry)
            await CaptureFaceSnapshotAsync(entry);
        else
            ShowFaceError(new InvalidOperationException("Chưa có ảnh khuôn mặt lúc xe vào để đối chiếu."));
    }

    private async Task VerifyLatestCapturedFaceAsync(bool entry)
    {
        if (string.IsNullOrWhiteSpace(_latestFaceImageBase64)) {
            ShowFaceError(new InvalidOperationException("Chưa chụp được khuôn mặt để đối chiếu."));
            return;
        }
        try
        {
            SetFaceBusy(true, "Đang đối chiếu khuôn mặt vừa chụp...");
            var started = System.Diagnostics.Stopwatch.StartNew();
            var member = (entry ? _entryMember : _exitMember).SelectedItem as AuthorizedMemberResponse;
            var response = member != null
                ? await _parkingApi.VerifyCapturedFaceAsync(
                    ResolveRegisteredMemberId(entry), _latestFaceImageBase64, entry ? "ENTRY" : "EXIT")
                : await _parkingApi.VerifyCapturedGuestFaceAsync(
                    RequireExitVehicleEvidence(), _latestFaceImageBase64,
                    LinkedExitSessionId(NormalizePlate(_exitPlate.Text)));
            started.Stop();
            ApplyFaceVerification(response, entry, started.Elapsed);
        }
        catch (Exception ex) { ShowFaceError(ex); }
        finally { SetFaceBusy(false, _faceStatus.Text); }
    }

    private long ResolveRegisteredMemberId(bool entry)
    {
        var combo = entry ? _entryMember : _exitMember;
        if (combo.SelectedItem is AuthorizedMemberResponse member)
        {
            if (!member.FaceImageAvailable) throw new InvalidOperationException("Thành viên chưa có ảnh đăng ký.");
            return member.Id;
        }
        throw new InvalidOperationException(entry
            ? "Hãy chọn thành viên có ảnh đăng ký để xác thực."
            : "Khách vãng lai sẽ được đối chiếu với ảnh khuôn mặt đã chụp lúc vào.");
    }

    private string RequireExitVehicleEvidence()
    {
        if (string.IsNullOrWhiteSpace(_exitVehicleEvidenceId))
            throw new InvalidOperationException("Hãy nhận dạng biển số xe ra trước khi đối chiếu khách vãng lai.");
        if (!_exitGuestFaceAvailable)
            throw new InvalidOperationException("Không tìm thấy ảnh khuôn mặt lúc xe vào.");
        return _exitVehicleEvidenceId;
    }

    private void ApplyFaceVerification(FaceVerificationResponse response, bool entry, TimeSpan elapsed)
    {
        bool pass = response.Decision == "PASS";
        if (response.Decision == "PASS") SetFaceFocusState(FaceFocusState.Verified);
        else if (response.Decision == "REVIEW") SetFaceFocusState(FaceFocusState.Warning);
        else SetFaceFocusState(FaceFocusState.Rejected);
        var combo = entry ? _entryMember : _exitMember;
        if(entry) _entryFaceEvidenceId=response.EvidenceId??""; else _exitFaceEvidenceId=response.EvidenceId??"";
        long? verifiedMemberId = pass
            ? (combo.SelectedItem as AuthorizedMemberResponse)?.Id
            : null;
        if(entry){_entryFaceVerified=pass;_entryFaceSimilarity=response.Similarity;_entryVerifiedMemberId=verifiedMemberId;}else{_exitFaceVerified=pass;_exitFaceSimilarity=response.Similarity;_exitVerifiedMemberId=verifiedMemberId;}
        if (!pass) combo.SelectedIndex = -1;
        if(!string.IsNullOrWhiteSpace(response.RealtimeImageBase64)){using var ms=new MemoryStream(Convert.FromBase64String(response.RealtimeImageBase64));using var img=Image.FromStream(ms);_facePreview.Image?.Dispose();_facePreview.Image=new Bitmap(img);}
        _faceStatus.Text = response.Decision switch
        {
            "PASS" => $"Đã xác nhận đúng người được phép điều khiển xe. | {elapsed.TotalSeconds:0.00}s",
            "REVIEW" => "Ảnh khuôn mặt chưa đủ rõ để xác nhận. "
                + "Vui lòng chụp lại trong điều kiện đủ sáng.",
            "REJECT" => "Khuôn mặt không khớp với chủ xe đã đăng ký. "
                + "Vui lòng kiểm tra người điều khiển, chọn lại cư dân phù hợp rồi đối chiếu lại.",
            _ => $"Chưa thể xác nhận khuôn mặt. Vui lòng thử lại. | {elapsed.TotalSeconds:0.00}s"
        };
        _faceStatus.ForeColor=pass?Color.SeaGreen:Color.Firebrick;
        _result.Text=$"Đối chiếu khuôn mặt: {DisplayFaceDecision(response.Decision)}\r\n{response.Message}";
        _result.BackColor=pass?Color.Honeydew:Color.MistyRose;
    }

    private ParkingRequest? Request(string plate) => BuildGateRequest(plate, includePayment: true);

    private ParkingRequest? BuildGateRequest(string plate, bool includePayment)
    {
        bool entry=_tabs.SelectedIndex==0; var selected=(entry?_entryMember:_exitMember).SelectedItem as AuthorizedMemberResponse;
        bool verified=entry?_entryFaceVerified:_exitFaceVerified;long? verifiedId=entry?_entryVerifiedMemberId:_exitVerifiedMemberId;
        if(selected != null && selected.Id != verifiedId) verified=false;
        if (entry && _entryResidentRegistered && !verified && !IsManual(true))
            throw new InvalidOperationException(
                "Chưa xác nhận đúng người điều khiển xe. Vui lòng chọn người được phép và đối chiếu khuôn mặt lại.");
        EnsureManualOverrideReason(entry);
        bool noPlate = entry && _entryNoPlate.Checked;
        if (noPlate)
        {
            if (string.IsNullOrWhiteSpace(_entryVehicleEvidenceId))
                throw new InvalidOperationException("Xe không biển số bắt buộc phải có ảnh phương tiện lúc vào.");
            if (string.IsNullOrWhiteSpace(_entryFaceEvidenceId)
                    && string.IsNullOrWhiteSpace(_entryGuestFaceBase64))
                throw new InvalidOperationException("Xe không biển số bắt buộc phải chụp ảnh khuôn mặt khi vào.");
            plate = "";
        }
        string identifier = NormalizePlate(plate);
        string confirmedType = entry ? _entryDetectedType : _exitDetectedType;
        if (entry && (string.IsNullOrWhiteSpace(confirmedType) || confirmedType == "UNKNOWN"))
            throw new InvalidOperationException("Vui lòng xác nhận loại xe trước khi tiếp tục.");
        bool exit = !entry;
        string? paymentMethod = null;
        string? paymentReference = null;
        if (exit && includePayment)
        {
            paymentMethod = ResolveSelectedPaymentMethod();
            paymentReference = string.IsNullOrWhiteSpace(_txtExitPaymentRef.Text) ? null : _txtExitPaymentRef.Text.Trim();
        }
        return new ParkingRequest(plate,identifier,confirmedType,IsManual(entry),selected?.Id,verified,
            entry?_entryFaceSimilarity:_exitFaceSimilarity,entry?_entryGuestFaceBase64:"",
            entry?_entryVehicleEvidenceId:_exitVehicleEvidenceId,
            entry?_entryFaceEvidenceId:_exitFaceEvidenceId,
            entry?_entryOverrideReasonCode:_exitOverrideReasonCode,
            entry?_entryOverrideReasonText:_exitOverrideReasonText,
            paymentMethod,paymentReference, NoPlate: noPlate,
            SessionId: !entry && _exitLinkedSessionId != null
                && string.Equals(_exitLinkedPlate, identifier, StringComparison.OrdinalIgnoreCase)
                ? _exitLinkedSessionId : null);
    }

    private string ResolveSelectedPaymentMethod()
    {
        string text = _cboExitPaymentMethod.SelectedItem?.ToString() ?? "CASH";
        if (text.Contains("Chuyển khoản") || text.Contains("BANK_TRANSFER")) return "BANK_TRANSFER";
        if (text.Contains("POS") || text.Contains("MOMO")) return "MOMO";
        return "CASH";
    }

    private void EnsureManualOverrideReason(bool entry)
    {
        if (!IsManual(entry)) return;
        string code = entry ? _entryOverrideReasonCode : _exitOverrideReasonCode;
        if (string.IsNullOrWhiteSpace(code)) throw new InvalidOperationException("Vui lòng chọn lý do chỉnh sửa kết quả nhận dạng.");
    }

    private bool IsManual(bool entry) => !string.IsNullOrWhiteSpace(
        entry ? _entryOverrideReasonCode : _exitOverrideReasonCode);

    private void EditAiResult()
    {
        bool entry = _tabs.SelectedIndex == 0;
        string evidenceId = entry ? _entryVehicleEvidenceId : _exitVehicleEvidenceId;
        if (string.IsNullOrWhiteSpace(evidenceId)) {
            MessageBox.Show(this, "Hãy chụp hoặc tải ảnh xe để nhận diện trước khi chỉnh sửa.",
                "Chưa có kết quả nhận dạng", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        string plate = entry ? _entryPlate.Text : _exitPlate.Text;
        string vehicleType = entry ? _entryDetectedType : _exitDetectedType;
        string family = entry ? _entryVehicleFamily : VehicleFamily(vehicleType);
        using var dialog = new ManualOverrideDialog(plate, vehicleType, VehicleTypesForFamily(family));
        if (dialog.ShowDialog(this) != DialogResult.OK) return;
        if (entry) {
            _entryPlate.Text = dialog.CorrectedPlate;
            SetEntryVehicleType(dialog.CorrectedVehicleType);
            _entryOverrideReasonCode = dialog.ReasonCode;
            _entryOverrideReasonText = dialog.ReasonText;
        } else {
            _exitPlate.Text = dialog.CorrectedPlate;
            _exitDetectedType = dialog.CorrectedVehicleType;
            _exitOverrideReasonCode = dialog.ReasonCode;
            _exitOverrideReasonText = dialog.ReasonText;
        }
        _editAiHint.Text = "Đã chỉnh sửa kết quả nhận dạng; thao tác sẽ được lưu trong nhật ký.";
        _editAiHint.ForeColor = Color.DarkOrange;
    }

    private async Task OpenEvidenceComparisonAsync()
    {
        try
        {
            ConfigureApis();
            bool entry = _tabs.SelectedIndex == 0;
            string plate = (entry ? _entryPlate.Text : _exitPlate.Text).Trim();
            long? sessionId = !entry && _lastPreview is { SessionId: > 0 } ? _lastPreview.SessionId : null;
            if (!entry && sessionId is null)
                sessionId = LinkedExitSessionId(NormalizePlate(plate));
            if (string.IsNullOrWhiteSpace(plate) && sessionId is null)
            {
                MessageBox.Show(this,
                    "Hãy nhập hoặc nhận diện biển số xe cần đối chiếu ảnh vào/ra.",
                    "Thiếu thông tin lượt gửi",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
                return;
            }
            string currentType = entry ? _entryDetectedType : _exitDetectedType;
            var comparison = await CompareEvidenceAsync(entry, plate, currentType, sessionId);
            bool hasActiveShift = _activeShiftId.HasValue || _parkingApi.CurrentOperator?.ActiveShift is not null;
            string family = entry ? _entryVehicleFamily : VehicleFamily(currentType);
            using var dlg = new EvidenceComparisonDialog(comparison, hasActiveShift, plate, currentType, VehicleTypesForFamily(family));
            if (dlg.ShowDialog(this) == DialogResult.OK && dlg.OverrideApplied)
            {
                if (entry)
                {
                    _entryPlate.Text = dlg.CorrectedPlate;
                    SetEntryVehicleType(dlg.CorrectedVehicleType);
                    _entryOverrideReasonCode = dlg.OverrideReasonCode;
                    _entryOverrideReasonText = dlg.OverrideReasonText;
                }
                else
                {
                    _exitPlate.Text = dlg.CorrectedPlate;
                    _exitDetectedType = dlg.CorrectedVehicleType;
                    _exitOverrideReasonCode = dlg.OverrideReasonCode;
                    _exitOverrideReasonText = dlg.OverrideReasonText;
                }
                UpdateManualEditState();
            }
            if (!entry && dlg.Confirmed && _exitMember.SelectedItem is null)
            {
                // Khách vãng lai: nhân viên đã xem đủ ảnh vào/ra và xác nhận đúng xe.
                // Dùng xác nhận này cho bước xem trước phí, không bắt xác thực lại.
                _exitFaceVerified = true;
                _exitFaceSimilarity ??= comparison.ExitFaceSimilarity;
                _exitFaceEvidenceId = string.IsNullOrWhiteSpace(comparison.ExitFaceEvidenceId)
                    ? _exitFaceEvidenceId
                    : comparison.ExitFaceEvidenceId;
                _faceStatus.Text = "Đã xác nhận ảnh vào/ra khớp với xe đang đỗ.";
                _faceStatus.ForeColor = Color.SeaGreen;
            }
            if (dlg.Confirmed)
            {
                string verifiedPlate = string.IsNullOrWhiteSpace(plate) ? "này" : plate;
                MessageBox.Show(this,
                    (entry
                        ? $"Xe vào {verifiedPlate} đã được xác thực.\r\nẢnh xe, biển số và khuôn mặt đã được ghi nhận đúng."
                        : $"Xe ra {verifiedPlate} đã được xác thực.\r\nBạn có thể bấm XEM TRƯỚC PHÍ để tính tiền gửi xe."),
                    "Đã xác thực xe",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
            }
        }
        catch (Exception ex)
        {
            ShowError(ex);
        }
    }

    private async Task<EvidenceComparisonResponse> CompareEvidenceAsync(
        bool entry, string plate, string currentType, long? sessionId)
    {
        try
        {
            return await _parkingApi.GetEvidenceComparisonAsync(
                plate, currentType, _exitVehicleEvidenceId, _exitFaceEvidenceId, sessionId);
        }
        catch (Exception ex) when (!entry && IsMissingOpenSession(ex))
        {
            // Xe vào bằng mã tạm nên không tra được theo biển thật: cho nhân viên
            // chọn đúng lượt đang đỗ rồi đối chiếu lại với biển vừa đọc.
            List<ExitCandidateResponse> candidates;
            try { candidates = await _parkingApi.ExitCandidatesAsync(); }
            catch (Exception loadEx) { ShowError(loadEx); throw; }
            if (candidates.Count == 0) throw;
            using var dialog = new ExitSessionPickerDialog(plate, candidates);
            if (dialog.ShowDialog(this) != DialogResult.OK || dialog.Selected is null) throw;
            _exitLinkedSessionId = dialog.Selected.SessionId.ToString();
            _exitLinkedPlate = NormalizePlate(plate);
            _lastPreview = null;
            return await _parkingApi.GetEvidenceComparisonAsync(
                plate, currentType, _exitVehicleEvidenceId, _exitFaceEvidenceId, dialog.Selected.SessionId);
        }
    }

    private void UpdateManualEditState()
    {
        bool entry = _tabs.SelectedIndex == 0;
        _editAiResult.Enabled = !string.IsNullOrWhiteSpace(entry ? _entryVehicleEvidenceId : _exitVehicleEvidenceId);
        bool edited = IsManual(entry);
        _editAiResult.Text = edited ? "CHỈNH SỬA LẠI KẾT QUẢ" : "CHỈNH SỬA KẾT QUẢ NHẬN DẠNG";
        _editAiHint.Text = edited ? "Đã chỉnh sửa kết quả nhận dạng; thao tác sẽ được lưu trong nhật ký."
            : "Chỉ sử dụng sau khi đã có ảnh nhận diện.";
        _editAiHint.ForeColor = edited ? Color.DarkOrange : Color.DimGray;
    }

    private async Task CaptureFaceSnapshotAsync(bool entry){try{SetFaceBusy(true,"Đang chụp khuôn mặt...");ConfigureApis();var r=await _parkingApi.CaptureCameraAsync(entry?"ENTRY":"EXIT");if(entry)_entryFaceEvidenceId=r.EvidenceId??"";else _exitFaceEvidenceId=r.EvidenceId??"";_latestFaceImageBase64="data:image/jpeg;base64,"+r.RealtimeImageBase64;if(entry)_entryGuestFaceBase64=_latestFaceImageBase64;ShowCapturedFace(r);_faceStatus.Text="Đã chụp khuôn mặt; đang chờ đối chiếu";_faceStatus.ForeColor=Color.SeaGreen;}catch(Exception ex){ShowFaceError(ex);}finally{SetFaceBusy(false,_faceStatus.Text);}}
    private void ShowCapturedFace(FaceVerificationResponse r){if(string.IsNullOrWhiteSpace(r.RealtimeImageBase64))return;using var ms=new MemoryStream(Convert.FromBase64String(r.RealtimeImageBase64));using var img=Image.FromStream(ms);_facePreview.Image?.Dispose();_facePreview.Image=new Bitmap(img);}
    private async Task RefreshMapAsync()
    {
        try
        {
            if (!_mapPanel.Controls.OfType<SlotControl>().Any())
            {
                _mapPanel.Controls.Clear();
                _mapPanel.Controls.Add(new Label
                {
                    Text = "Đang tải sơ đồ tổng quan...",
                    AutoSize = true,
                    ForeColor = LumiTheme.Muted,
                    Font = LumiTheme.AppFont(9.5f, FontStyle.Bold),
                    Margin = new Padding(12)
                });
                await Task.Yield();
            }
            var items = await _parkingApi.GetSlotsAsync();
            int availableCount = items.Count(item => item.Status == "AVAILABLE");
            int occupiedCount = items.Count(item => !string.IsNullOrWhiteSpace(item.OccupiedPlate)
                || item.Status.StartsWith("OCCUPIED", StringComparison.OrdinalIgnoreCase));
            int attentionCount = items.Count(NeedsAttention);
            _mapSummary.Text = $"Còn trống: {availableCount}/{items.Count}   •   Đang sử dụng: {occupiedCount}   •   Cần chú ý: {attentionCount}";

            var visibleItems = items
                .Where(item => item.Status != "AVAILABLE")
                .OrderByDescending(NeedsAttention)
                .ThenByDescending(item => item.OccupiedEntryTime ?? DateTime.MinValue)
                .ThenBy(item => item.SlotCode, StringComparer.OrdinalIgnoreCase)
                .Take(30)
                .ToList();
            foreach (var loading in _mapPanel.Controls.Cast<Control>().Where(c => c is not SlotControl).ToList())
            {
                _mapPanel.Controls.Remove(loading);
                loading.Dispose();
            }
            var existing = _mapPanel.Controls.OfType<SlotControl>()
                .Where(c => c.Slot != null)
                .GroupBy(c => c.Slot!.SlotCode, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(g => g.Key, g => g.First(), StringComparer.OrdinalIgnoreCase);

            var incoming = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var scroll = _mapPanel.AutoScrollPosition;

            _mapPanel.SuspendLayout();
            try
            {
                var newControls = new List<Control>();
                foreach (var item in visibleItems)
                {
                    incoming.Add(item.SlotCode);
                    if (existing.TryGetValue(item.SlotCode, out var control))
                    {
                        control.Update(item);
                        continue;
                    }

                    var slot = new SlotControl();
                    slot.Update(item);
                    slot.SlotClicked += (_, s) =>
                    {
                        using var dlg = new SlotDetailDialog(_parkingApi, s);
                        if (dlg.ShowDialog(this) == DialogResult.OK)
                        {
                            NotifyMapUpdated();
                        }
                    };
                    newControls.Add(slot);
                }
                if (newControls.Count > 0) _mapPanel.Controls.AddRange(newControls.ToArray());

                foreach (var stale in existing.Where(kv => !incoming.Contains(kv.Key)).Select(kv => kv.Value).ToList())
                {
                    _mapPanel.Controls.Remove(stale);
                    stale.Dispose();
                }

                if (visibleItems.Count == 0)
                {
                    _mapPanel.Controls.Add(new Label
                    {
                        Text = "Hiện không có vị trí nào cần theo dõi.",
                        AutoSize = true,
                        ForeColor = LumiTheme.Good,
                        Font = LumiTheme.AppFont(9.5f, FontStyle.Bold),
                        Margin = new Padding(12)
                    });
                }

                ResizeSlots(_mapPanel);
            }
            finally
            {
                _mapPanel.ResumeLayout(true);
                _mapPanel.PerformLayout();
                _mapPanel.AutoScrollPosition = new Point(-scroll.X, -scroll.Y);
            }
        }
        catch { }
    }

    private static bool NeedsAttention(ParkingSlotResponse slot) =>
        slot.Overdue || slot.Status is "OVERDUE_ALERT" or "OCCUPIED_MISMATCH" or "BLOCKED"
            or "OCCUPIED_BORROWED" or "RESERVED_BORROWED";

    private void ResizeSlots(FlowLayoutPanel panel)
    {
        if (panel.Controls.Count == 0) return;
        int padding = 12; 
        int minWidth = 140;
        int availableWidth = panel.ClientSize.Width - panel.Padding.Left - panel.Padding.Right;
        if (availableWidth <= 0) return;

        int cols = Math.Max(1, availableWidth / (minWidth + padding));
        int targetWidth = (availableWidth / cols) - padding - 4; 

        panel.SuspendLayout();
        foreach (Control c in panel.Controls)
        {
            if (c is SlotControl sc)
            {
                sc.Width = targetWidth;
            }
        }
        panel.ResumeLayout();
    }
    private async Task SetBarrierAsync(bool open, string reason)
    {
        try
        {
            if (open) await _barrier.OpenAsync(reason);
            else await _barrier.CloseAsync(reason);
        }
        catch (Exception ex)
        {
            _barrierState.Text = "BARRIER: LỖI THIẾT BỊ";
            _barrierState.ForeColor = Color.Firebrick;
            MessageBox.Show(this, ex.Message, "Không thể điều khiển barrier", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private async void AutoOpenBarrier(string reason)
    {
        await SetBarrierAsync(true, reason);
        await Task.Delay(TimeSpan.FromSeconds(_stationSettings.BarrierAutoCloseSeconds));
        if (_barrier.Status.Position == BarrierPosition.Open)
            await SetBarrierAsync(false, "Tự đóng theo cấu hình");
    }

    private void BarrierStatusChanged(BarrierStatus status)
    {
        if (InvokeRequired) { BeginInvoke(() => BarrierStatusChanged(status)); return; }
        _barrierState.Text = status.Position switch {
            BarrierPosition.Open => "BARRIER: ĐANG MỞ",
            BarrierPosition.Closed => "BARRIER: ĐANG ĐÓNG",
            _ => "BARRIER: LỖI THIẾT BỊ"
        };
        _barrierState.ForeColor = status.Position == BarrierPosition.Open ? Color.SeaGreen : Color.Firebrick;
        _barrierState.AccessibleDescription = status.Message;
    }

    private void ShowStationSettings()
    {
        using var dialog = new StationSettingsDialog(_stationSettings.Copy());
        if (dialog.ShowDialog(this) != DialogResult.OK || dialog.SavedSettings is null) return;
        _settingsStore.Save(dialog.SavedSettings);
        _stationSettings = dialog.SavedSettings;
        _parkingApi.StationSettings = _stationSettings;
        MessageBox.Show(this, _stationSettings.UsesNetworkCamera
            ? "Đã lưu cấu hình. Nguồn camera mạng sẽ được hỗ trợ ở bước tiếp theo."
            : "Đã lưu và áp dụng cấu hình trạm.",
            "Cấu hình trạm", MessageBoxButtons.OK, MessageBoxIcon.Information);
    }
    private void ConfigureApis() { _parkingApi.BaseUrl = _parkingUrl.Text.Trim(); }

    private bool _closingAfterLogout;
    private bool _explicitLogout;

    private async void ValidateSessionHeartbeat(object? sender, EventArgs e)
    {
        if (_checkingSession || _closingAfterLogout || !_parkingApi.IsAuthenticated) return;
        _checkingSession = true;
        try
        {
            await _parkingApi.ValidateSessionAsync();
        }
        catch (DesktopAuthenticationException)
        {
            _sessionHeartbeat.Stop();
            _closingAfterLogout = true;
            _explicitLogout = true;
            ShowLogoutToast("Phiên làm việc đã được kết thúc.\r\nVui lòng đăng nhập lại.", warning: true);
            LogoutCompleted?.Invoke();
            Close();
        }
        catch (HttpRequestException)
        {
            // Mạng có thể gián đoạn tạm thời; giữ phiên và thử lại ở nhịp tiếp theo.
        }
        catch (TaskCanceledException)
        {
            // Máy chủ phản hồi chậm; giữ phiên và thử lại ở nhịp tiếp theo.
        }
        catch (Exception)
        {
            _connection.Text = "Tạm thời chưa thể kiểm tra phiên làm việc";
            _connection.ForeColor = Color.DarkOrange;
        }
        finally { _checkingSession = false; }
    }

    private async void LogoutClicked(object? sender, EventArgs e)
    {
        if (_closingAfterLogout) return;
        _closingAfterLogout = true;
        _explicitLogout = true;
        _sessionHeartbeat.Stop();
        _outboxTimer.Stop();
        _clockTimer.Stop();
        foreach (var owned in OwnedForms.ToArray())
        {
            try { owned.Close(); } catch { }
        }
        await ShowShiftSummaryAsync(previous: false);
        Enabled = false;
        try { await _parkingApi.LogoutAsync(); }
        catch { /* Token is always cleared locally; the server session will expire if unreachable. */ }
        finally
        {
            ShowLogoutToast("Đã đăng xuất khỏi hệ thống.\r\nHẹn gặp lại.");
            LogoutCompleted?.Invoke();
            Close();
        }
    }

    private void ShowLogoutToast(string message, bool warning = false)
    {
        try { new ToastForm(message, warning).Show(); }
        catch { /* Thông báo chỉ để dễ chịu; không được chặn thao tác đăng xuất. */ }
    }

    private async Task ShowShiftSummaryAsync(bool previous)
    {
        try
        {
            ShiftSummary? summary = await _parkingApi.GetShiftSummaryAsync(previous);
            if (summary is null || !_parkingApi.MarkShiftSummaryShown(summary.ShiftId)) return;
            int outboxCount = await _parkingApi.GetOfflineQueueCountAsync();
            using var dialog = new ShiftSummaryDialog(summary, outboxCount, previous);
            dialog.ShowDialog(this);
        }
        catch (DesktopAuthenticationException) { }
        catch (Exception ex)
        {
            if (!previous)
                MessageBox.Show(this,
                    "Không tải được báo cáo ca, nhưng bạn vẫn có thể đăng xuất.\r\n\r\n" + ex.Message,
                    "Chưa tải được tổng kết ca", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private async void LogoutBeforeClose(object? sender, FormClosingEventArgs e)
    {
        if (_explicitLogout || _closingAfterLogout || !_parkingApi.IsAuthenticated) return;
        e.Cancel = true;
        _closingAfterLogout = true;
        Enabled = false;
        try { await _parkingApi.LogoutAsync(); }
        catch { /* Local close proceeds; server session can expire normally. */ }
        finally { Close(); }
    }

    private void ShowParkingResult(ParkingResponse r)
    {
        _result.BackColor = r.Warning ? Color.FromArgb(255, 247, 230) : Color.FromArgb(234, 245, 239);
        _result.ForeColor = r.Warning ? Color.DarkGoldenrod : Color.DarkGreen;
        _result.Text = $"{r.Message}\r\n\r\nMã lượt: {r.SessionId}\r\nBiển số: {r.PlateNumber}\r\nLoại xe: {DisplayVehicleType(r.VehicleType)}\r\nChủ xe: {r.OwnerName}\r\nThẻ: {r.CardCode}\r\nTrạng thái: {r.Status}\r\nGiờ vào: {r.EntryTime:dd/MM/yyyy HH:mm:ss}\r\nPhí: {r.Fee:N0} đ{(string.IsNullOrWhiteSpace(r.ReceiptNumber) ? "" : $"\r\nBiên nhận: {r.ReceiptNumber}")}";
    }

    private void ClearEntryForm()
    {
        _savedEntryPlate = "";
        _savedEntryVehicleType = "UNKNOWN";
        _entryNoPlate.Checked = false;
        _entryPlate.ReadOnly = false;
        _entryPlate.Text = "";
        SetEntryVehicleType("UNKNOWN", requireSelection: true);
        _vehiclePreview.Image?.Dispose();
        _vehiclePreview.Image = null;
        _facePreview.Image?.Dispose();
        _facePreview.Image = null;
        _result.Text = "";
        _result.BackColor = SystemColors.Control;
        _aiStatus.Text = "Chọn ảnh hoặc video để đọc biển số";
        _aiStatus.ForeColor = Color.DimGray;
        _faceStatus.Text = "Chọn ảnh, video hoặc camera khuôn mặt";
        _faceStatus.ForeColor = Color.DimGray;
        _residentInfo.Text = "Chưa tra cứu cư dân";
        _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
        _entryMember.DataSource = null;
        _entryFaceVerified = false;
        _entryResidentRegistered = false;
        _entryFaceSimilarity = null;
        _entryVerifiedMemberId = null;
        _entryGuestFaceBase64 = "";
        _latestFaceImageBase64 = "";
        _entryVehicleEvidenceId = "";
        _entryFaceEvidenceId = "";
        _entryOverrideReasonCode = "";
        _entryOverrideReasonText = "";
        UpdateManualEditState();
    }

    private void ClearExitForm()
    {
        _exitPlate.Text = "";
        _exitStoredVehicleType.Text = "Chưa tra cứu lượt gửi";
        _exitLinkedSessionId = null;
        _exitLinkedPlate = "";
        _exitDetectedType = "UNKNOWN";
        _vehiclePreview.Image?.Dispose();
        _vehiclePreview.Image = null;
        _facePreview.Image?.Dispose();
        _facePreview.Image = null;
        _result.Text = "";
        _result.BackColor = SystemColors.Control;
        _aiStatus.Text = "Chọn ảnh hoặc video để đọc biển số";
        _aiStatus.ForeColor = Color.DimGray;
        _faceStatus.Text = "Chọn ảnh, video hoặc camera khuôn mặt";
        _faceStatus.ForeColor = Color.DimGray;
        _residentInfo.Text = "Chưa tra cứu cư dân";
        _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
        _exitMember.DataSource = null;
        _exitFaceVerified = false;
        _exitFaceSimilarity = null;
        _exitVerifiedMemberId = null;
        _exitGuestFaceAvailable = false;
        _exitVehicleEvidenceId = "";
        _exitFaceEvidenceId = "";
        _exitOverrideReasonCode = "";
        _exitOverrideReasonText = "";
        UpdateManualEditState();
    }

    private string CurrentOperation() => _tabs.SelectedIndex == 0 ? "ENTRY" : "EXIT";

    private void ShowError(Exception ex)
    {
        string errorId = DateTimeOffset.Now.ToString("yyyyMMdd-HHmmss-fff");
        WriteDiagnosticError(errorId, ex);
        string message = ex switch
        {
            DesktopAuthenticationException => "Phiên đăng nhập hết hạn hoặc bị thu hồi. Hãy đăng nhập lại.",
            DesktopAuthorizationException => "Tài khoản đã đăng nhập nhưng không được cấp quyền cho thao tác này.",
            HttpRequestException => "Không kết nối được đến hệ thống.\r\n\r\nVui lòng kiểm tra kết nối mạng và thử lại.",
            TaskCanceledException => "Hệ thống phản hồi chậm hơn bình thường.\r\n\r\nVui lòng kiểm tra camera (nếu đang dùng) và thử lại.",
            System.Text.Json.JsonException => "Dữ liệu nhận được không hợp lệ.\r\n\r\nVui lòng thử lại. Nếu vẫn lỗi, liên hệ quản trị viên.",
            _ => ex.Message
        };
        if (ex is NullReferenceException)
            message = "Ứng dụng gặp lỗi khi xử lý thao tác. "
                + "Vui lòng thử lại. Nếu vẫn lỗi, liên hệ quản trị viên.";
        _result.BackColor = Color.MistyRose;
        _result.ForeColor = Color.Firebrick;
        _result.Text = "KHÔNG THỂ THỰC HIỆN\r\n\r\n" + message;
    }

    private static void WriteDiagnosticError(string errorId, Exception ex)
    {
        try
        {
            string directory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "ParkingGateDesktop");
            Directory.CreateDirectory(directory);
            string path = Path.Combine(directory, "desktop-error.log");
            string entry = $"[{DateTimeOffset.Now:O}] errorId={errorId}\r\n"
                + $"type={ex.GetType().FullName}\r\nmessage={ex.Message}\r\n"
                + $"stack={ex.StackTrace}\r\n"
                + (ex.InnerException is null ? "" :
                    $"innerType={ex.InnerException.GetType().FullName}\r\n"
                    + $"innerMessage={ex.InnerException.Message}\r\n"
                    + $"innerStack={ex.InnerException.StackTrace}\r\n")
                + "---\r\n";
            File.AppendAllText(path, entry);
        }
        catch
        {
            // Diagnostic logging must never hide the original application error.
        }
    }

    private void ShowLocalImage(string path)
    {
        _vehiclePreview.Image?.Dispose();
        using var original = Image.FromFile(path);
        _vehiclePreview.Image = new Bitmap(original);
    }

    private void ShowFaceError(Exception ex)
    {
        bool entry = _tabs.SelectedIndex == 0;
        ComboBox combo = entry ? _entryMember : _exitMember;
        if (entry)
        {
            _entryFaceVerified = false;
            _entryFaceSimilarity = null;
            _entryVerifiedMemberId = null;
        }
        else
        {
            _exitFaceVerified = false;
            _exitFaceSimilarity = null;
            _exitVerifiedMemberId = null;
        }
        combo.SelectedIndex = -1;
        SetFaceFocusState(FaceFocusState.Rejected);
        _faceStatus.Text = "Khuôn mặt: " + ex.Message;
        _faceStatus.ForeColor = Color.Firebrick;
    }

    private void SetVehicleBusy(bool busy, string text) { _vehicleBusy = busy; _aiStatus.Text = text; UseWaitCursor = _vehicleBusy || _faceBusy; }
    private void SetFaceBusy(bool busy, string text) { _faceBusy = busy; _faceStatus.Text = text; UseWaitCursor = _vehicleBusy || _faceBusy; }
    private Control BuildCapturePreviews()
    {
        var split = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 1 };
        split.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
        split.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
        var vehicleLayout = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 4, ColumnCount = 1 };
        vehicleLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        vehicleLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        vehicleLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        vehicleLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        vehicleLayout.Controls.Add(_vehiclePreview, 0, 0);
        var vehicleButtons = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoSize = true };
        vehicleButtons.Controls.Add(CreateButton("Tải ảnh", async (_, _) => await SelectAndRecognizeAsync(false)));
        vehicleButtons.Controls.Add(CreateButton("Tải video", async (_, _) => await SelectAndRecognizeAsync(true)));
        vehicleButtons.Controls.Add(CreateButton("Camera xe", async (_, _) => await RecognizeVehicleCameraAsync()));
        vehicleLayout.Controls.Add(vehicleButtons, 0, 1);
        vehicleLayout.Controls.Add(_aiStatus, 0, 2);
        vehicleLayout.Controls.Add(_aiWarningBanner, 0, 3);
        var vehicle = new GroupBox { Text = "XE / BIỂN SỐ", Dock = DockStyle.Fill, Padding = new Padding(8) };
        vehicle.Controls.Add(vehicleLayout);
        var faceLayout = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1 };
        faceLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        faceLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        faceLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        faceLayout.Controls.Add(_faceBorderPanel, 0, 0);
        var faceButtons = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoSize = true };
        faceButtons.Controls.Add(CreateButton("Tải ảnh", async (_, _) => await SelectFaceMediaAsync(false)));
        faceButtons.Controls.Add(CreateButton("Tải video", async (_, _) => await SelectFaceMediaAsync(true)));
        faceButtons.Controls.Add(CreateButton("Camera mặt", async (_, _) => await CaptureOrVerifyFaceAsync(_tabs.SelectedIndex == 0)));
        faceLayout.Controls.Add(faceButtons, 0, 1);
        faceLayout.Controls.Add(_faceStatus, 0, 2);
        var face = new GroupBox { Text = "NGƯỜI / KHUÔN MẶT", Dock = DockStyle.Fill, Padding = new Padding(8) };
        face.Controls.Add(faceLayout);
        split.Controls.Add(vehicle, 0, 0);
        split.Controls.Add(face, 1, 0);
        bool stacked = false;
        split.Resize += (_, _) =>
        {
            bool shouldStack = split.ClientSize.Width < 720;
            if (shouldStack == stacked) return;
            stacked = shouldStack;
            split.SuspendLayout();
            split.ColumnStyles.Clear();
            split.RowStyles.Clear();
            if (stacked)
            {
                split.ColumnCount = 1;
                split.RowCount = 2;
                split.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
                split.RowStyles.Add(new RowStyle(SizeType.Percent, 50));
                split.RowStyles.Add(new RowStyle(SizeType.Percent, 50));
                split.SetCellPosition(vehicle, new TableLayoutPanelCellPosition(0, 0));
                split.SetCellPosition(face, new TableLayoutPanelCellPosition(0, 1));
            }
            else
            {
                split.ColumnCount = 2;
                split.RowCount = 1;
                split.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
                split.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
                split.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
                split.SetCellPosition(vehicle, new TableLayoutPanelCellPosition(0, 0));
                split.SetCellPosition(face, new TableLayoutPanelCellPosition(1, 0));
            }
            split.ResumeLayout(true);
        };
        return split;
    }
    private static PictureBox PreviewBox() => new() { Dock = DockStyle.Fill, SizeMode = PictureBoxSizeMode.Zoom, BackColor = Color.FromArgb(235, 240, 246) };
    internal static string DisplayVehicleType(string? type) => (type ?? "").Trim() switch
    {
        "BICYCLE_ELECTRIC_BICYCLE" => "Xe đạp / xe đạp điện",
        "MOTORBIKE" => "Xe máy / xe máy điện",
        "LARGE_MOTORBIKE" => "Xe mô tô phân khối lớn",
        "CAR" => "Ô tô 4-5 chỗ",
        "CAR_6_7" => "Ô tô 6-7 chỗ",
        "CAR_8_9" => "Ô tô 8-9 chỗ",
        "" or "UNKNOWN" => "Chưa xác định",
        _ => type!
    };

    private async Task LoadVehicleTypeCatalogAsync()
    {
        var types = await _parkingApi.GetVehicleTypesAsync();
        _vehicleTypeCatalog = types
            .Where(type => type.Active)
            .Select(type => type with
            {
                DisplayName = type.Code is "CAR" or "CAR_6_7" or "CAR_8_9"
                    ? DisplayVehicleType(type.Code)
                    : type.DisplayName
            })
            .ToList();
        SetEntryVehicleType(_entryDetectedType, _entryDetectedType == "UNKNOWN");
    }

    private void SetEntryVehicleFamilyFromRecognition(string? detectedCode)
    {
        _entryVehicleFamily = VehicleFamily(detectedCode);
        _entryDetectedType = "UNKNOWN";
        PopulateEntryVehicleTypes(_entryVehicleFamily, null);
    }

    private void SetEntryVehicleType(string? code, bool requireSelection = false)
    {
        string normalized = string.IsNullOrWhiteSpace(code) ? "UNKNOWN" : code.Trim();
        var catalogType = _vehicleTypeCatalog.FirstOrDefault(type =>
            string.Equals(type.Code, normalized, StringComparison.OrdinalIgnoreCase));
        _entryVehicleFamily = catalogType is null ? VehicleFamily(normalized) : VehicleFamily(catalogType);
        _entryDetectedType = requireSelection ? "UNKNOWN" : normalized;
        PopulateEntryVehicleTypes(_entryVehicleFamily, requireSelection ? null : normalized);
    }

    private void PopulateEntryVehicleTypes(string family, string? selectedCode)
    {
        _syncingVehicleType = true;
        try
        {
            _vehicleType.Items.Clear();
            var options = _vehicleTypeCatalog
                .Where(type => string.IsNullOrWhiteSpace(family)
                    || string.Equals(VehicleFamily(type), family, StringComparison.OrdinalIgnoreCase))
                .OrderBy(type => type.DisplayName)
                .ToList();
            foreach (var option in options) _vehicleType.Items.Add(option);
            int selectedIndex = options.FindIndex(type =>
                string.Equals(type.Code, selectedCode, StringComparison.OrdinalIgnoreCase));
            _vehicleType.SelectedIndex = selectedIndex;
        }
        finally { _syncingVehicleType = false; }
    }

    private static string VehicleFamily(VehicleTypeDto type) =>
        type.Category.Contains("CAR", StringComparison.OrdinalIgnoreCase)
            || type.Code.StartsWith("CAR", StringComparison.OrdinalIgnoreCase)
                ? "CAR" : "TWO_WHEEL";

    private static string VehicleFamily(string? code) =>
        (code ?? "").StartsWith("CAR", StringComparison.OrdinalIgnoreCase) ? "CAR"
            : string.IsNullOrWhiteSpace(code) || string.Equals(code, "UNKNOWN", StringComparison.OrdinalIgnoreCase) ? ""
            : "TWO_WHEEL";

    private static string DisplayVehicleFamily(string? code) =>
        VehicleFamily(code) == "CAR" ? "nhóm ô tô" : "nhóm xe hai bánh";

    private IReadOnlyList<VehicleTypeDto> VehicleTypesForFamily(string family) =>
        _vehicleTypeCatalog
            .Where(type => string.IsNullOrWhiteSpace(family)
                || string.Equals(VehicleFamily(type), family, StringComparison.OrdinalIgnoreCase))
            .ToList();
    private static string DisplayFaceDecision(string decision) => decision switch
    {
        "PASS" => "ĐẠT - được phép tiếp tục",
        "REVIEW" => "CẦN KIỂM TRA LẠI",
        "REJECT" => "KHÔNG KHỚP",
        _ => decision
    };
    private static TextBox PlateBox() => new() { Width = 320, CharacterCasing = CharacterCasing.Upper };
    private static string NormalizePlate(string value) => new(value.ToUpperInvariant().Where(char.IsLetterOrDigit).ToArray());
    private static string Blank(string? value) => string.IsNullOrWhiteSpace(value) ? "-" : value;
    private static FlowLayoutPanel FormFlow()
    {
        var flow = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(14, 12, 14, 10),
            BackColor = LumiTheme.Surface
        };
        void FitControls()
        {
            int scrollbar = flow.VerticalScroll.Visible ? SystemInformation.VerticalScrollBarWidth : 0;
            int width = Math.Max(260, flow.ClientSize.Width - flow.Padding.Horizontal - scrollbar - 8);
            foreach (Control control in flow.Controls)
            {
                if (control is Button || control.AutoSize && control is CheckBox) continue;
                control.Width = width;
            }
        }
        flow.Resize += (_, _) => FitControls();
        flow.ControlAdded += (_, _) => FitControls();
        return flow;
    }

    private static Control HeaderField(string title, TextBox textBox)
    {
        var panel = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, AutoSize = true, WrapContents = false, Margin = new Padding(0, 0, 12, 0) };
        panel.Controls.Add(new Label { Text = title, AutoSize = true, Font = new Font("Segoe UI", 8.5f, FontStyle.Bold) });
        panel.Controls.Add(textBox);
        return panel;
    }

    private static Control Field(string title, TextBox textBox)
    {
        var panel = new TableLayoutPanel { ColumnCount = 1, RowCount = 2, Width = 320, Height = 72, Margin = new Padding(0, 0, 0, 8) };
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute, 27));
        panel.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        panel.Controls.Add(new Label { Text = title, AutoSize = true, Font = LumiTheme.AppFont(9.5f, FontStyle.Bold), ForeColor = LumiTheme.Ink }, 0, 0);
        var inputFrame = LumiTheme.InputFrame(textBox);
        inputFrame.Dock = DockStyle.Fill;
        inputFrame.Margin = Padding.Empty;
        panel.Controls.Add(inputFrame, 0, 1);
        return panel;
    }

    private static Control ComboField(string title, ComboBox combo)
    {
        var panel = new TableLayoutPanel { ColumnCount = 1, RowCount = 2, Width = 320, Height = 72, Margin = new Padding(0, 0, 0, 8) };
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute, 27));
        panel.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        panel.Controls.Add(new Label { Text = title, AutoSize = true, Font = LumiTheme.AppFont(9.5f, FontStyle.Bold), ForeColor = LumiTheme.Ink }, 0, 0);
        var inputFrame = LumiTheme.InputFrame(combo);
        inputFrame.Dock = DockStyle.Fill;
        inputFrame.Margin = Padding.Empty;
        panel.Controls.Add(inputFrame, 0, 1);
        return panel;
    }

    private static Button CreateButton(string text, EventHandler onClick)
    {
        var button = new Button { Text = text, AutoSize = true, Padding = new Padding(10, 5, 10, 5), BackColor = Color.FromArgb(46, 116, 181), ForeColor = Color.White, FlatStyle = FlatStyle.Flat, Margin = new Padding(3, 8, 8, 3) };
        button.FlatAppearance.BorderSize = 0;
        button.Click += onClick;
        return button;
    }
}
