namespace ParkingGateDesktop;

public sealed class SlotControl : UserControl
{
    private readonly Label _zoneHeader = new()
    {
        Dock = DockStyle.Top,
        Height = 20,
        Font = new Font("Segoe UI", 8f, FontStyle.Regular),
        TextAlign = ContentAlignment.MiddleCenter,
        BackColor = Color.FromArgb(40, 0, 0, 0),
        ForeColor = Color.White
    };

    private readonly Label _title = new()
    {
        Dock = DockStyle.Top,
        Height = 26,
        TextAlign = ContentAlignment.MiddleCenter,
        Font = new Font("Segoe UI", 11.5f, FontStyle.Bold),
        ForeColor = Color.White
    };

    private readonly Label _plate = new()
    {
        Dock = DockStyle.Top,
        Height = 24,
        TextAlign = ContentAlignment.MiddleCenter,
        Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
        BackColor = Color.FromArgb(50, 0, 0, 0),
        ForeColor = Color.White
    };

    private readonly Label _status = new()
    {
        Dock = DockStyle.Fill,
        TextAlign = ContentAlignment.MiddleCenter,
        Font = new Font("Segoe UI", 8f, FontStyle.Italic),
        ForeColor = Color.White
    };

    public ParkingSlotResponse? Slot { get; private set; }
    public event EventHandler<ParkingSlotResponse>? SlotClicked;

    public SlotControl()
    {
        Size = new Size(155, 98);
        Margin = new Padding(6);
        BorderStyle = BorderStyle.FixedSingle;
        Cursor = Cursors.Hand;

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
        string vIcon = slot.AllowedVehicleType == "MOTORBIKE" ? "🛵" : "🚗";
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
            _status.Font = new Font("Segoe UI", 8f, FontStyle.Bold);
            _status.ForeColor = Color.Yellow;
        }
        else
        {
            _status.Text = slot.StatusDescription ?? slot.Status;
            _status.Font = new Font("Segoe UI", 8f, FontStyle.Regular);
            _status.ForeColor = Color.White;
        }

        BackColor = slot.Status switch
        {
            "AVAILABLE" => Color.FromArgb(39, 174, 96),       // Xanh lá
            "RESERVED_EMPTY" => Color.FromArgb(41, 128, 185),  // Xanh dương
            "OCCUPIED_VALID" => Color.FromArgb(192, 57, 43),   // Đỏ
            "OCCUPIED_BORROWED" => Color.FromArgb(211, 84, 0),// Cam đậm
            "RESERVED_BORROWED" => Color.FromArgb(230, 126, 34), // Cam
            "OVERDUE_ALERT" => Color.FromArgb(120, 40, 31),    // Đỏ thẫm cảnh báo
            "OCCUPIED_MISMATCH" => Color.FromArgb(185, 119, 14), // Vàng đất
            "BLOCKED" => Color.FromArgb(108, 122, 137),       // Xám cấm đỗ
            _ => Color.FromArgb(52, 73, 94)
        };
    }
}
