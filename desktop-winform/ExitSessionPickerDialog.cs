using System;
using System.Collections.Generic;
using System.Drawing;
using System.Windows.Forms;

namespace ParkingGateDesktop;

/// <summary>
/// Nối biển số đọc được lúc ra với lượt vào đang dùng mã tạm NPL.
/// Nhân viên chọn đúng xe dựa trên giờ vào và ô đang đỗ.
/// </summary>
public sealed class ExitSessionPickerDialog : Form
{
    private readonly ListView _list = new()
    {
        Dock = DockStyle.Fill,
        View = View.Details,
        FullRowSelect = true,
        MultiSelect = false,
        HideSelection = false
    };
    private readonly List<ExitCandidateResponse> _candidates;
    private readonly Button _choose = new() { Text = "CHỌN XE NÀY", AutoSize = true };

    public ExitCandidateResponse? Selected { get; private set; }

    public ExitSessionPickerDialog(string plate, List<ExitCandidateResponse> candidates)
    {
        _candidates = candidates;
        Text = "Chọn lượt xe vào để xác nhận xe ra";
        StartPosition = FormStartPosition.CenterParent;
        MinimizeBox = false;
        MaximizeBox = false;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        ClientSize = new Size(620, 430);
        Font = new Font("Segoe UI", 9.5f);

        var header = new Label
        {
            Dock = DockStyle.Top,
            AutoSize = false,
            Height = 78,
            Padding = new Padding(12),
            Text = $"Biển số đọc được: {plate}\r\n"
                + "Không tìm thấy lượt vào theo biển này. Hãy chọn đúng xe đang đỗ bằng mã tạm "
                + "dựa vào giờ vào và ô đỗ."
        };

        _list.Columns.Add("Mã lượt", 170);
        _list.Columns.Add("Giờ vào", 150);
        _list.Columns.Add("Ô đỗ", 80);
        _list.Columns.Add("Loại xe", 180);
        foreach (var item in candidates)
        {
            var row = new ListViewItem(item.PlateNumber) { Tag = item };
            row.SubItems.Add(item.EntryTime.ToString("dd/MM/yyyy HH:mm:ss"));
            row.SubItems.Add(string.IsNullOrWhiteSpace(item.SlotCode) ? "-" : item.SlotCode);
            row.SubItems.Add(MainForm.DisplayVehicleType(item.VehicleType ?? "UNKNOWN"));
            _list.Items.Add(row);
        }
        if (_list.Items.Count > 0) _list.Items[0].Selected = true;
        _list.DoubleClick += (_, _) => Accept();

        var hint = new Label
        {
            Dock = DockStyle.Top,
            AutoSize = false,
            Height = 34,
            Padding = new Padding(12, 8, 12, 0),
            ForeColor = Color.DimGray,
            Text = "Chỉ chọn khi chắc chắn đúng xe. Nối nhầm sẽ tính phí sai cho xe khác."
        };

        var buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            FlowDirection = FlowDirection.RightToLeft,
            Height = 52,
            Padding = new Padding(0, 10, 12, 0)
        };
        var cancel = new Button { Text = "HỦY", AutoSize = true, DialogResult = DialogResult.Cancel };
        buttons.Controls.Add(cancel);
        buttons.Controls.Add(_choose);

        Controls.Add(_list);
        Controls.Add(hint);
        Controls.Add(header);
        Controls.Add(buttons);
        _choose.Click += (_, _) => Accept();
        AcceptButton = _choose;
        CancelButton = cancel;
    }

    private void Accept()
    {
        // Nút "CHỌN XE NÀY" và double-click đều phải lưu đúng dòng đang chọn.
        var item = _list.SelectedItems.Count > 0
            ? _list.SelectedItems[0]
            : (_list.Items.Count > 0 ? _list.Items[0] : null);
        if (item?.Tag is not ExitCandidateResponse candidate) return;
        Selected = candidate;
        DialogResult = DialogResult.OK;
        Close();
    }
}
