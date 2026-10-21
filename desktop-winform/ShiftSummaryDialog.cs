namespace ParkingGateDesktop;

public sealed class ShiftSummaryDialog : Form
{
    public ShiftSummaryDialog(ShiftSummary summary, int offlineQueueCount, bool previous)
    {
        Text = previous ? "Tổng kết ca trước" : "Tổng kết ca hiện tại";
        StartPosition = FormStartPosition.CenterParent;
        MinimumSize = new Size(620, 650);
        Size = new Size(680, 720);
        Font = new Font("Segoe UI", 10);
        BackColor = Color.White;

        var root = new TableLayoutPanel {
            Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 3,
            Padding = new Padding(24), AutoScroll = true
        };
        root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.RowStyles.Add(new RowStyle(SizeType.AutoSize));

        root.Controls.Add(new Label {
            Text = previous ? "BÁO CÁO BÀN GIAO CA TRƯỚC" : "BÁO CÁO CA HIỆN TẠI",
            AutoSize = true, Font = new Font("Segoe UI", 16, FontStyle.Bold),
            ForeColor = Color.FromArgb(23, 54, 93), Margin = new Padding(0, 0, 0, 18)
        }, 0, 0);

        var details = new TableLayoutPanel {
            Dock = DockStyle.Top, AutoSize = true, ColumnCount = 2,
            CellBorderStyle = TableLayoutPanelCellBorderStyle.Single
        };
        details.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 48));
        details.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 52));
        Add(details, "Tên ca", summary.ShiftName);
        Add(details, "Nhân viên", summary.OperatorName);
        Add(details, "Thời gian", $"{summary.StartsAt.ToLocalTime():dd/MM/yyyy HH:mm} – {summary.EndsAt.ToLocalTime():dd/MM/yyyy HH:mm}");
        Add(details, "Nhiệm vụ", summary.Duty);
        Add(details, "Tổng thu", Money(summary.TotalRevenue), true);
        Add(details, "Tiền mặt", Money(summary.CashRevenue));
        Add(details, "Chuyển khoản", Money(summary.BankTransferRevenue));
        Add(details, "POS", Money(summary.PosRevenue));
        Add(details, "Xe vào trong ca", summary.VehicleEntries.ToString("N0"));
        Add(details, "Xe ra trong ca", summary.VehicleExits.ToString("N0"));
        Add(details, "Xe đang trong bãi", summary.VehiclesCurrentlyInside.ToString("N0"));
        Add(details, "Ô đang sử dụng", summary.OccupiedSlots.ToString("N0"));
        Add(details, "Ô còn trống", summary.AvailableSlots.ToString("N0"));
        Add(details, "Giao dịch chờ đối soát", summary.PendingReconciliation.ToString("N0"),
            summary.PendingReconciliation > 0);
        Add(details, "Chỉnh sửa/xử lý thủ công", summary.ManualOverrides.ToString("N0"),
            summary.ManualOverrides > 0);
        Add(details, "Outbox chưa đồng bộ trên máy", offlineQueueCount.ToString("N0"),
            offlineQueueCount > 0);
        root.Controls.Add(details, 0, 1);

        var close = new Button {
            Text = previous ? "ĐÃ XEM – TIẾP TỤC CA MỚI" : "XÁC NHẬN BÀN GIAO",
            AutoSize = true, DialogResult = DialogResult.OK, Padding = new Padding(16, 7, 16, 7),
            BackColor = Color.FromArgb(45, 116, 181), ForeColor = Color.White, FlatStyle = FlatStyle.Flat,
            Anchor = AnchorStyles.Right, Margin = new Padding(0, 18, 0, 0)
        };
        root.Controls.Add(close, 0, 2);
        Controls.Add(root);
        AcceptButton = close;
    }

    private static string Money(decimal value) => $"{value:N0} đ";

    private static void Add(TableLayoutPanel table, string label, string value, bool warning = false)
    {
        int row = table.RowCount++;
        table.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        table.Controls.Add(new Label {
            Text = label, AutoSize = true, Dock = DockStyle.Fill,
            Padding = new Padding(10), Font = new Font("Segoe UI", 10, FontStyle.Bold)
        }, 0, row);
        table.Controls.Add(new Label {
            Text = value, AutoSize = true, Dock = DockStyle.Fill, Padding = new Padding(10),
            ForeColor = warning ? Color.Firebrick : Color.FromArgb(23, 54, 93)
        }, 1, row);
    }
}
