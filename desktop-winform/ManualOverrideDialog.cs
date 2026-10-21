namespace ParkingGateDesktop;

public sealed class ManualOverrideDialog : Form
{
    private readonly ComboBox _reason = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 330 };
    private readonly TextBox _description = new() { Multiline = true, Width = 330, Height = 100, MaxLength = 500 };
    private readonly TextBox _plate = new() { Width = 330, MaxLength = 30, CharacterCasing = CharacterCasing.Upper };
    private readonly ComboBox _vehicleType = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 330 };

    public string ReasonCode => (_reason.SelectedItem as ReasonOption)?.Code ?? "";
    public string ReasonText => _description.Text.Trim();
    public string CorrectedPlate => _plate.Text.Trim();
    public string CorrectedVehicleType => (_vehicleType.SelectedItem as VehicleTypeOption)?.Code ?? "UNKNOWN";

    public ManualOverrideDialog(string currentPlate, string currentVehicleType)
    {
        Text = "Xử lý thủ công";
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(390, 450);
        Font = new Font("Segoe UI", 10);

        _reason.Items.AddRange(new object[] {
            new ReasonOption("PLATE_NOT_DETECTED", "AI không đọc được biển số"),
            new ReasonOption("PLATE_CORRECTION", "AI đọc sai biển số"),
            new ReasonOption("VEHICLE_TYPE_CORRECTION", "AI nhận sai loại xe"),
            new ReasonOption("FACE_REVIEW", "Kết quả khuôn mặt cần xem xét"),
            new ReasonOption("FACE_FAILED", "Đối chiếu khuôn mặt thất bại"),
            new ReasonOption("REGISTRATION_IMAGE_MISSING", "Thiếu ảnh đăng ký khuôn mặt")
        });
        _reason.SelectedIndex = string.IsNullOrWhiteSpace(currentPlate) ? 0 : 1;
        _plate.Text = currentPlate;
        _vehicleType.Items.AddRange(new object[] {
            new VehicleTypeOption("MOTORBIKE", "Xe máy"),
            new VehicleTypeOption("CAR", "Ô tô"),
            new VehicleTypeOption("UNKNOWN", "Không xác định")
        });
        _vehicleType.SelectedIndex = string.Equals(currentVehicleType, "CAR", StringComparison.OrdinalIgnoreCase) ? 1
            : string.Equals(currentVehicleType, "MOTORBIKE", StringComparison.OrdinalIgnoreCase) ? 0 : 2;

        var ok = new Button { Text = "Xác nhận xử lý", Width = 145, DialogResult = DialogResult.None };
        var cancel = new Button { Text = "Hủy", Width = 90, DialogResult = DialogResult.Cancel };
        ok.Click += (_, _) => {
            if (ReasonText.Length < 10) {
                MessageBox.Show("Mô tả phải có ít nhất 10 ký tự.", "Thiếu lý do",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            if (string.IsNullOrWhiteSpace(CorrectedPlate)) {
                MessageBox.Show("Vui lòng nhập biển số đã kiểm tra từ ảnh camera.", "Thiếu biển số",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            DialogResult = DialogResult.OK;
            Close();
        };

        var buttons = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.LeftToRight };
        buttons.Controls.Add(ok);
        buttons.Controls.Add(cancel);
        var layout = new FlowLayoutPanel {
            Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false,
            Padding = new Padding(20), AutoScroll = true
        };
        layout.Controls.Add(new Label {
            Text = "Chỉ sử dụng khi AI hoặc camera nhận diện không chính xác. Thao tác sẽ được lưu lại để quản lý kiểm tra.",
            Width = 340, Height = 45, ForeColor = Color.DarkRed
        });
        layout.Controls.Add(new Label { Text = "Lý do cần xử lý thủ công", AutoSize = true });
        layout.Controls.Add(_reason);
        layout.Controls.Add(new Label { Text = "Biển số sau khi kiểm tra", AutoSize = true });
        layout.Controls.Add(_plate);
        layout.Controls.Add(new Label { Text = "Loại xe sau khi kiểm tra", AutoSize = true });
        layout.Controls.Add(_vehicleType);
        layout.Controls.Add(new Label { Text = "Mô tả cụ thể (10–500 ký tự)", AutoSize = true });
        layout.Controls.Add(_description);
        layout.Controls.Add(buttons);
        Controls.Add(layout);
        AcceptButton = ok;
        CancelButton = cancel;
    }

    private sealed record ReasonOption(string Code, string Label)
    {
        public override string ToString() => Label;
    }
    private sealed record VehicleTypeOption(string Code, string Label)
    {
        public override string ToString() => Label;
    }
}
