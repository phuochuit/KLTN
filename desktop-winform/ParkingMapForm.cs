namespace ParkingGateDesktop;

public sealed class ParkingMapForm : Form
{
    private readonly ParkingApiClient _api;
    private readonly BufferedFlowLayoutPanel _panel = new() { Dock = DockStyle.Fill, AutoScroll = true, Padding = new Padding(18), BackColor = LumiTheme.SurfaceSoft };

    private readonly ComboBox _cboFloor = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 150 };
    private readonly ComboBox _cboZone = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 150 };
    private readonly ComboBox _cboVehicleType = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 120 };

    private readonly Label _lblSummary = new() { AutoSize = true, Font = new Font("Segoe UI", 9.5f, FontStyle.Bold), ForeColor = Color.FromArgb(23, 54, 93), Margin = new Padding(10, 8, 10, 0) };
    private readonly Label _lblPage = new() { AutoSize = true, Margin = new Padding(10, 12, 8, 0), ForeColor = LumiTheme.Muted };
    private readonly Button _btnPreviousPage = new() { Text = "Trang trước", AutoSize = true };
    private readonly Button _btnNextPage = new() { Text = "Trang sau", AutoSize = true };
    private readonly Button _btnRefresh = new() { Text = "Làm mới", AutoSize = false };
    private const int PageSize = 48;
    private int _currentPage = 1;

    private List<ParkingSlotResponse> _allSlots = new();
    private bool _updatingFilters;
    private readonly System.Windows.Forms.Timer _resizeTimer = new() { Interval = 150 };

    private readonly Action _mapUpdatedHandler;

    public ParkingMapForm(ParkingApiClient api)
    {
        _api = api;
        Text = "LumiPark - Sơ đồ bãi đỗ xe";
        Size = new Size(1180, 780);
        MinimumSize = new Size(760, 560);
        StartPosition = FormStartPosition.CenterParent;

        BuildUi();
        LumiTheme.Apply(this);
        _btnRefresh.Dock = DockStyle.Fill;
        _btnRefresh.Margin = Padding.Empty;
        _btnRefresh.Padding = new Padding(12, 6, 12, 6);
        _btnRefresh.MinimumSize = new Size(0, 44);
        Shown += async (_, _) => await ReloadAsync();
        _resizeTimer.Tick += (_, _) =>
        {
            _resizeTimer.Stop();
            ResizeSlots(_panel);
        };
        _panel.Resize += (_, _) =>
        {
            _resizeTimer.Stop();
            _resizeTimer.Start();
        };

        _mapUpdatedHandler = () => {
            if (!this.IsDisposed && this.IsHandleCreated)
            {
                this.Invoke((MethodInvoker)async delegate {
                    try { await ReloadAsync(); } catch { }
                });
            }
        };
        MainForm.OnSharedMapUpdated += _mapUpdatedHandler;
        this.FormClosed += (_, _) =>
        {
            MainForm.OnSharedMapUpdated -= _mapUpdatedHandler;
            _resizeTimer.Dispose();
        };
    }

    private void BuildUi()
    {
        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            RowCount = 4,
            ColumnCount = 1,
            Padding = new Padding(20),
            BackColor = LumiTheme.Canvas
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 68));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 66));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

        var heading = new Panel { Dock = DockStyle.Fill, BackColor = LumiTheme.Canvas };
        heading.Controls.Add(new Label
        {
            Text = "Sơ đồ bãi đỗ xe",
            AutoSize = true,
            Location = new Point(0, 2),
            Font = LumiTheme.AppFont(19, FontStyle.Bold),
            ForeColor = LumiTheme.Ink
        });
        heading.Controls.Add(new Label
        {
            Text = "Theo dõi vị trí trống và trạng thái từng ô đỗ",
            AutoSize = true,
            Location = new Point(2, 38),
            Font = LumiTheme.AppFont(9.5f),
            ForeColor = LumiTheme.Muted
        });

        var topBar = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(14, 7, 14, 7),
            BackColor = LumiTheme.Surface,
            WrapContents = true
        };
        LumiTheme.StyleCard(topBar, 14);

        _cboFloor.SelectedIndexChanged += (_, _) => ApplyFilters();
        topBar.Controls.Add(BuildFilterField("Tầng", _cboFloor, 190));

        _cboZone.SelectedIndexChanged += (_, _) => ApplyFilters();
        topBar.Controls.Add(BuildFilterField("Khu vực", _cboZone, 190));

        _cboVehicleType.Items.AddRange(new object[] { "Tất cả loại xe", "Ô tô", "Xe máy" });
        _cboVehicleType.SelectedIndex = 0;
        _cboVehicleType.SelectedIndexChanged += (_, _) => ApplyFilters();
        topBar.Controls.Add(BuildFilterField("Loại xe", _cboVehicleType, 170));

        _btnRefresh.Click += async (_, _) => await ReloadAsync();
        var refreshBlock = new TableLayoutPanel
        {
            ColumnCount = 1,
            RowCount = 2,
            Width = 150,
            Height = 68,
            Margin = new Padding(0, 0, 8, 0)
        };
        refreshBlock.RowStyles.Add(new RowStyle(SizeType.Absolute, 24));
        refreshBlock.RowStyles.Add(new RowStyle(SizeType.Absolute, 44));
        refreshBlock.Controls.Add(_btnRefresh, 0, 1);
        topBar.Controls.Add(refreshBlock);

        var summaryBar = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            BackColor = LumiTheme.Surface,
            Padding = new Padding(10, 2, 10, 2)
        };
        _btnPreviousPage.Click += (_, _) =>
        {
            if (_currentPage <= 1) return;
            _currentPage--;
            FilterAndRenderSlots();
        };
        _btnNextPage.Click += (_, _) =>
        {
            _currentPage++;
            FilterAndRenderSlots();
        };
        summaryBar.Controls.Add(_lblSummary);
        summaryBar.Controls.Add(_btnPreviousPage);
        summaryBar.Controls.Add(_lblPage);
        summaryBar.Controls.Add(_btnNextPage);
        LumiTheme.StyleCard(summaryBar, 12);

        LumiTheme.StyleCard(_panel, 16);
        root.Controls.Add(heading, 0, 0);
        root.Controls.Add(topBar, 0, 1);
        root.Controls.Add(summaryBar, 0, 2);
        root.Controls.Add(_panel, 0, 3);
        Controls.Add(root);
        void ApplyResponsiveLayout()
        {
            bool compact = ClientSize.Width < 1050;
            root.Padding = compact ? new Padding(12) : new Padding(20);
            root.RowStyles[0].Height = compact ? 62 : 68;
            root.RowStyles[1].Height = compact ? 148 : 82;
            root.RowStyles[2].Height = compact ? 82 : 48;
            _panel.Padding = compact ? new Padding(12) : new Padding(18);
        }
        Resize += (_, _) => ApplyResponsiveLayout();
        ApplyResponsiveLayout();
    }

    public async Task ReloadAsync()
    {
        try
        {
            _lblSummary.Text = "Đang tải sơ đồ bãi đỗ xe...";
            _panel.Controls.Clear();
            _panel.Controls.Add(new Label
            {
                Text = "Đang tải vị trí đỗ, vui lòng chờ...",
                AutoSize = true,
                ForeColor = LumiTheme.Muted,
                Font = LumiTheme.AppFont(10f, FontStyle.Bold),
                Margin = new Padding(16)
            });
            await Task.Yield();
            var rawSlots = await _api.GetSlotsAsync();
            _allSlots = rawSlots
                .OrderBy(s => GetSlotPrefix(s.SlotCode))
                .ThenBy(s => GetSlotNumber(s.SlotCode))
                .ToList();

            // Cập nhật các danh mục tầng & khu
            var floors = _allSlots.Select(s => s.Floor ?? "Tầng B1").Distinct().OrderBy(f => f).ToList();
            var zones = _allSlots.Select(s => s.ZoneName).Distinct().OrderBy(z => z).ToList();

            string currentFloor = _cboFloor.SelectedItem?.ToString() ?? "Tất cả các tầng";
            string currentZone = _cboZone.SelectedItem?.ToString() ?? "Tất cả các khu";

            _updatingFilters = true;
            try
            {
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
            }
            finally { _updatingFilters = false; }

            _currentPage = 1;
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

        int pageCount = Math.Max(1, (int)Math.Ceiling(filtered.Count / (double)PageSize));
        _currentPage = Math.Clamp(_currentPage, 1, pageCount);
        var pageItems = filtered.Skip((_currentPage - 1) * PageSize).Take(PageSize).ToList();
        _lblSummary.Text = $"Tổng: {_allSlots.Count} ô  •  Trống: {available}  •  Đang đỗ: {occupied}  •  Cư dân: {reserved}  •  Đỗ nhờ: {borrowed}" + (overdue > 0 ? $"  •  Quá giờ: {overdue}" : "");
        _lblPage.Text = $"Trang {_currentPage}/{pageCount}";
        _btnPreviousPage.Enabled = _currentPage > 1;
        _btnNextPage.Enabled = _currentPage < pageCount;

        var controls = new List<Control>(pageItems.Count);
        foreach (var slot in pageItems)
        {
            var control = new SlotControl();
            control.Update(slot);
            control.SlotClicked += (_, s) =>
            {
                using var dlg = new SlotDetailDialog(_api, s);
                if (dlg.ShowDialog(this) == DialogResult.OK)
                {
                    MainForm.NotifyMapUpdated();
                }
            };
            controls.Add(control);
        }
        if (controls.Count > 0) _panel.Controls.AddRange(controls.ToArray());

        ResizeSlots(_panel);
        _panel.ResumeLayout();
    }

    private void ApplyFilters()
    {
        if (_updatingFilters) return;
        _currentPage = 1;
        FilterAndRenderSlots();
    }

    private static Control BuildFilterField(string title, ComboBox combo, int width)
    {
        var block = new TableLayoutPanel
        {
            ColumnCount = 1,
            RowCount = 2,
            Width = width,
            Height = 68,
            Margin = new Padding(0, 0, 10, 0)
        };
        block.RowStyles.Add(new RowStyle(SizeType.Absolute, 24));
        block.RowStyles.Add(new RowStyle(SizeType.Absolute, 44));
        block.Controls.Add(new Label
        {
            Text = title,
            AutoSize = true,
            Font = LumiTheme.AppFont(9f, FontStyle.Bold),
            ForeColor = LumiTheme.Ink
        }, 0, 0);
        var frame = LumiTheme.InputFrame(combo, width);
        frame.Dock = DockStyle.Fill;
        frame.Margin = Padding.Empty;
        block.Controls.Add(frame, 0, 1);
        return block;
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
    private static string GetSlotPrefix(string? code)
    {
        if (string.IsNullOrEmpty(code)) return "";
        int i = 0;
        while (i < code.Length && !char.IsDigit(code[i])) i++;
        return code.Substring(0, i);
    }

    private static int GetSlotNumber(string? code)
    {
        if (string.IsNullOrEmpty(code)) return 0;
        int i = 0;
        while (i < code.Length && !char.IsDigit(code[i])) i++;
        int j = i;
        while (j < code.Length && char.IsDigit(code[j])) j++;
        if (i < j && int.TryParse(code.Substring(i, j - i), out int num)) return num;
        return 0;
    }
}

internal sealed class BufferedFlowLayoutPanel : FlowLayoutPanel
{
    public BufferedFlowLayoutPanel()
    {
        DoubleBuffered = true;
        SetStyle(ControlStyles.OptimizedDoubleBuffer | ControlStyles.AllPaintingInWmPaint, true);
    }
}
