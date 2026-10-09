namespace ParkingGateDesktop;

public sealed class SlotDetailDialog : Form
{
    private readonly ParkingApiClient _api;
    private readonly ParkingSlotResponse _slot;

    private readonly ComboBox _cboResidentVehicles = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 480 };
    private readonly ComboBox _cboRecentSessions = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 480 };

    private readonly TextBox _txtBorrowPlate = new() { Width = 200, CharacterCasing = CharacterCasing.Upper, Font = new Font("Segoe UI", 10f, FontStyle.Bold) };
    private readonly ComboBox _cboBorrowHours = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 140 };
    private readonly TextBox _txtBorrowNotes = new() { Width = 480 };

    private readonly Label _lblInfoAssigned = new() { AutoSize = true, Font = new Font("Segoe UI", 10f), ForeColor = Color.FromArgb(30, 41, 59), Margin = new Padding(0, 6, 0, 8) };
    private readonly Label _lblInfoOccupied = new() { AutoSize = true, Font = new Font("Segoe UI", 10f), ForeColor = Color.FromArgb(30, 41, 59), Margin = new Padding(0, 0, 0, 8) };
    private readonly Label _lblInfoBorrowed = new() { AutoSize = true, Font = new Font("Segoe UI", 10f), ForeColor = Color.FromArgb(30, 41, 59), Margin = new Padding(0, 0, 0, 4) };

    public SlotDetailDialog(ParkingApiClient api, ParkingSlotResponse slot)
    {
        _api = api;
        _slot = slot;

        Text = $"Chi tiết & Điều phối vị trí {_slot.SlotCode}";
        Size = new Size(680, 720);
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        BackColor = Color.White;

        BuildUi();
        Shown += async (_, _) => await LoadDataAsync();
    }

    private void BuildUi()
    {
        var mainLayout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            RowCount = 3,
            ColumnCount = 1,
            Padding = new Padding(16)
        };
        mainLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
        mainLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 195));
        mainLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

        // 1. Header
        var header = new Panel { Dock = DockStyle.Fill, BackColor = Color.FromArgb(23, 54, 93), Padding = new Padding(12) };
        var title = new Label
        {
            Text = $"VỊ TRÍ {_slot.SlotCode} ({_slot.Floor ?? "Tầng B1"} - {_slot.ZoneName})",
            Font = new Font("Segoe UI", 13f, FontStyle.Bold),
            ForeColor = Color.White,
            Dock = DockStyle.Top,
            Height = 28
        };
        var subtitle = new Label
        {
            Text = $"Trạng thái: {_slot.StatusDescription ?? _slot.Status} | Loại xe: {(_slot.AllowedVehicleType == "MOTORBIKE" ? "Xe máy" : "Ô tô")}",
            Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
            ForeColor = Color.FromArgb(220, 235, 252),
            Dock = DockStyle.Top,
            Height = 22
        };
        header.Controls.Add(subtitle);
        header.Controls.Add(title);
        mainLayout.Controls.Add(header, 0, 0);

        // 2. Info Summary Box (Rộng rãi, thoáng mắt, không bị scroll hay cắt chữ)
        var infoBox = new GroupBox
        {
            Text = "Thông tin thực tế ô đỗ",
            Dock = DockStyle.Fill,
            Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
            ForeColor = Color.FromArgb(23, 54, 93),
            Padding = new Padding(14, 10, 14, 10)
        };
        var infoFlow = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = false
        };

        _lblInfoAssigned.Text = !string.IsNullOrWhiteSpace(_slot.AssignedPlate)
            ? $"• Xe cấp cố định: {_slot.AssignedPlate} (Chủ xe: {_slot.AssignedOwnerName ?? "-"} | Căn: {_slot.AssignedApartment ?? "-"} | SĐT: {_slot.AssignedOwnerPhone ?? "-"})"
            : "• Xe cấp cố định: (Chưa cấp cho cư dân nào)";

        _lblInfoOccupied.Text = !string.IsNullOrWhiteSpace(_slot.OccupiedPlate)
            ? $"• Xe đang đỗ: {_slot.OccupiedPlate}" + (_slot.OccupiedEntryTime.HasValue ? $" (Vào lúc: {_slot.OccupiedEntryTime.Value:HH:mm:ss dd/MM})" : "")
            : "• Xe đang đỗ: (Hiện tại ô đang trống)";

        _lblInfoBorrowed.Text = !string.IsNullOrWhiteSpace(_slot.BorrowedPlate)
            ? $"• Xe mượn đỗ nhờ: {_slot.BorrowedPlate}" + (_slot.BorrowedUntil.HasValue ? $" (Hạn đến: {_slot.BorrowedUntil.Value:HH:mm dd/MM})" : "") + (_slot.Overdue ? " ⚠️ ĐÃ QUÁ HẠN!" : "")
            : "• Xe mượn đỗ nhờ: (Không có)";

        if (_slot.Overdue) _lblInfoBorrowed.ForeColor = Color.Firebrick;

        infoFlow.Controls.Add(_lblInfoAssigned);
        infoFlow.Controls.Add(_lblInfoOccupied);
        infoFlow.Controls.Add(_lblInfoBorrowed);
        infoBox.Controls.Add(infoFlow);
        mainLayout.Controls.Add(infoBox, 0, 1);

        // 3. Action Tabs
        var tabs = new TabControl { Dock = DockStyle.Fill, Font = new Font("Segoe UI", 9.5f) };
        tabs.TabPages.Add(BuildResidentTab());
        tabs.TabPages.Add(BuildBorrowTab());
        tabs.TabPages.Add(BuildGuestTab());
        tabs.TabPages.Add(BuildManagementTab());

        mainLayout.Controls.Add(tabs, 0, 2);
        Controls.Add(mainLayout);
    }

    private TabPage BuildResidentTab()
    {
        var page = new TabPage("Gán xe cư dân") { Padding = new Padding(14) };
        var flow = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false };

        flow.Controls.Add(new Label { Text = "Chọn xe cư dân chưa có vị trí đỗ (Quy tắc 1 xe - 1 ô):", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold) });
        flow.Controls.Add(_cboResidentVehicles);

        var hint = new Label
        {
            Text = "Mỗi xe cư dân chỉ được gán tối đa 1 ô cố định. Hệ thống sẽ chặn nếu xe đã được gán tại ô khác.",
            ForeColor = Color.DimGray,
            Font = new Font("Segoe UI", 8.5f, FontStyle.Italic),
            AutoSize = true,
            Margin = new Padding(0, 6, 0, 14)
        };
        flow.Controls.Add(hint);

        var btnPanel = new FlowLayoutPanel { AutoSize = true };
        var btnAssign = CreateBtn("LƯU GÁN XE", Color.FromArgb(46, 116, 181), async (_, _) => await AssignResidentAsync());
        var btnUnassign = CreateBtn("HỦY GÁN (TRẢ VỀ VỊ TRÍ TRỐNG)", Color.FromArgb(108, 122, 137), async (_, _) => await UnassignResidentAsync());
        btnPanel.Controls.Add(btnAssign);
        btnPanel.Controls.Add(btnUnassign);
        flow.Controls.Add(btnPanel);

        page.Controls.Add(flow);
        return page;
    }

    private TabPage BuildBorrowTab()
    {
        var page = new TabPage("Đỗ nhờ (Hàng xóm)") { Padding = new Padding(14) };
        var flow = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false };

        flow.Controls.Add(new Label { Text = "Biển số xe đỗ nhờ (Xe B):", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold) });
        flow.Controls.Add(_txtBorrowPlate);

        flow.Controls.Add(new Label { Text = "Thời gian cho phép đậu nhờ:", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold), Margin = new Padding(0, 8, 0, 0) });
        _cboBorrowHours.Items.AddRange(new object[] { "1 giờ", "2 giờ", "4 giờ", "8 giờ", "12 giờ", "24 giờ", "48 giờ" });
        _cboBorrowHours.SelectedIndex = 1;
        flow.Controls.Add(_cboBorrowHours);

        flow.Controls.Add(new Label { Text = "Ghi chú:", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold), Margin = new Padding(0, 8, 0, 0) });
        flow.Controls.Add(_txtBorrowNotes);

        var hint = new Label
        {
            Text = "⚠️ Nếu xe đỗ nhờ vượt quá thời gian thiết lập, hệ thống sẽ tự động đổi màu ô sang cảnh báo đỏ QUÁ GIỜ.",
            ForeColor = Color.DarkGoldenrod,
            Font = new Font("Segoe UI", 8.5f, FontStyle.Italic),
            AutoSize = true,
            Margin = new Padding(0, 8, 0, 14)
        };
        flow.Controls.Add(hint);

        var btnPanel = new FlowLayoutPanel { AutoSize = true };
        var btnConfirm = CreateBtn("XÁC NHẬN CHO ĐỖ NHỜ", Color.FromArgb(230, 126, 34), async (_, _) => await SetupBorrowAsync());
        var btnCancel = CreateBtn("HỦY THIẾT LẬP ĐỖ NHỜ", Color.FromArgb(192, 57, 43), async (_, _) => await CancelBorrowAsync());
        btnPanel.Controls.Add(btnConfirm);
        if (!string.IsNullOrWhiteSpace(_slot.BorrowedPlate)) btnPanel.Controls.Add(btnCancel);
        flow.Controls.Add(btnPanel);

        page.Controls.Add(flow);
        return page;
    }

    private TabPage BuildGuestTab()
    {
        var page = new TabPage("Điều phối xe vừa vào") { Padding = new Padding(14) };
        var flow = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false };

        flow.Controls.Add(new Label { Text = "Chọn xe vừa quét camera vào trạm (chưa có ô):", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold) });
        flow.Controls.Add(_cboRecentSessions);

        var hint = new Label
        {
            Text = "Danh sách này tự động lấy các lượt xe vừa nhận dạng lúc vào từ màn hình trạm barrier.",
            ForeColor = Color.DimGray,
            Font = new Font("Segoe UI", 8.5f, FontStyle.Italic),
            AutoSize = true,
            Margin = new Padding(0, 8, 0, 14)
        };
        flow.Controls.Add(hint);

        var btnDispatch = CreateBtn("ĐIỀU PHỐI VÀO Ô NÀY", Color.FromArgb(46, 116, 181), async (_, _) => await DispatchGuestAsync());
        flow.Controls.Add(btnDispatch);

        page.Controls.Add(flow);
        return page;
    }

    private TabPage BuildManagementTab()
    {
        var page = new TabPage("Khóa / Giải phóng") { Padding = new Padding(14) };
        var flow = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false };

        bool isBlocked = _slot.StatusOverride == "BLOCKED";
        var btnBlock = CreateBtn(isBlocked ? "✅ MỞ LẠI VỊ TRÍ ĐỖ (BỎ CẤM ĐỖ)" : "🚫 ĐẶT LÀM Ô CẤM ĐỖ / ĐANG BẢO TRÌ",
            isBlocked ? Color.FromArgb(39, 174, 96) : Color.FromArgb(108, 122, 137),
            async (_, _) => await ToggleBlockAsync(!isBlocked));
        flow.Controls.Add(btnBlock);

        var btnRelease = CreateBtn("🗑️ GIẢI PHÓNG Ô (XÓA TOÀN BỘ GÁN XE & ĐỖ NHỜ)", Color.FromArgb(192, 57, 43), async (_, _) => await ReleaseSlotAsync());
        btnRelease.Margin = new Padding(0, 16, 0, 0);
        flow.Controls.Add(btnRelease);

        page.Controls.Add(flow);
        return page;
    }

    private async Task LoadDataAsync()
    {
        try
        {
            // 1. Xe cư dân chưa có ô
            var unassignedVehicles = await _api.GetUnassignedVehiclesAsync();
            _cboResidentVehicles.Items.Clear();
            _cboResidentVehicles.Items.Add("-- Chọn xe cư dân --");
            foreach (var v in unassignedVehicles)
            {
                _cboResidentVehicles.Items.Add(new ComboBoxItem(v.Id, $"{v.PlateNumber} ({v.VehicleType})"));
            }
            _cboResidentVehicles.SelectedIndex = 0;

            // 2. Xe vừa vào trạm chưa có ô
            var recentSessions = await _api.GetRecentUnassignedSessionsAsync();
            _cboRecentSessions.Items.Clear();
            _cboRecentSessions.Items.Add("-- Chọn xe vừa vào trạm --");
            foreach (var s in recentSessions)
            {
                string slot = string.IsNullOrWhiteSpace(s.SlotCode) ? "Chưa có ô" : $"Đang ở ô {s.SlotCode}";
                _cboRecentSessions.Items.Add(new ComboBoxItem(s.SessionId, $"{s.PlateNumber} | {slot}"));
            }
            _cboRecentSessions.SelectedIndex = 0;

            // Điền trước thông tin đỗ nhờ nếu có
            if (!string.IsNullOrWhiteSpace(_slot.BorrowedPlate))
            {
                _txtBorrowPlate.Text = _slot.BorrowedPlate;
                _txtBorrowNotes.Text = _slot.BorrowNotes ?? "";
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show("Lỗi tải dữ liệu: " + ex.Message, "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private async Task AssignResidentAsync()
    {
        if (_cboResidentVehicles.SelectedItem is ComboBoxItem item)
        {
            try
            {
                await _api.AssignSlotAsync(_slot.Id, item.Id);
                MessageBox.Show("Gán xe cư dân thành công!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
                DialogResult = DialogResult.OK;
                Close();
            }
            catch (Exception ex) { MessageBox.Show(ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error); }
        }
        else
        {
            MessageBox.Show("Vui lòng chọn xe cư dân cần gán!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private async Task UnassignResidentAsync()
    {
        try
        {
            await _api.AssignSlotAsync(_slot.Id, null);
            MessageBox.Show("Đã hủy gán xe cho ô đỗ này!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Exception ex) { MessageBox.Show(ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error); }
    }

    private async Task SetupBorrowAsync()
    {
        string plate = _txtBorrowPlate.Text.Trim();
        if (plate.Length < 5)
        {
            MessageBox.Show("Vui lòng nhập biển số xe đỗ nhờ hợp lệ!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        int hours = _cboBorrowHours.SelectedIndex switch
        {
            0 => 1,
            1 => 2,
            2 => 4,
            3 => 8,
            4 => 12,
            5 => 24,
            6 => 48,
            _ => 2
        };

        try
        {
            await _api.BorrowSlotAsync(_slot.Id, plate, hours, _txtBorrowNotes.Text.Trim());
            MessageBox.Show($"Đã thiết lập xe {plate} đỗ nhờ trong {hours} giờ thành công!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Exception ex) { MessageBox.Show(ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error); }
    }

    private async Task CancelBorrowAsync()
    {
        try
        {
            await _api.CancelBorrowAsync(_slot.Id);
            MessageBox.Show("Đã hủy trạng thái đỗ nhờ!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Exception ex) { MessageBox.Show(ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error); }
    }

    private async Task DispatchGuestAsync()
    {
        if (_cboRecentSessions.SelectedItem is ComboBoxItem item)
        {
            try
            {
                await _api.DispatchSessionAsync(_slot.Id, item.Id);
                MessageBox.Show("Điều phối xe vào ô thành công!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
                DialogResult = DialogResult.OK;
                Close();
            }
            catch (Exception ex) { MessageBox.Show(ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error); }
        }
        else
        {
            MessageBox.Show("Vui lòng chọn lượt xe vừa vào cần điều phối!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private async Task ToggleBlockAsync(bool block)
    {
        try
        {
            await _api.UpdateSlotStatusAsync(_slot.Id, block ? "BLOCKED" : "NORMAL");
            MessageBox.Show(block ? "Đã đặt ô ở chế độ CẤM ĐỖ!" : "Đã mở lại ô đỗ hoạt động bình thường!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Exception ex) { MessageBox.Show(ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error); }
    }

    private async Task ReleaseSlotAsync()
    {
        if (MessageBox.Show("Bạn có chắc chắn muốn giải phóng ô đỗ này về trạng thái trống hoàn toàn?", "Xác nhận", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
        {
            try
            {
                await _api.ReleaseSlotAsync(_slot.Id);
                MessageBox.Show("Đã giải phóng ô đỗ thành công!", "Thông báo", MessageBoxButtons.OK, MessageBoxIcon.Information);
                DialogResult = DialogResult.OK;
                Close();
            }
            catch (Exception ex) { MessageBox.Show(ex.Message, "Lỗi", MessageBoxButtons.OK, MessageBoxIcon.Error); }
        }
    }

    private static Button CreateBtn(string text, Color bg, EventHandler onClick)
    {
        var btn = new Button
        {
            Text = text,
            AutoSize = true,
            Padding = new Padding(12, 7, 12, 7),
            BackColor = bg,
            ForeColor = Color.White,
            FlatStyle = FlatStyle.Flat,
            Font = new Font("Segoe UI", 9f, FontStyle.Bold),
            Cursor = Cursors.Hand,
            Margin = new Padding(0, 4, 10, 4)
        };
        btn.FlatAppearance.BorderSize = 0;
        btn.Click += onClick;
        return btn;
    }

    private sealed class ComboBoxItem
    {
        public long Id { get; }
        public string Text { get; }
        public ComboBoxItem(long id, string text) { Id = id; Text = text; }
        public override string ToString() => Text;
    }
}
