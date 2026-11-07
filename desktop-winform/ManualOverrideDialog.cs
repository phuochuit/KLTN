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

    public ManualOverrideDialog(string currentPlate, string currentVehicleType, IReadOnlyList<VehicleTypeDto>? availableTypes = null)
    {
        Text = "LumiPark - Xử lý thủ công";
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(460, 560);
        Font = LumiTheme.AppFont();
        BackColor = LumiTheme.Canvas;

        _reason.Items.AddRange(new object[] {
            new ReasonOption("PLATE_NOT_DETECTED", "Không đọc được biển số"),
            new ReasonOption("PLATE_CORRECTION", "Biển số nhận dạng chưa đúng"),
            new ReasonOption("VEHICLE_TYPE_CORRECTION", "Loại xe nhận dạng chưa đúng"),
            new ReasonOption("FACE_REVIEW", "Kết quả khuôn mặt cần xem xét"),
            new ReasonOption("FACE_FAILED", "Đối chiếu khuôn mặt thất bại"),
            new ReasonOption("REGISTRATION_IMAGE_MISSING", "Thiếu ảnh đăng ký khuôn mặt")
        });
        _reason.SelectedIndex = string.IsNullOrWhiteSpace(currentPlate) ? 0 : 1;
        _plate.Text = currentPlate;
        bool currentIsCar = currentVehicleType.StartsWith("CAR", StringComparison.OrdinalIgnoreCase);
        var dynamicOptions = (availableTypes ?? Array.Empty<VehicleTypeDto>())
            .Where(type => type.Active)
            .Where(type => currentVehicleType == "UNKNOWN"
                || (type.Category.Contains("CAR", StringComparison.OrdinalIgnoreCase)
                    || type.Code.StartsWith("CAR", StringComparison.OrdinalIgnoreCase)) == currentIsCar)
            .Select(type => new VehicleTypeOption(type.Code, type.DisplayName))
            .ToArray();
        if (dynamicOptions.Length > 0)
            _vehicleType.Items.AddRange(dynamicOptions);
        else
            _vehicleType.Items.Add(new VehicleTypeOption(currentVehicleType, MainForm.DisplayVehicleType(currentVehicleType)));
        int selectedVehicleType = Enumerable.Range(0, _vehicleType.Items.Count)
            .FirstOrDefault(index => string.Equals(
                (_vehicleType.Items[index] as VehicleTypeOption)?.Code,
                currentVehicleType, StringComparison.OrdinalIgnoreCase), -1);
        _vehicleType.SelectedIndex = selectedVehicleType >= 0 ? selectedVehicleType : 0;

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
            Padding = new Padding(28, 24, 28, 22), AutoScroll = true, BackColor = LumiTheme.Surface
        };
        layout.Controls.Add(new Label {
            Text = "Chỉ sử dụng khi kết quả nhận dạng chưa chính xác. Thao tác sẽ được lưu lại để quản lý kiểm tra.",
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
        LumiTheme.Apply(this);
        LumiTheme.StyleButton(ok, primary: true);
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
