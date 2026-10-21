namespace ParkingGateDesktop;

public sealed class PaymentMethodDialog : Form
{
    public string? SelectedMethod { get; private set; }
    public string? TransactionReference { get; private set; }
    public bool ApplyPayment { get; private set; }

    private readonly ComboBox _method = new() {
        DropDownStyle = ComboBoxStyle.DropDownList,
        Width = 240
    };
    private readonly TextBox _reference = new() { Width = 240, Visible = false };
    private readonly Label _referenceLabel = new() { Text = "Mã giao dịch", AutoSize = true };
    private readonly Label _fee = new() { AutoSize = true, Font = new Font("Segoe UI", 11f, FontStyle.Bold) };

    public PaymentMethodDialog(string plate, decimal fee)
    {
        Text = "Thu phí xe ra";
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(370, 230);
        Font = new Font("Segoe UI", 10);

        _method.Items.Add(new PaymentOption("CASH", "Tiền mặt", false));
        _method.Items.Add(new PaymentOption("BANK_TRANSFER", "Chuyển khoản", true));
        _method.Items.Add(new PaymentOption("POS", "Máy POS", true));
        _method.DisplayMember = "Label";
        _method.ValueMember = "Code";
        _method.SelectedIndex = 0;
        _method.SelectedIndexChanged += OnMethodChanged;

        _fee.Text = $"Biển số: {plate}\r\nPhí thu: {fee:N0} đ";
        var feePanel = new Panel { Height = 55, Dock = DockStyle.Top };
        feePanel.Controls.Add(_fee);
        var root = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(18), RowCount = 5, ColumnCount = 2 };
        root.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 115));
        root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        root.Controls.Add(new Label { Text = "Hình thức thu", AutoSize = true }, 0, 0);
        root.Controls.Add(_method, 1, 0);
        root.Controls.Add(_referenceLabel, 0, 1);
        root.Controls.Add(_reference, 1, 1);
        root.Controls.Add(new Label { Text = "Áp dụng thanh toán cho lượt ra này.", AutoSize = true, MaximumSize = new Size(320, 0), ForeColor = Color.DimGray }, 0, 2);
        root.SetColumnSpan(root.GetControlFromPosition(0, 2)!, 2);
        var buttons = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.RightToLeft };
        var save = new Button { Text = "Xác nhận thu", AutoSize = true };
        var cancel = new Button { Text = "Hủy", AutoSize = true, DialogResult = DialogResult.Cancel };
        var skip = new Button { Text = "Bỏ qua", AutoSize = true };
        save.Click += SaveClicked;
        skip.Click += (_, _) => { ApplyPayment = false; DialogResult = DialogResult.Cancel; Close(); };
        buttons.Controls.Add(save);
        buttons.Controls.Add(cancel);
        buttons.Controls.Add(skip);
        root.Controls.Add(buttons, 0, 4);
        root.SetColumnSpan(buttons, 2);
        Controls.Add(root);
        Controls.Add(feePanel);
        AcceptButton = save;
        CancelButton = cancel;
        OnMethodChanged(this, EventArgs.Empty);
    }

    private void OnMethodChanged(object? sender, EventArgs e)
    {
        if (_method.SelectedItem is not PaymentOption option) return;
        _reference.Visible = option.RequiresReference;
        _referenceLabel.Visible = option.RequiresReference;
    }

    private void SaveClicked(object? sender, EventArgs e)
    {
        if (_method.SelectedItem is not PaymentOption option)
        {
            MessageBox.Show(this, "Vui lòng chọn hình thức thanh toán.", "Chưa đủ thông tin",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }
        SelectedMethod = option.Code;
        TransactionReference = option.RequiresReference ? _reference.Text.Trim() : null;
        if (option.RequiresReference && string.IsNullOrWhiteSpace(TransactionReference))
        {
            MessageBox.Show(this, "Vui lòng nhập mã giao dịch chuyển khoản/POS.", "Chưa đủ thông tin", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }
        ApplyPayment = true;
        DialogResult = DialogResult.OK;
        Close();
    }

    private sealed class PaymentOption(string code, string label, bool requiresReference)
    {
        public string Code => code;
        public string Label => label;
        public bool RequiresReference => requiresReference;
        public override string ToString() => label;
    }
}
