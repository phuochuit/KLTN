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
    private readonly Label _connection = new() { Text = "Chưa kiểm tra dịch vụ", AutoSize = true, ForeColor = Color.DarkOrange };
    private readonly System.Windows.Forms.Timer _sessionHeartbeat = new() { Interval = 2000 };
    private readonly System.Windows.Forms.Timer _outboxTimer = new() { Interval = 15000 };
    private readonly System.Windows.Forms.Timer _clockTimer = new() { Interval = 1000 };
    private readonly Label _operatorName = new() { AutoSize = true, Font = new Font("Segoe UI", 10, FontStyle.Bold), ForeColor = Color.FromArgb(23, 54, 93) };
    private readonly Label _operatorShift = new() { AutoSize = true, ForeColor = Color.DimGray };
    private readonly Label _currentTime = new() { AutoSize = true, ForeColor = Color.FromArgb(23, 54, 93) };
    private readonly PictureBox _vehiclePreview = PreviewBox();
    private readonly PictureBox _facePreview = PreviewBox();
    private readonly Label _result = new() { Dock = DockStyle.Fill, AutoSize = false, Padding = new Padding(16), Font = new Font("Segoe UI", 10.5f), BorderStyle = BorderStyle.FixedSingle };
    private readonly Label _aiStatus = new() { Text = "Chọn ảnh hoặc video để nhận dạng tự động", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(10, 12, 0, 0) };
    private readonly Label _faceStatus = new() { Text = "Chưa chụp khuôn mặt", AutoSize = true, ForeColor = Color.DimGray, Padding = new Padding(8) };
    private readonly TextBox _entryPlate = PlateBox();
    private readonly TextBox _exitPlate = PlateBox();
    private readonly TextBox _vehicleType = new() { Width = 190, ReadOnly = true, Text = "UNKNOWN" };
    private readonly Label _residentInfo = new() {
        Width = 260, Height = 90, AutoSize = false, Padding = new Padding(10),
        BorderStyle = BorderStyle.FixedSingle, BackColor = Color.FromArgb(245, 247, 250),
        Text = "Chưa tra cứu cư dân"
    };
    private readonly Button _editAiResult = new() { Text = "CHỈNH SỬA KẾT QUẢ AI", AutoSize = true, Enabled = false };
    private readonly Button _compareEvidenceBtn = new() { Text = "ĐỐI CHIẾU ẢNH VÀO / RA", AutoSize = true };
    private readonly Label _editAiHint = new() { Text = "Chỉ sử dụng sau khi đã có ảnh nhận diện từ camera/AI.", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(10, 12, 0, 0) };
    private readonly TabControl _tabs = new() { Dock = DockStyle.Fill };
    private readonly ComboBox _entryMember = new() { Width = 250, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _exitMember = new() { Width = 250, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly FlowLayoutPanel _mapPanel = new() { Dock = DockStyle.Fill, AutoScroll = true, WrapContents = true };
    private readonly Label _barrierState = new() { Text = "BARRIER: ĐANG ĐÓNG", AutoSize = true, ForeColor = Color.Firebrick, Font = new Font("Segoe UI", 10, FontStyle.Bold), Margin = new Padding(8, 17, 5, 0) };
    private bool _entryFaceVerified, _exitFaceVerified;
    private double? _entryFaceSimilarity, _exitFaceSimilarity;
    private long? _entryVerifiedMemberId, _exitVerifiedMemberId;
    private string _entryGuestFaceBase64 = "", _latestFaceImageBase64 = "";
    private bool _exitGuestFaceAvailable;
    private string _entryVehicleEvidenceId = "", _exitVehicleEvidenceId = "";
    private string _entryFaceEvidenceId = "", _exitFaceEvidenceId = "";
    private string _entryOverrideReasonCode = "", _entryOverrideReasonText = "";
    private string _exitOverrideReasonCode = "", _exitOverrideReasonText = "";
    private ParkingResponse? _lastPreview;
    private string _entryDetectedType = "UNKNOWN", _exitDetectedType = "UNKNOWN";
    private bool _checkingSession;
    private bool _syncingOutbox;
    private bool _vehicleBusy, _faceBusy;
    private long? _activeShiftId;

    public MainForm(ParkingApiClient parkingApi, GateStationSettingsStore settingsStore, IBarrierController barrier)
    {
        _parkingApi = parkingApi;
        _settingsStore = settingsStore;
        _barrier = barrier;
        _stationSettings = settingsStore.Load();
        _parkingApi.StationSettings = _stationSettings;
        _barrier.StatusChanged += BarrierStatusChanged;
        _parkingApi.OperatorProfileChanged += UpdateOperatorHeader;
        _mapPanel.Resize += (_, _) => ResizeSlots(_mapPanel);

        OnSharedMapUpdated += () => {
            if (!this.IsDisposed && this.IsHandleCreated)
            {
                this.Invoke((MethodInvoker)async delegate {
                    try { await RefreshMapAsync(); } catch { }
                });
            }
        };

        Text = "Trạm gác - Parking ANPR";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(1120, 720);
        Size = new Size(1240, 800);
        Font = new Font("Segoe UI", 10);
        BackColor = Color.White;

        var root = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1, Padding = new Padding(16) };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 145));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
        Controls.Add(root);
        root.Controls.Add(BuildHeader(), 0, 0);
        root.Controls.Add(BuildBody(), 0, 1);
        var editPanel = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.LeftToRight };
        editPanel.Controls.Add(_editAiResult);
        editPanel.Controls.Add(_compareEvidenceBtn);
        editPanel.Controls.Add(_editAiHint);
        root.Controls.Add(editPanel, 0, 2);
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
            await CheckServicesAsync();
            if (_parkingApi.CurrentOperator is not null) UpdateOperatorHeader(_parkingApi.CurrentOperator);
            UpdateClock();
            _clockTimer.Start();
            _sessionHeartbeat.Start();
            _outboxTimer.Start();
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
        };
    }

    private Control BuildHeader()
    {
        var panel = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = true };
        panel.Controls.Add(new Label { Text = "PARKING ANPR", AutoSize = true, Font = new Font("Segoe UI", 18, FontStyle.Bold), ForeColor = Color.FromArgb(23, 54, 93), Margin = new Padding(0, 8, 25, 0) });
        panel.Controls.Add(HeaderField("Spring API", _parkingUrl));
        panel.Controls.Add(CreateButton("Kiểm tra", async (_, _) => await CheckServicesAsync()));
        panel.Controls.Add(CreateButton("Đăng xuất", LogoutClicked));
        panel.Controls.Add(CreateButton("Sơ đồ bãi", (_, _) => { ConfigureApis(); new ParkingMapForm(_parkingApi).Show(this); }));
        panel.Controls.Add(CreateButton("Cấu hình trạm", (_, _) => ShowStationSettings()));
        panel.Controls.Add(CreateButton("MỞ BARRIER", async (_, _) => await SetBarrierAsync(true, "Mở thủ công")));
        panel.Controls.Add(CreateButton("ĐÓNG BARRIER", async (_, _) => await SetBarrierAsync(false, "Đóng thủ công")));
        panel.Controls.Add(_barrierState);
        panel.Controls.Add(_connection);
        _connection.Margin = new Padding(10, 17, 0, 0);
        var operatorPanel = new FlowLayoutPanel {
            AutoSize = true, FlowDirection = FlowDirection.TopDown, WrapContents = false,
            Margin = new Padding(20, 6, 0, 0)
        };
        operatorPanel.Controls.Add(_operatorName);
        operatorPanel.Controls.Add(_operatorShift);
        operatorPanel.Controls.Add(_currentTime);
        panel.Controls.Add(operatorPanel);
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
        var split = new SplitContainer { Dock = DockStyle.Fill, SplitterDistance = 570, BackColor = Color.White };
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1, Padding = new Padding(0, 0, 10, 0) };
        left.RowStyles.Add(new RowStyle(SizeType.Percent, 68));
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
        left.RowStyles.Add(new RowStyle(SizeType.Percent, 32));
        left.Controls.Add(BuildCapturePreviews(), 0, 0);
        var media = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoScroll = true, FlowDirection = FlowDirection.LeftToRight };
        media.Controls.Add(CreateButton("XỬ LÝ SONG SONG", async (_, _) => await SelectAndProcessParallelAsync()));
        media.Controls.Add(new Label { Text = "Media xe và camera mặt sẽ chạy đồng thời", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(10, 17, 0, 0) });
        left.Controls.Add(media, 0, 1);
        var mapBox=new GroupBox{Text="Sơ đồ vị trí đỗ (cập nhật từ database)",Dock=DockStyle.Fill};mapBox.Controls.Add(_mapPanel);left.Controls.Add(mapBox,0,2);
        split.Panel1.Controls.Add(left);

        var right = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 2, ColumnCount = 1, Padding = new Padding(10, 0, 0, 0) };
        right.RowStyles.Add(new RowStyle(SizeType.Percent, 70));
        right.RowStyles.Add(new RowStyle(SizeType.Percent, 30));
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
        flow.Controls.Add(Field("Biển số AI tự điền", _entryPlate));
        flow.Controls.Add(Field("Loại xe AI", _vehicleType));
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
        flow.Controls.Add(Field("Biển số AI tự điền", _exitPlate));
        flow.Controls.Add(CreateButton("TRA CỨU NGƯỜI ĐƯỢC PHÉP", async (_, _) => await LookupResidentAsync(false)));
        flow.Controls.Add(ComboField("Người đang lấy xe", _exitMember));
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
        await ProcessVehicleAndFaceAsync(() => ProcessMediaAsync(dialog.FileName, video));
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
            _entryDetectedType = response.VehicleType;
            _entryVehicleEvidenceId = response.EvidenceId ?? "";
        } else {
            _exitDetectedType = response.VehicleType;
            _exitVehicleEvidenceId = response.EvidenceId ?? "";
        }
        _vehicleType.Text = DisplayVehicleType(response.VehicleType);
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
        _aiStatus.Text = $"{response.Message} | {elapsed.TotalSeconds:0.00}s | Biển số: {response.PlateText} | {DisplayVehicleType(response.VehicleType)} | YOLO {response.DetectionConfidence:P0} | OCR {response.OcrConfidence:P0} | frame {response.FrameIndex}";
        UpdateManualEditState();
    }

    private async Task CheckServicesAsync()
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
                    : "Có dịch vụ chưa sẵn sàng";
            _connection.ForeColor = pending > 0 ? Color.DarkOrange
                : checks.All(x => x) ? Color.SeaGreen : Color.Firebrick;
            if(checks.All(x=>x)) await RefreshMapAsync();
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
            string card = NormalizePlate(plate);
            if (string.IsNullOrWhiteSpace(plate))
            {
                _residentInfo.Text = "Nhập hoặc nhận diện biển số để tra cứu";
                _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
                return;
            }
            ResidentLookupResponse resident = await _parkingApi.LookupResidentAsync(plate, card);
            ComboBox combo = entry ? _entryMember : _exitMember;
            combo.DataSource = resident.AuthorizedMembers?.ToList() ?? new List<AuthorizedMemberResponse>();
            if(!entry) _exitGuestFaceAvailable=resident.GuestEntryFaceAvailable;
            if(entry){_entryFaceVerified=false;_entryFaceSimilarity=null;_entryVerifiedMemberId=null;}else{_exitFaceVerified=false;_exitFaceSimilarity=null;_exitVerifiedMemberId=null;}
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
        }
    }

    private async Task PreviewExitAsync()
    {
        try
        {
            ConfigureApis();
            var previewRequest = Request(_exitPlate.Text);
            if (previewRequest == null) return;
            _lastPreview = await _parkingApi.PreviewExitAsync(previewRequest);
            ShowParkingResult(_lastPreview);
        }
        catch (Exception ex) { ShowError(ex); }
    }

    private async Task ConfirmExitAsync()
    {
        try
        {
            if (_lastPreview == null)
            {
                MessageBox.Show("Hãy bấm Xem trước phí trước khi xác nhận.", "Chưa xem trước", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            string previewPlate = NormalizePlate(_lastPreview.PlateNumber ?? "");
            if (string.IsNullOrWhiteSpace(previewPlate))
            {
                _lastPreview = null;
                MessageBox.Show(this,
                    "Dữ liệu xem trước phí không có biển số hợp lệ. Vui lòng bấm Xem trước phí lại.",
                    "Dữ liệu xem trước không hợp lệ", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            if (!string.Equals(previewPlate, NormalizePlate(_exitPlate.Text),
                    StringComparison.OrdinalIgnoreCase))
            {
                MessageBox.Show(this,
                    "Biển số hiện tại khác với lần xem trước phí. Vui lòng bấm Xem trước phí lại.",
                    "Biển số đã thay đổi", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            ConfigureApis();
            var exitRequest = Request(_exitPlate.Text);
            if (exitRequest == null) return;
            var response = await _parkingApi.ConfirmExitAsync(exitRequest);
            ShowParkingResult(response);
            if(!response.Warning || IsManual(false)) AutoOpenBarrier("Xe ra đã được xác nhận");
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
                response = video
                    ? await _parkingApi.VerifyGuestFaceVideoAsync(vehicleEvidenceId, dialog.FileName)
                    : await _parkingApi.VerifyGuestFaceImageAsync(vehicleEvidenceId, dialog.FileName);
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
                    RequireExitVehicleEvidence(), _latestFaceImageBase64);
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
        var combo = entry ? _entryMember : _exitMember;
        bool pass = response.Decision == "PASS";
        if(entry) _entryFaceEvidenceId=response.EvidenceId??""; else _exitFaceEvidenceId=response.EvidenceId??"";
        if(entry){_entryFaceVerified=pass;_entryFaceSimilarity=response.Similarity;_entryVerifiedMemberId=(combo.SelectedItem as AuthorizedMemberResponse)?.Id;}else{_exitFaceVerified=pass;_exitFaceSimilarity=response.Similarity;_exitVerifiedMemberId=(combo.SelectedItem as AuthorizedMemberResponse)?.Id;}
        if(!string.IsNullOrWhiteSpace(response.RealtimeImageBase64)){using var ms=new MemoryStream(Convert.FromBase64String(response.RealtimeImageBase64));using var img=Image.FromStream(ms);_facePreview.Image?.Dispose();_facePreview.Image=new Bitmap(img);}
        _faceStatus.Text=$"{DisplayFaceDecision(response.Decision)} | {elapsed.TotalSeconds:0.00}s | Điểm {response.Similarity:0.000}";
        _faceStatus.ForeColor=pass?Color.SeaGreen:Color.Firebrick;
        _result.Text=$"Xác thực khuôn mặt: {DisplayFaceDecision(response.Decision)}\r\nĐiểm tương đồng SFace: {response.Similarity:0.000}\r\nNgưỡng cho phép: từ {response.MatchThreshold:0.000}\r\n{response.Message}";
        _result.BackColor=pass?Color.Honeydew:Color.MistyRose;
    }

    private ParkingRequest? Request(string plate)
    {
        bool entry=_tabs.SelectedIndex==0; var selected=(entry?_entryMember:_exitMember).SelectedItem as AuthorizedMemberResponse;
        bool verified=entry?_entryFaceVerified:_exitFaceVerified;long? verifiedId=entry?_entryVerifiedMemberId:_exitVerifiedMemberId;
        if(selected != null && selected.Id != verifiedId) verified=false;
        EnsureManualOverrideReason(entry);
        string identifier = NormalizePlate(plate);
        bool exit = !entry;
        string? paymentMethod = null;
        string? paymentReference = null;
        if (exit && _lastPreview is { Fee: > 0 })
        {
            using var dialog = new PaymentMethodDialog(plate, _lastPreview.Fee);
            if (dialog.ShowDialog(this) != DialogResult.OK || !dialog.ApplyPayment
                || dialog.SelectedMethod is null) return null!;
            paymentMethod = dialog.SelectedMethod;
            paymentReference = dialog.TransactionReference;
        }
        return new ParkingRequest(plate,identifier,entry?_entryDetectedType:_exitDetectedType,IsManual(entry),selected?.Id,verified,
            entry?_entryFaceSimilarity:_exitFaceSimilarity,entry?_entryGuestFaceBase64:"",
            entry?_entryVehicleEvidenceId:_exitVehicleEvidenceId,
            entry?_entryFaceEvidenceId:_exitFaceEvidenceId,
            entry?_entryOverrideReasonCode:_exitOverrideReasonCode,
            entry?_entryOverrideReasonText:_exitOverrideReasonText,
            paymentMethod,paymentReference);
    }

    private void EnsureManualOverrideReason(bool entry)
    {
        if (!IsManual(entry)) return;
        string code = entry ? _entryOverrideReasonCode : _exitOverrideReasonCode;
        if (string.IsNullOrWhiteSpace(code)) throw new InvalidOperationException("Vui lòng chọn lý do chỉnh sửa kết quả AI.");
    }

    private bool IsManual(bool entry) => !string.IsNullOrWhiteSpace(
        entry ? _entryOverrideReasonCode : _exitOverrideReasonCode);

    private void EditAiResult()
    {
        bool entry = _tabs.SelectedIndex == 0;
        string evidenceId = entry ? _entryVehicleEvidenceId : _exitVehicleEvidenceId;
        if (string.IsNullOrWhiteSpace(evidenceId)) {
            MessageBox.Show(this, "Hãy chụp hoặc tải ảnh xe để AI nhận diện trước khi chỉnh sửa.",
                "Chưa có kết quả AI", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        string plate = entry ? _entryPlate.Text : _exitPlate.Text;
        string vehicleType = entry ? _entryDetectedType : _exitDetectedType;
        using var dialog = new ManualOverrideDialog(plate, vehicleType);
        if (dialog.ShowDialog(this) != DialogResult.OK) return;
        if (entry) {
            _entryPlate.Text = dialog.CorrectedPlate;
            _entryDetectedType = dialog.CorrectedVehicleType;
            _entryOverrideReasonCode = dialog.ReasonCode;
            _entryOverrideReasonText = dialog.ReasonText;
            _vehicleType.Text = DisplayVehicleType(_entryDetectedType);
        } else {
            _exitPlate.Text = dialog.CorrectedPlate;
            _exitDetectedType = dialog.CorrectedVehicleType;
            _exitOverrideReasonCode = dialog.ReasonCode;
            _exitOverrideReasonText = dialog.ReasonText;
        }
        _editAiHint.Text = "Đã chỉnh sửa kết quả AI; thao tác sẽ được lưu trong nhật ký.";
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
            var comparison = await _parkingApi.GetEvidenceComparisonAsync(
                plate,
                currentType,
                _exitVehicleEvidenceId,
                _exitFaceEvidenceId,
                sessionId);
            bool hasActiveShift = _activeShiftId.HasValue || _parkingApi.CurrentOperator?.ActiveShift is not null;
            using var dlg = new EvidenceComparisonDialog(comparison, hasActiveShift, plate, currentType);
            if (dlg.ShowDialog(this) == DialogResult.OK && dlg.OverrideApplied)
            {
                if (entry)
                {
                    _entryPlate.Text = dlg.CorrectedPlate;
                    _entryDetectedType = dlg.CorrectedVehicleType;
                    _entryOverrideReasonCode = dlg.OverrideReasonCode;
                    _entryOverrideReasonText = dlg.OverrideReasonText;
                    _vehicleType.Text = DisplayVehicleType(_entryDetectedType);
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
        }
        catch (Exception ex)
        {
            ShowError(ex);
        }
    }

    private void UpdateManualEditState()
    {
        bool entry = _tabs.SelectedIndex == 0;
        _editAiResult.Enabled = !string.IsNullOrWhiteSpace(entry ? _entryVehicleEvidenceId : _exitVehicleEvidenceId);
        bool edited = IsManual(entry);
        _editAiResult.Text = edited ? "CHỈNH SỬA LẠI KẾT QUẢ" : "CHỈNH SỬA KẾT QUẢ AI";
        _editAiHint.Text = edited ? "Đã chỉnh sửa kết quả AI; thao tác sẽ được lưu trong nhật ký."
            : "Chỉ sử dụng sau khi đã có ảnh nhận diện từ camera/AI.";
        _editAiHint.ForeColor = edited ? Color.DarkOrange : Color.DimGray;
    }

    private async Task CaptureFaceSnapshotAsync(bool entry){try{SetFaceBusy(true,"Đang chụp khuôn mặt...");ConfigureApis();var r=await _parkingApi.CaptureCameraAsync(entry?"ENTRY":"EXIT");if(entry)_entryFaceEvidenceId=r.EvidenceId??"";else _exitFaceEvidenceId=r.EvidenceId??"";_latestFaceImageBase64="data:image/jpeg;base64,"+r.RealtimeImageBase64;if(entry)_entryGuestFaceBase64=_latestFaceImageBase64;ShowCapturedFace(r);_faceStatus.Text="Đã chụp khuôn mặt; đang chờ đối chiếu";_faceStatus.ForeColor=Color.SeaGreen;}catch(Exception ex){ShowFaceError(ex);}finally{SetFaceBusy(false,_faceStatus.Text);}}
    private void ShowCapturedFace(FaceVerificationResponse r){if(string.IsNullOrWhiteSpace(r.RealtimeImageBase64))return;using var ms=new MemoryStream(Convert.FromBase64String(r.RealtimeImageBase64));using var img=Image.FromStream(ms);_facePreview.Image?.Dispose();_facePreview.Image=new Bitmap(img);}
        private async Task RefreshMapAsync()
    {
        try
        {
            _mapPanel.SuspendLayout();
            _mapPanel.Controls.Clear();
            foreach (var item in await _parkingApi.GetSlotsAsync())
            {
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
                _mapPanel.Controls.Add(slot);
            }
            ResizeSlots(_mapPanel);
            _mapPanel.ResumeLayout();
        }
        catch { }
    }

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
            ? "Đã lưu cấu hình. Nguồn camera mạng cần dịch vụ AI hỗ trợ RTSP/HTTP ở bước tiếp theo."
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
            MessageBox.Show(this,
                "Phiên làm việc đã được kết thúc. Vui lòng đăng nhập lại.",
                "Yêu cầu đăng nhập lại", MessageBoxButtons.OK, MessageBoxIcon.Information);
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
        await ShowShiftSummaryAsync(previous: false);
        Enabled = false;
        try { await _parkingApi.LogoutAsync(); }
        catch { /* Token is always cleared locally; the server session will expire if unreachable. */ }
        finally
        {
            LogoutCompleted?.Invoke();
            Close();
        }
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
        _entryPlate.Text = "";
        _vehicleType.Text = "UNKNOWN";
        _entryDetectedType = "UNKNOWN";
        _vehiclePreview.Image?.Dispose();
        _vehiclePreview.Image = null;
        _facePreview.Image?.Dispose();
        _facePreview.Image = null;
        _result.Text = "";
        _result.BackColor = SystemColors.Control;
        _aiStatus.Text = "Chọn ảnh hoặc video để nhận dạng tự động";
        _aiStatus.ForeColor = Color.DimGray;
        _faceStatus.Text = "Chọn ảnh, video hoặc camera khuôn mặt";
        _faceStatus.ForeColor = Color.DimGray;
        _residentInfo.Text = "Chưa tra cứu cư dân";
        _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
        _entryMember.DataSource = null;
        _entryFaceVerified = false;
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
        _vehicleType.Text = "UNKNOWN";
        _exitDetectedType = "UNKNOWN";
        _vehiclePreview.Image?.Dispose();
        _vehiclePreview.Image = null;
        _facePreview.Image?.Dispose();
        _facePreview.Image = null;
        _result.Text = "";
        _result.BackColor = SystemColors.Control;
        _aiStatus.Text = "Chọn ảnh hoặc video để nhận dạng tự động";
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
            HttpRequestException => "Không kết nối được đến dịch vụ.\r\n\r\nNguyên nhân có thể: Spring Web hoặc ANPR chưa chạy, sai địa chỉ/cổng, hoặc dịch vụ vừa bị tắt.\r\n\r\nCách xử lý: chạy run-all-laragon.cmd, chờ các dịch vụ khởi động xong rồi bấm Kiểm tra.",
            TaskCanceledException => "Dịch vụ phản hồi quá thời gian cho phép.\r\n\r\nNếu đang nhận dạng video hoặc mở camera, hãy kiểm tra camera và thử lại. Nếu không, hãy khởi động lại dịch vụ ANPR.",
            System.Text.Json.JsonException => "Máy chủ trả về dữ liệu không đúng định dạng. Có thể đang chạy phiên bản dịch vụ cũ.\r\n\r\nHãy đóng các cửa sổ Parking Web/ANPR cũ và chạy lại run-all-laragon.cmd.",
            _ => ex.Message
        };
        if (ex is NullReferenceException)
            message = "Ứng dụng gặp dữ liệu không hợp lệ khi xử lý thao tác. "
                + "Mã lỗi: " + errorId + ". Vui lòng gửi file desktop-error.log để kiểm tra.";
        _result.BackColor = Color.MistyRose;
        _result.ForeColor = Color.Firebrick;
        _result.Text = "KHÔNG THỂ THỰC HIỆN\r\n\r\n" + message;
        _aiStatus.Text = "Nhận dạng thất bại - có thể nhập biển số thủ công";
        _aiStatus.ForeColor = Color.Firebrick;
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
        var vehicleLayout = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1 };
        vehicleLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        vehicleLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        vehicleLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        vehicleLayout.Controls.Add(_vehiclePreview, 0, 0);
        var vehicleButtons = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoSize = true };
        vehicleButtons.Controls.Add(CreateButton("Tải ảnh", async (_, _) => await SelectAndRecognizeAsync(false)));
        vehicleButtons.Controls.Add(CreateButton("Tải video", async (_, _) => await SelectAndRecognizeAsync(true)));
        vehicleButtons.Controls.Add(CreateButton("Camera xe", async (_, _) => await RecognizeVehicleCameraAsync()));
        vehicleLayout.Controls.Add(vehicleButtons, 0, 1);
        vehicleLayout.Controls.Add(_aiStatus, 0, 2);
        var vehicle = new GroupBox { Text = "XE / BIỂN SỐ", Dock = DockStyle.Fill, Padding = new Padding(8) };
        vehicle.Controls.Add(vehicleLayout);
        var faceLayout = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1 };
        faceLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        faceLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        faceLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        faceLayout.Controls.Add(_facePreview, 0, 0);
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
        return split;
    }
    private static PictureBox PreviewBox() => new() { Dock = DockStyle.Fill, SizeMode = PictureBoxSizeMode.Zoom, BackColor = Color.FromArgb(235, 240, 246) };
    private static string DisplayVehicleType(string type) => type switch
    {
        "BICYCLE_ELECTRIC_BICYCLE" => "Xe đạp / xe đạp điện",
        "MOTORBIKE" => "Xe máy / xe máy điện",
        "LARGE_MOTORBIKE" => "Xe mô tô phân khối lớn",
        "CAR" => "Ô tô 4-5 chỗ",
        "CAR_6_7" => "Ô tô 6-7 chỗ",
        "CAR_8_9" => "Ô tô 8-9 chỗ",
        _ => "Không xác định"
    };
    private static string DisplayFaceDecision(string decision) => decision switch
    {
        "PASS" => "ĐẠT - được phép tiếp tục",
        "REVIEW" => "CẦN KIỂM TRA LẠI",
        "REJECT" => "KHÔNG KHỚP",
        _ => decision
    };
    private static TextBox PlateBox() => new() { Width = 190, CharacterCasing = CharacterCasing.Upper };
    private static string NormalizePlate(string value) => new(value.ToUpperInvariant().Where(char.IsLetterOrDigit).ToArray());
    private static string Blank(string? value) => string.IsNullOrWhiteSpace(value) ? "-" : value;
    private static FlowLayoutPanel FormFlow() => new() { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoScroll = true };

    private static Control HeaderField(string title, TextBox textBox)
    {
        var panel = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, AutoSize = true, WrapContents = false, Margin = new Padding(0, 0, 12, 0) };
        panel.Controls.Add(new Label { Text = title, AutoSize = true, Font = new Font("Segoe UI", 8.5f, FontStyle.Bold) });
        panel.Controls.Add(textBox);
        return panel;
    }

    private static Control Field(string title, TextBox textBox)
    {
        var panel = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, AutoSize = true, WrapContents = false, Margin = new Padding(0, 0, 0, 12) };
        panel.Controls.Add(new Label { Text = title, AutoSize = true, Font = new Font("Segoe UI", 10, FontStyle.Bold) });
        panel.Controls.Add(textBox);
        return panel;
    }

    private static Control ComboField(string title, ComboBox combo)
    {
        var panel=new FlowLayoutPanel{FlowDirection=FlowDirection.TopDown,AutoSize=true,WrapContents=false,Margin=new Padding(0,0,0,12)};
        panel.Controls.Add(new Label{Text=title,AutoSize=true,Font=new Font("Segoe UI",10,FontStyle.Bold)});panel.Controls.Add(combo);return panel;
    }

    private static Button CreateButton(string text, EventHandler onClick)
    {
        var button = new Button { Text = text, AutoSize = true, Padding = new Padding(10, 5, 10, 5), BackColor = Color.FromArgb(46, 116, 181), ForeColor = Color.White, FlatStyle = FlatStyle.Flat, Margin = new Padding(3, 8, 8, 3) };
        button.FlatAppearance.BorderSize = 0;
        button.Click += onClick;
        return button;
    }
}
