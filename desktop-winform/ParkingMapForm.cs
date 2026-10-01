namespace ParkingGateDesktop;

public sealed class ParkingMapForm : Form
{
    private readonly ParkingApiClient _api;
    private readonly FlowLayoutPanel _panel = new() { Dock = DockStyle.Fill, AutoScroll = true, Padding = new Padding(12), BackColor = Color.FromArgb(244, 247, 251) };

    private readonly ComboBox _cboFloor = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 150 };
    private readonly ComboBox _cboZone = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 150 };
    private readonly ComboBox _cboVehicleType = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 120 };

    private readonly Label _lblSummary = new() { AutoSize = true, Font = new Font("Segoe UI", 9.5f, FontStyle.Bold), ForeColor = Color.FromArgb(23, 54, 93), Margin = new Padding(10, 8, 10, 0) };

    private List<ParkingSlotResponse> _allSlots = new();

    public ParkingMapForm(ParkingApiClient api)
    {
        _api = api;
        Text = "Sơ đồ và điều phối bãi đỗ xe - Quản trị thông minh";
        Size = new Size(1180, 780);
        StartPosition = FormStartPosition.CenterParent;

        BuildUi();
        Shown += async (_, _) => await ReloadAsync();
    }

    private void BuildUi()
    {
        var topBar = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            Height = 52,
            Padding = new Padding(10, 8, 10, 8),
            BackColor = Color.White,
            WrapContents = false
        };

        topBar.Controls.Add(new Label { Text = "Tầng:", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold), Margin = new Padding(0, 6, 4, 0) });
        _cboFloor.SelectedIndexChanged += (_, _) => FilterAndRenderSlots();
        topBar.Controls.Add(_cboFloor);

        topBar.Controls.Add(new Label { Text = "Khu vực:", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold), Margin = new Padding(12, 6, 4, 0) });
        _cboZone.SelectedIndexChanged += (_, _) => FilterAndRenderSlots();
        topBar.Controls.Add(_cboZone);

        topBar.Controls.Add(new Label { Text = "Loại xe:", AutoSize = true, Font = new Font("Segoe UI", 9f, FontStyle.Bold), Margin = new Padding(12, 6, 4, 0) });
        _cboVehicleType.Items.AddRange(new object[] { "Tất cả loại xe", "Ô tô", "Xe máy" });
        _cboVehicleType.SelectedIndex = 0;
        _cboVehicleType.SelectedIndexChanged += (_, _) => FilterAndRenderSlots();
        topBar.Controls.Add(_cboVehicleType);

        var btnRefresh = new Button
        {
            Text = "🔄 Làm mới",
            AutoSize = true,
            BackColor = Color.FromArgb(46, 116, 181),
            ForeColor = Color.White,
            FlatStyle = FlatStyle.Flat,
            Font = new Font("Segoe UI", 9f, FontStyle.Bold),
            Cursor = Cursors.Hand,
            Margin = new Padding(14, 2, 8, 0)
        };
        btnRefresh.FlatAppearance.BorderSize = 0;
        btnRefresh.Click += async (_, _) => await ReloadAsync();
        topBar.Controls.Add(btnRefresh);

        topBar.Controls.Add(_lblSummary);

        Controls.Add(_panel);
        Controls.Add(topBar);
    }

    public async Task ReloadAsync()
    {
        try
        {
            _allSlots = await _api.GetSlotsAsync();

            // Cập nhật các danh mục tầng & khu
            var floors = _allSlots.Select(s => s.Floor ?? "Tầng B1").Distinct().OrderBy(f => f).ToList();
            var zones = _allSlots.Select(s => s.ZoneName).Distinct().OrderBy(z => z).ToList();

            string currentFloor = _cboFloor.SelectedItem?.ToString() ?? "Tất cả các tầng";
            string currentZone = _cboZone.SelectedItem?.ToString() ?? "Tất cả các khu";

            _cboFloor.Items.Clear();
            _cboFloor.Items.Add("Tất cả các tầng");
            foreach (var f in floors) _cboFloor.Items.Add(f);
            int idxFloor = _cboFloor.Items.IndexOf(currentFloor);
            _cboFloor.SelectedIndex = idxFloor >= 0 ? idxFloor : 0;

            _cboZone.Items.Clear();
            _cboZone.Items.Add("Tất cả các khu");
            foreach (var z in zones) _cboZone.Items.Add(z);
            int idxZone = _cboZone.Items.IndexOf(currentZone);
            _cboZone.SelectedIndex = idxZone >= 0 ? idxZone : 0;

            FilterAndRenderSlots();
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "Không tải được sơ đồ", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private void FilterAndRenderSlots()
    {
        _panel.SuspendLayout();
        _panel.Controls.Clear();

        string selFloor = _cboFloor.SelectedItem?.ToString() ?? "Tất cả các tầng";
        string selZone = _cboZone.SelectedItem?.ToString() ?? "Tất cả các khu";
        int selVType = _cboVehicleType.SelectedIndex; // 0: Tất cả, 1: Ô tô, 2: Xe máy

        var filtered = _allSlots.Where(s =>
        {
            if (selFloor != "Tất cả các tầng" && (s.Floor ?? "Tầng B1") != selFloor) return false;
            if (selZone != "Tất cả các khu" && s.ZoneName != selZone) return false;
            if (selVType == 1 && s.AllowedVehicleType == "MOTORBIKE") return false;
            if (selVType == 2 && s.AllowedVehicleType != "MOTORBIKE") return false;
            return true;
        }).ToList();

        int available = _allSlots.Count(s => s.Status == "AVAILABLE");
        int occupied = _allSlots.Count(s => s.Status.StartsWith("OCCUPIED"));
        int reserved = _allSlots.Count(s => s.Status == "RESERVED_EMPTY");
        int borrowed = _allSlots.Count(s => !string.IsNullOrWhiteSpace(s.BorrowedPlate));
        int overdue = _allSlots.Count(s => s.Overdue);

        _lblSummary.Text = $"| Tổng: {_allSlots.Count} ô | Trống: {available} | Đang đỗ: {occupied} | Cư dân: {reserved} | Đỗ nhờ: {borrowed}" + (overdue > 0 ? $" | ⚠️ Quá giờ: {overdue}" : "");

        foreach (var slot in filtered)
        {
            var control = new SlotControl();
            control.Update(slot);
            control.SlotClicked += async (_, s) =>
            {
                using var dlg = new SlotDetailDialog(_api, s);
                if (dlg.ShowDialog(this) == DialogResult.OK)
                {
                    await ReloadAsync();
                }
            };
            _panel.Controls.Add(control);
        }

        _panel.ResumeLayout();
    }
}
