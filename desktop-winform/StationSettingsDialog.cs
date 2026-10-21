namespace ParkingGateDesktop;

public sealed class StationSettingsDialog : Form
{
    private readonly TextBox _vehicleSource = new() { Width = 310 };
    private readonly TextBox _faceSource = new() { Width = 310 };
    private readonly NumericUpDown _frameCount = new() { Minimum = 1, Maximum = 60, Width = 100 };
    private readonly NumericUpDown _autoClose = new() { Minimum = 1, Maximum = 120, Width = 100 };
    public GateStationSettings? SavedSettings { get; private set; }

    public StationSettingsDialog(GateStationSettings current)
    {
        Text = "Cấu hình trạm gác";
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(560, 330);
        Font = new Font("Segoe UI", 10);

        _vehicleSource.Text = current.VehicleCameraSource;
        _faceSource.Text = current.FaceCameraSource;
        _frameCount.Value = current.CameraFrameCount;
        _autoClose.Value = current.BarrierAutoCloseSeconds;

        var root = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(18), RowCount = 7, ColumnCount = 2 };
        root.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 180));
        root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        root.Controls.Add(new Label { Text = "Camera xe", AutoSize = true }, 0, 0);
        root.Controls.Add(_vehicleSource, 1, 0);
        root.Controls.Add(new Label { Text = "Camera khuôn mặt", AutoSize = true }, 0, 1);
        root.Controls.Add(_faceSource, 1, 1);
        root.Controls.Add(new Label { Text = "Số khung hình", AutoSize = true }, 0, 2);
        root.Controls.Add(_frameCount, 1, 2);
        root.Controls.Add(new Label { Text = "Tự đóng barrier (giây)", AutoSize = true }, 0, 3);
        root.Controls.Add(_autoClose, 1, 3);
        root.Controls.Add(new Label {
            Text = "Nhập 0, 1... cho camera USB hoặc địa chỉ RTSP/HTTP. Địa chỉ mạng sẽ được dùng sau khi dịch vụ AI hỗ trợ nguồn mạng.",
            AutoSize = true, MaximumSize = new Size(500, 0), ForeColor = Color.DimGray
        }, 0, 4);
        root.SetColumnSpan(root.GetControlFromPosition(0, 4)!, 2);
        var buttons = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.RightToLeft, Dock = DockStyle.Fill };
        var save = new Button { Text = "Lưu cấu hình", AutoSize = true };
        var cancel = new Button { Text = "Hủy", AutoSize = true, DialogResult = DialogResult.Cancel };
        save.Click += SaveClicked;
        buttons.Controls.Add(save);
        buttons.Controls.Add(cancel);
        root.Controls.Add(buttons, 0, 6);
        root.SetColumnSpan(buttons, 2);
        Controls.Add(root);
        AcceptButton = save;
        CancelButton = cancel;
    }

    private void SaveClicked(object? sender, EventArgs e)
    {
        try
        {
            var settings = new GateStationSettings {
                VehicleCameraSource = _vehicleSource.Text.Trim(),
                FaceCameraSource = _faceSource.Text.Trim(),
                CameraFrameCount = (int)_frameCount.Value,
                BarrierAutoCloseSeconds = (int)_autoClose.Value,
                BarrierDriver = "SIMULATOR"
            };
            settings.Validate();
            SavedSettings = settings;
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (ArgumentException ex)
        {
            MessageBox.Show(this, ex.Message, "Cấu hình chưa hợp lệ", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }
}
