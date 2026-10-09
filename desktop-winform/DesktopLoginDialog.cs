namespace ParkingGateDesktop;

internal sealed class DesktopLoginDialog : Form
{
    private readonly Func<string, string, Task> _authenticate;
    private readonly TextBox _username = new() { Dock = DockStyle.Fill, AccessibleName = "Tên tài khoản" };
    private readonly TextBox _password = new() { Dock = DockStyle.Fill, UseSystemPasswordChar = true, AccessibleName = "Mật khẩu" };
    private readonly Label _error = new() { AutoSize = true, ForeColor = Color.Firebrick, AccessibleRole = AccessibleRole.Alert };
    private readonly Button _login = new() { Text = "Đăng nhập", AutoSize = true };
    private readonly Button _cancel = new() { Text = "Thoát", AutoSize = true, DialogResult = DialogResult.Cancel };

    public DesktopLoginDialog(Func<string, string, Task> authenticate)
    {
        _authenticate = authenticate;
        Text = "Đăng nhập trạm gác";
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MinimizeBox = false;
        MaximizeBox = false;
        ShowInTaskbar = false;
        ClientSize = new Size(410, 235);
        Font = new Font("Segoe UI", 10);

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(20),
            ColumnCount = 2,
            RowCount = 6,
            AutoSize = false
        };
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 105));
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 32));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 36));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 32));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));

        var title = new Label
        {
            Text = "PARKING ANPR — ĐĂNG NHẬP",
            Dock = DockStyle.Fill,
            Font = new Font("Segoe UI", 11, FontStyle.Bold),
            ForeColor = Color.FromArgb(23, 54, 93),
            TextAlign = ContentAlignment.MiddleLeft
        };
        layout.Controls.Add(title, 0, 0);
        layout.SetColumnSpan(title, 2);
        layout.Controls.Add(new Label { Text = "Tài khoản", AutoSize = true, Anchor = AnchorStyles.Left }, 0, 1);
        layout.Controls.Add(_username, 1, 1);
        layout.Controls.Add(new Label { Text = "Mật khẩu", AutoSize = true, Anchor = AnchorStyles.Left }, 0, 2);
        layout.Controls.Add(_password, 1, 2);
        layout.Controls.Add(_error, 0, 4);
        layout.SetColumnSpan(_error, 2);

        var buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.RightToLeft,
            WrapContents = false
        };
        buttons.Controls.Add(_login);
        buttons.Controls.Add(_cancel);
        layout.Controls.Add(buttons, 0, 5);
        layout.SetColumnSpan(buttons, 2);
        Controls.Add(layout);

        AcceptButton = _login;
        CancelButton = _cancel;
        _login.Click += LoginClicked;
        Shown += (_, _) => _username.Focus();
    }

    private async void LoginClicked(object? sender, EventArgs e)
    {
        _error.Text = "";
        _login.Enabled = false;
        _username.Enabled = false;
        _password.Enabled = false;
        UseWaitCursor = true;
        try
        {
            await _authenticate(_username.Text, _password.Text);
            _password.Clear();
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (DesktopAuthenticationException)
        {
            _error.Text = "Thông tin đăng nhập không hợp lệ hoặc tài khoản chưa thể sử dụng.";
            _password.Clear();
            _password.Focus();
        }
        catch (HttpRequestException)
        {
            _error.Text = "Không kết nối được Spring API. Kiểm tra địa chỉ và trạng thái dịch vụ.";
        }
        catch (TaskCanceledException)
        {
            _error.Text = "Spring API phản hồi quá thời gian cho phép. Thử lại sau.";
        }
        catch (InvalidOperationException)
        {
            _error.Text = "Không thể đăng nhập với địa chỉ Spring API hiện tại.";
        }
        finally
        {
            UseWaitCursor = false;
            _login.Enabled = true;
            _username.Enabled = true;
            _password.Enabled = true;
        }
    }
}
