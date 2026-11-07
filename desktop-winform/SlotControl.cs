namespace ParkingGateDesktop;

public sealed class SlotControl : UserControl
{
    private readonly Label _zoneHeader = new()
    {
        Dock = DockStyle.Top,
        Height = 24,
        Font = LumiTheme.AppFont(8f),
        TextAlign = ContentAlignment.MiddleCenter,
        BackColor = Color.FromArgb(32, 0, 0, 0),
        ForeColor = Color.White
    };

    private readonly Label _title = new()
    {
        Dock = DockStyle.Top,
        Height = 30,
        TextAlign = ContentAlignment.MiddleCenter,
        Font = LumiTheme.AppFont(12f, FontStyle.Bold),
        ForeColor = Color.White
    };

    private readonly Label _plate = new()
    {
        Dock = DockStyle.Top,
        Height = 26,
        TextAlign = ContentAlignment.MiddleCenter,
        Font = LumiTheme.AppFont(9.3f, FontStyle.Bold),
        BackColor = Color.FromArgb(35, 0, 0, 0),
        ForeColor = Color.White
    };

    private readonly Label _status = new()
    {
        Dock = DockStyle.Fill,
        TextAlign = ContentAlignment.MiddleCenter,
        Font = LumiTheme.AppFont(8f, FontStyle.Italic),
        ForeColor = Color.White
    };

    public ParkingSlotResponse? Slot { get; private set; }
    public event EventHandler<ParkingSlotResponse>? SlotClicked;

    public SlotControl()
    {
        Size = new Size(164, 112);
        Margin = new Padding(7);
        BorderStyle = BorderStyle.None;
        Cursor = Cursors.Hand;
        Padding = new Padding(1);
        LumiTheme.Round(this, 14);

        Controls.Add(_status);
        Controls.Add(_plate);
        Controls.Add(_title);
        Controls.Add(_zoneHeader);

        // Bắt sự kiện click trên toàn bộ control và các nhãn con
        Click += (_, _) => OnClicked();
        _zoneHeader.Click += (_, _) => OnClicked();
        _title.Click += (_, _) => OnClicked();
        _plate.Click += (_, _) => OnClicked();
        _status.Click += (_, _) => OnClicked();
    }

    private void OnClicked()
    {
        if (Slot != null)
        {
            SlotClicked?.Invoke(this, Slot);
        }
    }

    public void Update(ParkingSlotResponse slot)
    {
        Slot = slot;
        string renderKey = string.Join("|", slot.SlotCode, slot.ZoneName, slot.AllowedVehicleType,
            slot.OccupiedPlate, slot.BorrowedPlate, slot.AssignedPlate, slot.Status, slot.StatusDescription, slot.Overdue);
        if (Tag is string previousKey && previousKey == renderKey) return;
        Tag = renderKey;

        string vIcon = slot.AllowedVehicleType switch { "MOTORBIKE" => "🛵", "CAR" => "🚗", _ => "🅿" };
        _zoneHeader.Text = $"{vIcon} {slot.ZoneName}";
        _title.Text = slot.SlotCode;

        if (!string.IsNullOrWhiteSpace(slot.OccupiedPlate))
        {
            _plate.Text = $"Đỗ: {slot.OccupiedPlate}";
        }
        else if (!string.IsNullOrWhiteSpace(slot.BorrowedPlate))
        {
            _plate.Text = $"Nhờ: {slot.BorrowedPlate}";
        }
        else if (!string.IsNullOrWhiteSpace(slot.AssignedPlate))
        {
            _plate.Text = $"Cấp: {slot.AssignedPlate}";
        }
        else
        {
            _plate.Text = "Trống";
        }

        if (slot.Overdue)
        {
            _status.Text = "⚠️ QUÁ GIỜ ĐỖ NHỜ";
            _status.Font = LumiTheme.AppFont(8f, FontStyle.Bold);
            _status.ForeColor = Color.FromArgb(255, 238, 160);
        }
        else
        {
            _status.Text = slot.StatusDescription ?? slot.Status;
            _status.Font = LumiTheme.AppFont(8f);
            _status.ForeColor = Color.White;
        }

        BackColor = slot.Status switch
        {
            "AVAILABLE" => LumiTheme.Good,
            "RESERVED_EMPTY" => LumiTheme.Primary,
            "OCCUPIED_VALID" => LumiTheme.Danger,
            "OCCUPIED_BORROWED" => Color.FromArgb(180, 83, 9),
            "RESERVED_BORROWED" => Color.FromArgb(194, 101, 18),
            "OVERDUE_ALERT" => Color.FromArgb(117, 35, 30),
            "OCCUPIED_MISMATCH" => Color.FromArgb(139, 91, 15),
            "BLOCKED" => Color.FromArgb(75, 85, 105),
            _ => LumiTheme.Muted
        };
    }
}
