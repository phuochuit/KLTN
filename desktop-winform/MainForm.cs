using System.Drawing;

namespace ParkingGateDesktop;

public sealed class MainForm : Form
{
    private readonly ParkingApiClient _parkingApi = new();
    private readonly AnprApiClient _anprApi = new();
    private readonly TextBox _parkingUrl = new() { Text = "http://localhost:8080", Width = 190 };
    private readonly TextBox _anprUrl = new() { Text = "http://localhost:8001", Width = 190 };
    private readonly Label _connection = new() { Text = "Chưa kiểm tra dịch vụ", AutoSize = true, ForeColor = Color.DarkOrange };
    private readonly PictureBox _preview = new() { Dock = DockStyle.Fill, SizeMode = PictureBoxSizeMode.Zoom, BackColor = Color.FromArgb(235, 240, 246) };
    private readonly Label _result = new() { Dock = DockStyle.Fill, AutoSize = false, Padding = new Padding(16), Font = new Font("Segoe UI", 10.5f), BorderStyle = BorderStyle.FixedSingle };
    private readonly Label _aiStatus = new() { Text = "Chọn ảnh hoặc video để nhận dạng tự động", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(10, 12, 0, 0) };
    private readonly TextBox _entryPlate = PlateBox();
    private readonly TextBox _entryCard = PlateBox();
    private readonly TextBox _exitPlate = PlateBox();
    private readonly TextBox _exitCard = PlateBox();
    private readonly TextBox _vehicleType = new() { Width = 190, ReadOnly = true, Text = "UNKNOWN" };
    private readonly Label _residentInfo = new() {
        Width = 260, Height = 90, AutoSize = false, Padding = new Padding(10),
        BorderStyle = BorderStyle.FixedSingle, BackColor = Color.FromArgb(245, 247, 250),
        Text = "Chưa tra cứu cư dân"
    };
    private readonly CheckBox _manual = new() { Text = "Xác nhận thủ công khi AI cảnh báo", AutoSize = true };
    private readonly TabControl _tabs = new() { Dock = DockStyle.Fill };
    private readonly ComboBox _entryMember = new() { Width = 250, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _exitMember = new() { Width = 250, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly FlowLayoutPanel _mapPanel = new() { Dock = DockStyle.Fill, AutoScroll = true, WrapContents = true };
    private readonly Label _barrierState = new() { Text = "BARRIER: ĐANG ĐÓNG", AutoSize = true, ForeColor = Color.Firebrick, Font = new Font("Segoe UI", 10, FontStyle.Bold), Margin = new Padding(8, 17, 5, 0) };
    private bool _entryFaceVerified, _exitFaceVerified;
    private double? _entryFaceSimilarity, _exitFaceSimilarity;
    private long? _entryVerifiedMemberId, _exitVerifiedMemberId;
    private string _entryGuestFaceBase64 = "", _exitGuestFacePath = "";
    private bool _barrierOpen;
    private ParkingResponse? _lastPreview;
    private string _detectedType = "UNKNOWN";

    public MainForm()
    {
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
        root.Controls.Add(_manual, 0, 2);
    }

    private Control BuildHeader()
    {
        var panel = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = true };
        panel.Controls.Add(new Label { Text = "PARKING ANPR", AutoSize = true, Font = new Font("Segoe UI", 18, FontStyle.Bold), ForeColor = Color.FromArgb(23, 54, 93), Margin = new Padding(0, 8, 25, 0) });
        panel.Controls.Add(HeaderField("Spring API", _parkingUrl));
        panel.Controls.Add(HeaderField("ANPR API", _anprUrl));
        panel.Controls.Add(CreateButton("Kiểm tra", async (_, _) => await CheckServicesAsync()));
        panel.Controls.Add(CreateButton("Sơ đồ bãi", (_, _) => { ConfigureApis(); new ParkingMapForm(_parkingApi).Show(this); }));
        panel.Controls.Add(CreateButton("MỞ BARRIER", (_, _) => SetBarrier(true, "Mở thủ công")));
        panel.Controls.Add(CreateButton("ĐÓNG BARRIER", (_, _) => SetBarrier(false, "Đóng thủ công")));
        panel.Controls.Add(_barrierState);
        panel.Controls.Add(_connection);
        _connection.Margin = new Padding(10, 17, 0, 0);
        return panel;
    }

    private Control BuildBody()
    {
        var split = new SplitContainer { Dock = DockStyle.Fill, SplitterDistance = 570, BackColor = Color.White };
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, ColumnCount = 1, Padding = new Padding(0, 0, 10, 0) };
        left.RowStyles.Add(new RowStyle(SizeType.Percent, 68));
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
        left.RowStyles.Add(new RowStyle(SizeType.Percent, 32));
        left.Controls.Add(_preview, 0, 0);
        var media = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoScroll = true };
        media.Controls.Add(CreateButton("Nhận dạng ảnh", async (_, _) => await SelectAndRecognizeAsync(false)));
        media.Controls.Add(CreateButton("Nhận dạng video", async (_, _) => await SelectAndRecognizeAsync(true)));
        media.Controls.Add(_aiStatus);
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
        flow.Controls.Add(Field("Mã thẻ (không bắt buộc)", _entryCard));
        flow.Controls.Add(CreateButton("TRA CỨU CƯ DÂN", async (_, _) => await LookupResidentAsync()));
        flow.Controls.Add(ComboField("Người đang điều khiển", _entryMember));
        flow.Controls.Add(CreateButton("CHỤP & XÁC THỰC KHUÔN MẶT", async (_, _) => await VerifyFaceAsync(true)));
        flow.Controls.Add(CreateButton("CHỤP ẢNH KHÁCH VÃNG LAI", async (_, _) => await CaptureGuestAsync()));
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
        flow.Controls.Add(Field("Mã thẻ (không bắt buộc)", _exitCard));
        flow.Controls.Add(CreateButton("TRA CỨU NGƯỜI ĐƯỢC PHÉP", async (_, _) => await LookupResidentAsync(false)));
        flow.Controls.Add(ComboField("Người đang lấy xe", _exitMember));
        flow.Controls.Add(CreateButton("CHỤP & XÁC THỰC KHUÔN MẶT", async (_, _) => await VerifyFaceAsync(false)));
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
        try
        {
            _result.Text = "";
            _result.BackColor = SystemColors.Control;
            ConfigureApis();
            SetBusy(true, video ? "Đang lấy mẫu frame và nhận dạng video..." : "Đang nhận dạng ảnh...");
            if (!video) ShowLocalImage(dialog.FileName);
            AnprResponse response = video
                ? await _anprApi.RecognizeVideoAsync(dialog.FileName)
                : await _anprApi.RecognizeImageAsync(dialog.FileName);
            ApplyRecognition(response);
        }
        catch (Exception ex) { ShowError(ex); }
        finally { SetBusy(false, _aiStatus.Text); }
    }

    private void ApplyRecognition(AnprResponse response)
    {
        _detectedType = response.VehicleType;
        _vehicleType.Text = DisplayVehicleType(response.VehicleType);
        if (_tabs.SelectedIndex == 0) _entryPlate.Text = response.PlateText;
        else _exitPlate.Text = response.PlateText;
        if (!string.IsNullOrWhiteSpace(response.AnnotatedImageBase64))
        {
            byte[] bytes = Convert.FromBase64String(response.AnnotatedImageBase64);
            using var stream = new MemoryStream(bytes);
            using var image = Image.FromStream(stream);
            _preview.Image?.Dispose();
            _preview.Image = new Bitmap(image);
        }
        bool lowConfidence = string.IsNullOrWhiteSpace(response.PlateText) || response.OcrConfidence < 0.45 || response.DetectionConfidence < 0.25;
        _manual.Checked = lowConfidence;
        _aiStatus.ForeColor = lowConfidence ? Color.DarkOrange : Color.SeaGreen;
        _aiStatus.Text = $"{response.Message} | Biển số: {response.PlateText} | {DisplayVehicleType(response.VehicleType)} | YOLO {response.DetectionConfidence:P0} | OCR {response.OcrConfidence:P0} | frame {response.FrameIndex}";
        if (_tabs.SelectedIndex == 0 && !string.IsNullOrWhiteSpace(response.PlateText))
            _ = LookupResidentAsync();
    }

    private async Task CheckServicesAsync()
    {
        try
        {
            ConfigureApis();
            var checks = await Task.WhenAll(_parkingApi.HealthAsync(), _anprApi.HealthAsync());
            _connection.Text = checks.All(x => x) ? "Spring + ANPR đang hoạt động" : "Có dịch vụ chưa sẵn sàng";
            _connection.ForeColor = checks.All(x => x) ? Color.SeaGreen : Color.Firebrick;
            if(checks.All(x=>x)) await RefreshMapAsync();
        }
        catch (Exception ex) { ShowError(ex); }
    }

    private async Task EntryAsync()
    {
        try
        {
            ConfigureApis();
            var response = await _parkingApi.EntryAsync(Request(_entryPlate.Text, _entryCard.Text));
            ShowParkingResult(response);
            if(!response.Warning || _manual.Checked) AutoOpenBarrier("Xe vào đã được xác nhận");
            await RefreshMapAsync();
            _exitPlate.Text = response.PlateNumber;
            _exitCard.Text = response.CardCode;
            _entryGuestFaceBase64 = "";

            if (!response.Warning || _manual.Checked)
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
            string card = entry ? _entryCard.Text : _exitCard.Text;
            if (string.IsNullOrWhiteSpace(plate) && string.IsNullOrWhiteSpace(card))
            {
                _residentInfo.Text = "Nhập hoặc nhận diện biển số để tra cứu";
                _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
                return;
            }
            ResidentLookupResponse resident = await _parkingApi.LookupResidentAsync(plate, card);
            ComboBox combo = entry ? _entryMember : _exitMember;
            combo.DataSource = resident.AuthorizedMembers?.ToList() ?? new List<AuthorizedMemberResponse>();
            if(!entry) _exitGuestFacePath=resident.GuestEntryFaceImagePath ?? "";
            if(entry){_entryFaceVerified=false;_entryFaceSimilarity=null;_entryVerifiedMemberId=null;}else{_exitFaceVerified=false;_exitFaceSimilarity=null;_exitVerifiedMemberId=null;}
            _residentInfo.BackColor = resident.Registered && resident.CardMatched
                ? Color.FromArgb(234, 245, 239) : Color.FromArgb(255, 247, 230);
            string pass = resident.PassType switch
            {
                "MONTHLY" => resident.MonthlyValid
                    ? $"Gói 30 ngày - hạn {resident.ValidUntil:dd/MM/yyyy}"
                    : "Gói 30 ngày không còn hiệu lực",
                "PER_VISIT" => "Vé lượt",
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
            _lastPreview = await _parkingApi.PreviewExitAsync(Request(_exitPlate.Text, _exitCard.Text));
            ShowParkingResult(_lastPreview);
        }
        catch (Exception ex) { ShowError(ex); }
    }

    private async Task ConfirmExitAsync()
    {
        try
        {
            if (_lastPreview == null || !_lastPreview.PlateNumber.Equals(NormalizePlate(_exitPlate.Text), StringComparison.OrdinalIgnoreCase))
            {
                MessageBox.Show("Hãy bấm Xem trước phí trước khi xác nhận.", "Chưa xem trước", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            ConfigureApis();
            var response = await _parkingApi.ConfirmExitAsync(Request(_exitPlate.Text, _exitCard.Text));
            ShowParkingResult(response);
            if(!response.Warning || _manual.Checked) AutoOpenBarrier("Xe ra đã được xác nhận");
            await RefreshMapAsync();
            _lastPreview = null;

            if (!response.Warning || _manual.Checked)
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
            SetBusy(true, "Đang chụp và xác thực khuôn mặt...");
            var combo=entry?_entryMember:_exitMember;
            string path;
            if(combo.SelectedItem is AuthorizedMemberResponse member){if(!member.FaceImageAvailable)throw new InvalidOperationException("Thành viên chưa có ảnh đăng ký");path=member.RegistrationFaceImagePath;}
            else if(!entry && !string.IsNullOrWhiteSpace(_exitGuestFacePath)) path=_exitGuestFacePath;
            else throw new InvalidOperationException(entry?"Cư dân: hãy chọn thành viên. Khách: dùng nút chụp ảnh khách vãng lai.":"Không tìm thấy ảnh khuôn mặt lúc vào");
            var response=await _anprApi.VerifyCameraAsync(_parkingUrl.Text.TrimEnd('/')+path);
            bool pass=response.Decision=="PASS";
            if(entry){_entryFaceVerified=pass;_entryFaceSimilarity=response.Similarity;_entryVerifiedMemberId=(combo.SelectedItem as AuthorizedMemberResponse)?.Id;}else{_exitFaceVerified=pass;_exitFaceSimilarity=response.Similarity;_exitVerifiedMemberId=(combo.SelectedItem as AuthorizedMemberResponse)?.Id;}
            if(!string.IsNullOrWhiteSpace(response.RealtimeImageBase64)){using var ms=new MemoryStream(Convert.FromBase64String(response.RealtimeImageBase64));using var img=Image.FromStream(ms);_preview.Image?.Dispose();_preview.Image=new Bitmap(img);}
            _result.Text=$"Xác thực khuôn mặt: {DisplayFaceDecision(response.Decision)}\r\nĐiểm tương đồng SFace: {response.Similarity:0.000}\r\nNgưỡng cho phép: từ {response.MatchThreshold:0.000}\r\n{response.Message}";
            _result.BackColor=pass?Color.Honeydew:Color.MistyRose;
        } catch(Exception ex){ShowError(ex);}
        finally { SetBusy(false, "Chọn ảnh hoặc video để nhận dạng tự động"); }
    }

    private ParkingRequest Request(string plate, string card)
    {
        bool entry=_tabs.SelectedIndex==0; var selected=(entry?_entryMember:_exitMember).SelectedItem as AuthorizedMemberResponse;
        bool verified=entry?_entryFaceVerified:_exitFaceVerified;long? verifiedId=entry?_entryVerifiedMemberId:_exitVerifiedMemberId;
        if(selected != null && selected.Id != verifiedId) verified=false;
        return new(plate,card,_detectedType,_manual.Checked,selected?.Id,verified,entry?_entryFaceSimilarity:_exitFaceSimilarity,entry?_entryGuestFaceBase64:"");
    }

    private async Task CaptureGuestAsync(){try{ConfigureApis();var r=await _anprApi.CaptureCameraAsync();_entryGuestFaceBase64="data:image/jpeg;base64,"+r.RealtimeImageBase64;ShowCapturedFace(r);_result.Text="Ảnh khách lúc vào đã chụp và sẽ lưu tạm cùng lượt xe.";}catch(Exception ex){ShowError(ex);}}
    private void ShowCapturedFace(FaceVerificationResponse r){if(string.IsNullOrWhiteSpace(r.RealtimeImageBase64))return;using var ms=new MemoryStream(Convert.FromBase64String(r.RealtimeImageBase64));using var img=Image.FromStream(ms);_preview.Image?.Dispose();_preview.Image=new Bitmap(img);}
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
                slot.SlotClicked += async (_, s) =>
                {
                    using var dlg = new SlotDetailDialog(_parkingApi, s);
                    if (dlg.ShowDialog(this) == DialogResult.OK)
                    {
                        await RefreshMapAsync();
                    }
                };
                _mapPanel.Controls.Add(slot);
            }
            _mapPanel.ResumeLayout();
        }
        catch { }
    }
    private void SetBarrier(bool open,string reason){_barrierOpen=open;_barrierState.Text=open?"BARRIER: ĐANG MỞ":"BARRIER: ĐANG ĐÓNG";_barrierState.ForeColor=open?Color.SeaGreen:Color.Firebrick;_barrierState.AccessibleDescription=reason;}
    private async void AutoOpenBarrier(string reason){SetBarrier(true,reason);await Task.Delay(5000);if(_barrierOpen)SetBarrier(false,"Tự đóng sau 5 giây");}
    private void ConfigureApis() { _parkingApi.BaseUrl = _parkingUrl.Text.Trim(); _anprApi.BaseUrl = _anprUrl.Text.Trim(); }

    private void ShowParkingResult(ParkingResponse r)
    {
        _result.BackColor = r.Warning ? Color.FromArgb(255, 247, 230) : Color.FromArgb(234, 245, 239);
        _result.ForeColor = r.Warning ? Color.DarkGoldenrod : Color.DarkGreen;
        _result.Text = $"{r.Message}\r\n\r\nMã lượt: {r.SessionId}\r\nBiển số: {r.PlateNumber}\r\nLoại xe: {DisplayVehicleType(r.VehicleType)}\r\nChủ xe: {r.OwnerName}\r\nThẻ: {r.CardCode}\r\nTrạng thái: {r.Status}\r\nGiờ vào: {r.EntryTime:dd/MM/yyyy HH:mm:ss}\r\nPhí: {r.Fee:N0} đ";
    }

    private void ClearEntryForm()
    {
        _entryPlate.Text = "";
        _entryCard.Text = "";
        _vehicleType.Text = "UNKNOWN";
        _preview.Image?.Dispose();
        _preview.Image = null;
        _result.Text = "";
        _result.BackColor = SystemColors.Control;
        _aiStatus.Text = "Chọn ảnh hoặc video để nhận dạng tự động";
        _aiStatus.ForeColor = Color.DimGray;
        _residentInfo.Text = "Chưa tra cứu cư dân";
        _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
        _entryMember.DataSource = null;
        _entryFaceVerified = false;
        _entryFaceSimilarity = null;
        _entryVerifiedMemberId = null;
        _entryGuestFaceBase64 = "";
    }

    private void ClearExitForm()
    {
        _exitPlate.Text = "";
        _exitCard.Text = "";
        _vehicleType.Text = "UNKNOWN";
        _preview.Image?.Dispose();
        _preview.Image = null;
        _result.Text = "";
        _result.BackColor = SystemColors.Control;
        _aiStatus.Text = "Chọn ảnh hoặc video để nhận dạng tự động";
        _aiStatus.ForeColor = Color.DimGray;
        _residentInfo.Text = "Chưa tra cứu cư dân";
        _residentInfo.BackColor = Color.FromArgb(245, 247, 250);
        _exitMember.DataSource = null;
        _exitFaceVerified = false;
        _exitFaceSimilarity = null;
        _exitVerifiedMemberId = null;
        _exitGuestFacePath = "";
    }

    private void ShowError(Exception ex)
    {
        string message = ex switch
        {
            HttpRequestException => "Không kết nối được đến dịch vụ.\r\n\r\nNguyên nhân có thể: Spring Web hoặc ANPR chưa chạy, sai địa chỉ/cổng, hoặc dịch vụ vừa bị tắt.\r\n\r\nCách xử lý: chạy run-all-laragon.cmd, chờ các dịch vụ khởi động xong rồi bấm Kiểm tra.",
            TaskCanceledException => "Dịch vụ phản hồi quá thời gian cho phép.\r\n\r\nNếu đang nhận dạng video hoặc mở camera, hãy kiểm tra camera và thử lại. Nếu không, hãy khởi động lại dịch vụ ANPR.",
            System.Text.Json.JsonException => "Máy chủ trả về dữ liệu không đúng định dạng. Có thể đang chạy phiên bản dịch vụ cũ.\r\n\r\nHãy đóng các cửa sổ Parking Web/ANPR cũ và chạy lại run-all-laragon.cmd.",
            _ => ex.Message
        };
        _result.BackColor = Color.MistyRose;
        _result.ForeColor = Color.Firebrick;
        _result.Text = "KHÔNG THỂ THỰC HIỆN\r\n\r\n" + message;
        _aiStatus.Text = "Nhận dạng thất bại - có thể nhập biển số thủ công";
        _aiStatus.ForeColor = Color.Firebrick;
    }

    private void ShowLocalImage(string path)
    {
        _preview.Image?.Dispose();
        using var original = Image.FromFile(path);
        _preview.Image = new Bitmap(original);
    }

    private void SetBusy(bool busy, string text) { UseWaitCursor = busy; _aiStatus.Text = text; _tabs.Enabled = !busy; }
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
