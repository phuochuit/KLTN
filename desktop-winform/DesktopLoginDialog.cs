namespace ParkingGateDesktop;

internal sealed class DesktopLoginDialog : Form
{
    public event Action? LoginSucceeded;
    private readonly Func<string, string, Task> _authenticate;
    private readonly TextBox _username = new() { Dock = DockStyle.Fill, AccessibleName = "Tên tài khoản" };
    private readonly TextBox _password = new() { Dock = DockStyle.Fill, UseSystemPasswordChar = true, AccessibleName = "Mật khẩu" };
    private readonly Button _togglePassword = new() { Text = "Hiện", AutoSize = true, TabStop = false, AccessibleName = "Hiện mật khẩu" };
    private readonly Label _error = new() { AutoSize = true, ForeColor = Color.Firebrick, AccessibleRole = AccessibleRole.Alert };
    private readonly Button _login = new() { Text = "Đăng nhập", AutoSize = true };
    private readonly Button _cancel = new() { Text = "Thoát", AutoSize = true, DialogResult = DialogResult.Cancel };

    public DesktopLoginDialog(Func<string, string, Task> authenticate)
    {
        _authenticate = authenticate;
        Text = "Đăng nhập trạm gác";
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MinimizeBox = true;
        MaximizeBox = false;
        ShowInTaskbar = true;
        ClientSize = new Size(560, 340);
        Font = new Font("Segoe UI", 10);

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(48, 42, 48, 36),
            ColumnCount = 2,
            RowCount = 6,
            AutoSize = false
        };
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 105));
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 64));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 44));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 24));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));

        var title = new Label
        {
            Text = "PARKING ANPR — ĐĂNG NHẬP",
            Dock = DockStyle.Fill,
            Font = new Font("Segoe UI", 16, FontStyle.Bold),
            ForeColor = Color.FromArgb(23, 54, 93),
            TextAlign = ContentAlignment.MiddleLeft
        };
        layout.Controls.Add(title, 0, 0);
        layout.SetColumnSpan(title, 2);
        layout.Controls.Add(new Label { Text = "Tài khoản", AutoSize = true, Anchor = AnchorStyles.Left }, 0, 1);
        layout.Controls.Add(_username, 1, 1);
        layout.Controls.Add(new Label { Text = "Mật khẩu", AutoSize = true, Anchor = AnchorStyles.Left }, 0, 2);
        var passwordPanel = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 1,
            Margin = Padding.Empty
        };
        passwordPanel.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        passwordPanel.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        passwordPanel.Controls.Add(_password, 0, 0);
        passwordPanel.Controls.Add(_togglePassword, 1, 0);
        layout.Controls.Add(passwordPanel, 1, 2);
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
        _togglePassword.Click += (_, _) =>
        {
            _password.UseSystemPasswordChar = !_password.UseSystemPasswordChar;
            _togglePassword.Text = _password.UseSystemPasswordChar ? "Hiện" : "Ẩn";
            _togglePassword.AccessibleName = _password.UseSystemPasswordChar ? "Hiện mật khẩu" : "Ẩn mật khẩu";
            _password.Focus();
            _password.SelectionStart = _password.TextLength;
        };
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
            LoginSucceeded?.Invoke();
        }
        catch (DesktopAuthenticationException)
        {
            _error.Text = "Sai tên đăng nhập hoặc mật khẩu.";
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
