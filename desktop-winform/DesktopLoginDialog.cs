namespace ParkingGateDesktop;

internal sealed class DesktopLoginDialog : Form
{
    public event Action? LoginSucceeded;
    private readonly Func<string, string, Task> _authenticate;
    private readonly TextBox _username = new() { Dock = DockStyle.Fill, AccessibleName = "Tên tài khoản" };
    private readonly TextBox _password = new() { Dock = DockStyle.Fill, UseSystemPasswordChar = true, AccessibleName = "Mật khẩu" };
    private readonly CheckBox _showPassword = new() { Text = "Hiện mật khẩu", AutoSize = true, AccessibleName = "Hiện mật khẩu" };
    private readonly Label _error = new() { AutoSize = true, ForeColor = Color.Firebrick, AccessibleRole = AccessibleRole.Alert };
    private readonly Button _login = new() { Text = "Đăng nhập", AutoSize = true };
    private readonly Button _cancel = new() { Text = "Thoát", AutoSize = true, DialogResult = DialogResult.Cancel };

    public DesktopLoginDialog(Func<string, string, Task> authenticate)
    {
        _authenticate = authenticate;
        Text = "LumiPark - Đăng nhập trạm gác";
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = true;
        ClientSize = new Size(800, 500);
        MinimumSize = new Size(800, 500);
        MaximumSize = new Size(800, 500);
        Font = LumiTheme.AppFont();
        BackColor = LumiTheme.Primary;

        var shell = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            RowCount = 2,
            ColumnCount = 1,
            Padding = new Padding(1),
            BackColor = LumiTheme.Primary
        };
        shell.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        shell.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

        var topBar = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = LumiTheme.Primary,
            Margin = Padding.Empty
        };
        var windowTitle = new Label
        {
            Text = "LumiPark  •  Trạm gác",
            Location = new Point(18, 0),
            Size = new Size(610, 46),
            TextAlign = ContentAlignment.MiddleLeft,
            ForeColor = Color.White,
            Font = LumiTheme.AppFont(10f, FontStyle.Bold)
        };
        topBar.Controls.Add(windowTitle);

        Point dragCursor = Point.Empty;
        Point dragForm = Point.Empty;
        void StartDrag(object? _, MouseEventArgs e)
        {
            if (e.Button != MouseButtons.Left) return;
            dragCursor = Cursor.Position;
            dragForm = Location;
        }
        void ContinueDrag(object? _, MouseEventArgs e)
        {
            if (e.Button != MouseButtons.Left) return;
            Point delta = Point.Subtract(Cursor.Position, new Size(dragCursor));
            Location = Point.Add(dragForm, new Size(delta));
        }
        topBar.MouseDown += StartDrag;
        topBar.MouseMove += ContinueDrag;
        windowTitle.MouseDown += StartDrag;
        windowTitle.MouseMove += ContinueDrag;

        var body = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 1,
            Margin = Padding.Empty,
            BackColor = LumiTheme.Surface
        };
        body.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 305));
        body.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));

        var brandPanel = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            RowCount = 4,
            ColumnCount = 1,
            Padding = new Padding(34, 62, 34, 42),
            BackColor = LumiTheme.Primary
        };
        brandPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 82));
        brandPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
        brandPanel.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        brandPanel.RowStyles.Add(new RowStyle(SizeType.Absolute, 34));
        brandPanel.Controls.Add(new Label
        {
            Text = "LumiPark",
            Dock = DockStyle.Fill,
            Font = LumiTheme.AppFont(26f, FontStyle.Bold),
            ForeColor = Color.White,
            TextAlign = ContentAlignment.BottomLeft
        }, 0, 0);
        brandPanel.Controls.Add(new Label
        {
            Text = "Vận hành bãi xe\r\nnhanh chóng và an toàn",
            Dock = DockStyle.Fill,
            Font = LumiTheme.AppFont(12f, FontStyle.Bold),
            ForeColor = Color.FromArgb(232, 236, 255),
            TextAlign = ContentAlignment.TopLeft
        }, 0, 1);
        brandPanel.Controls.Add(new Label
        {
            Text = "Theo dõi phương tiện, vị trí đỗ và ca trực trên một màn hình thống nhất.",
            Dock = DockStyle.Top,
            AutoSize = false,
            Height = 70,
            Font = LumiTheme.AppFont(9.5f),
            ForeColor = Color.FromArgb(210, 218, 255)
        }, 0, 2);
        brandPanel.Controls.Add(new Label
        {
            Text = "TRẠM GÁC • SẴN SÀNG",
            Dock = DockStyle.Fill,
            Font = LumiTheme.AppFont(8.5f, FontStyle.Bold),
            ForeColor = Color.FromArgb(221, 228, 255),
            TextAlign = ContentAlignment.MiddleLeft
        }, 0, 3);

        var formArea = new Panel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(48, 42, 48, 38),
            BackColor = LumiTheme.Surface
        };
        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 8,
            BackColor = LumiTheme.Surface
        };
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 68));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 22));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 22));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 76));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 54));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        var heading = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 2,
            BackColor = LumiTheme.Surface,
            Margin = Padding.Empty
        };
        heading.RowStyles.Add(new RowStyle(SizeType.Absolute, 40));
        heading.RowStyles.Add(new RowStyle(SizeType.Absolute, 30));
        heading.Controls.Add(new Label
        {
            Text = "Đăng nhập trạm gác",
            Dock = DockStyle.Fill,
            Font = LumiTheme.AppFont(17f, FontStyle.Bold),
            ForeColor = LumiTheme.Ink,
            TextAlign = ContentAlignment.BottomLeft
        }, 0, 0);
        heading.Controls.Add(new Label
        {
            Text = "Nhập thông tin để tiếp tục vận hành",
            Dock = DockStyle.Fill,
            Font = LumiTheme.AppFont(9.2f),
            ForeColor = LumiTheme.Muted,
            TextAlign = ContentAlignment.TopLeft
        }, 0, 1);
        layout.Controls.Add(heading, 0, 0);
        layout.Controls.Add(new Label { Text = "Tài khoản", Dock = DockStyle.Fill, ForeColor = LumiTheme.Muted }, 0, 1);
        var usernameFrame = new Panel { Dock = DockStyle.Fill, BackColor = LumiTheme.ControlLine, Padding = new Padding(1), Margin = new Padding(0, 2, 0, 7) };
        var usernameSurface = new Panel { Dock = DockStyle.Fill, BackColor = LumiTheme.Surface, Padding = new Padding(11, 9, 11, 0) };
        _username.Dock = DockStyle.Top;
        _username.BorderStyle = BorderStyle.None;
        _username.Margin = Padding.Empty;
        usernameSurface.Controls.Add(_username);
        usernameFrame.Controls.Add(usernameSurface);
        layout.Controls.Add(usernameFrame, 0, 2);
        layout.Controls.Add(new Label { Text = "Mật khẩu", Dock = DockStyle.Fill, ForeColor = LumiTheme.Muted }, 0, 3);

        var passwordFrame = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = LumiTheme.ControlLine,
            Padding = new Padding(1),
            Margin = new Padding(0, 2, 0, 7)
        };
        var passwordSurface = new Panel { Dock = DockStyle.Fill, BackColor = LumiTheme.Surface, Padding = new Padding(10, 8, 4, 0) };
        _password.Dock = DockStyle.Top;
        _password.BorderStyle = BorderStyle.None;
        _password.Margin = Padding.Empty;
        passwordSurface.Controls.Add(_password);
        passwordFrame.Controls.Add(passwordSurface);
        layout.Controls.Add(passwordFrame, 0, 4);
        var loginStatus = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 2,
            BackColor = LumiTheme.Surface,
            Padding = new Padding(0, 6, 0, 0),
            Margin = Padding.Empty
        };
        loginStatus.RowStyles.Add(new RowStyle(SizeType.Absolute, 28));
        loginStatus.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        _showPassword.Margin = Padding.Empty;
        _error.AutoSize = false;
        _error.Dock = DockStyle.Fill;
        _error.Margin = new Padding(0, 2, 0, 0);
        _error.Font = LumiTheme.AppFont(8.8f, FontStyle.Bold);
        _error.TextAlign = ContentAlignment.TopLeft;
        loginStatus.Controls.Add(_showPassword, 0, 0);
        loginStatus.Controls.Add(_error, 0, 1);
        layout.Controls.Add(loginStatus, 0, 5);

        var buttons = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 1,
            BackColor = LumiTheme.Surface
        };
        buttons.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 36));
        buttons.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 64));
        _cancel.AutoSize = false;
        _cancel.Dock = DockStyle.Fill;
        _login.AutoSize = false;
        _login.Dock = DockStyle.Fill;
        buttons.Controls.Add(_cancel, 0, 0);
        buttons.Controls.Add(_login, 1, 0);
        layout.Controls.Add(buttons, 0, 6);
        layout.Controls.Add(new Label
        {
            Text = "Sử dụng tài khoản được cấp cho nhân viên trực.",
            Dock = DockStyle.Fill,
            ForeColor = LumiTheme.Muted,
            Font = LumiTheme.AppFont(8.3f),
            TextAlign = ContentAlignment.BottomLeft
        }, 0, 7);

        formArea.Controls.Add(layout);
        body.Controls.Add(brandPanel, 0, 0);
        body.Controls.Add(formArea, 1, 0);
        shell.Controls.Add(topBar, 0, 0);
        shell.Controls.Add(body, 0, 1);
        Controls.Add(shell);

        AcceptButton = _login;
        CancelButton = _cancel;
        _login.Click += LoginClicked;
        _showPassword.CheckedChanged += (_, _) =>
        {
            _password.UseSystemPasswordChar = !_showPassword.Checked;
            _showPassword.AccessibleName = _showPassword.Checked ? "Ẩn mật khẩu" : "Hiện mật khẩu";
            _password.Focus();
            _password.SelectionStart = _password.TextLength;
        };
        LumiTheme.Apply(this);
        LumiTheme.StyleButton(_login, primary: true);
        _username.BorderStyle = BorderStyle.None;
        _password.BorderStyle = BorderStyle.None;
        _cancel.BackColor = LumiTheme.SurfaceSoft;
        _cancel.FlatAppearance.BorderSize = 0;
        _cancel.Margin = new Padding(0, 4, 8, 4);
        _login.Margin = new Padding(8, 4, 0, 4);
        LumiTheme.Round(this, 16);
        Shown += (_, _) =>
        {
            _username.Focus();
        };
    }

    private async void LoginClicked(object? sender, EventArgs e)
    {
        _error.Text = "";
        if (string.IsNullOrWhiteSpace(_username.Text) && string.IsNullOrWhiteSpace(_password.Text))
        {
            _error.Text = "Vui lòng nhập tài khoản và mật khẩu.";
            _username.Focus();
            return;
        }
        if (string.IsNullOrWhiteSpace(_username.Text))
        {
            _error.Text = "Vui lòng nhập tài khoản.";
            _username.Focus();
            return;
        }
        if (string.IsNullOrWhiteSpace(_password.Text))
        {
            _error.Text = "Vui lòng nhập mật khẩu.";
            _password.Focus();
            return;
        }
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
            _error.Text = "Không kết nối được đến hệ thống. Vui lòng kiểm tra kết nối mạng và thử lại.";
        }
        catch (TaskCanceledException)
        {
            _error.Text = "Hệ thống phản hồi quá thời gian. Vui lòng thử lại sau.";
        }
        catch (InvalidOperationException)
        {
            _error.Text = "Sai thông tin đăng nhập hoặc mật khẩu.";
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
