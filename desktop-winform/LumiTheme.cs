using System.Drawing.Drawing2D;

namespace ParkingGateDesktop;

/// <summary>
/// Token giao diện dùng chung cho WinForms. Lớp này chỉ định dạng control,
/// không đăng ký hoặc thay đổi bất kỳ luồng nghiệp vụ nào.
/// </summary>
internal static class LumiTheme
{
    private static readonly HashSet<Control> FramedInputs = new();
    internal static readonly Color Canvas = Color.FromArgb(234, 241, 255);
    internal static readonly Color Surface = Color.White;
    internal static readonly Color SurfaceSoft = Color.FromArgb(243, 245, 255);
    internal static readonly Color Ink = Color.FromArgb(23, 30, 66);
    internal static readonly Color Muted = Color.FromArgb(66, 80, 107);
    internal static readonly Color Primary = Color.FromArgb(48, 66, 170);
    internal static readonly Color PrimaryHover = Color.FromArgb(43, 57, 152);
    internal static readonly Color Accent = Color.FromArgb(117, 65, 201);
    internal static readonly Color Line = Color.FromArgb(220, 227, 242);
    internal static readonly Color ControlLine = Color.FromArgb(123, 137, 171);
    internal static readonly Color Good = Color.FromArgb(23, 96, 82);
    internal static readonly Color Danger = Color.FromArgb(163, 43, 37);
    internal static readonly Color Warning = Color.FromArgb(133, 77, 14);

    internal static Font AppFont(float size = 10f, FontStyle style = FontStyle.Regular)
    {
        try { return new Font("Be Vietnam Pro", size, style); }
        catch { return new Font("Segoe UI", size, style); }
    }

    internal static void Apply(Form form)
    {
        form.Font = AppFont();
        form.BackColor = Canvas;
        form.ForeColor = Ink;
        form.AutoScaleMode = AutoScaleMode.Dpi;
        ApplyTo(form.Controls);
    }

    internal static void ApplyTo(Control.ControlCollection controls)
    {
        foreach (Control control in controls)
        {
            switch (control)
            {
                case Button button:
                    StyleButton(button, IsPrimaryAction(button.Text), IsDangerAction(button.Text));
                    break;
                case TextBox textBox:
                    StyleInput(textBox);
                    break;
                case ComboBox comboBox:
                    StyleInput(comboBox);
                    break;
                case NumericUpDown numeric:
                    StyleInput(numeric);
                    break;
                case CheckBox checkBox:
                    checkBox.ForeColor = Ink;
                    checkBox.Font = AppFont(9.5f, FontStyle.Regular);
                    break;
                case TabControl tabs:
                    tabs.Font = AppFont(10f, FontStyle.Bold);
                    tabs.Padding = new Point(18, 8);
                    break;
                case TabPage page:
                    page.BackColor = Surface;
                    page.ForeColor = Ink;
                    break;
                case GroupBox group:
                    group.ForeColor = Ink;
                    group.Font = AppFont(9.5f, FontStyle.Bold);
                    group.Padding = new Padding(12);
                    break;
                case Label label when label.Font.Style == FontStyle.Regular:
                    label.ForeColor = label.ForeColor == SystemColors.ControlText ? Ink : label.ForeColor;
                    break;
            }

            if (control.HasChildren) ApplyTo(control.Controls);
        }
    }

    internal static void StyleButton(Button button, bool primary = false, bool danger = false)
    {
        button.AutoSize = button.AutoSize;
        button.MinimumSize = new Size(0, 42);
        button.Padding = new Padding(14, 7, 14, 7);
        button.Margin = new Padding(4, 5, 6, 5);
        button.Font = AppFont(9.3f, FontStyle.Bold);
        button.Cursor = Cursors.Hand;
        button.FlatStyle = FlatStyle.Flat;
        button.UseVisualStyleBackColor = false;
        button.BackColor = danger ? Danger : primary ? Primary : SurfaceSoft;
        button.ForeColor = danger || primary ? Color.White : Ink;
        button.FlatAppearance.BorderColor = danger ? Danger : primary ? Primary : ControlLine;
        button.FlatAppearance.BorderSize = 0;
        button.FlatAppearance.MouseOverBackColor = danger
            ? Color.FromArgb(139, 35, 31)
            : primary ? PrimaryHover : Color.FromArgb(232, 238, 255);
        button.FlatAppearance.MouseDownBackColor = danger
            ? Color.FromArgb(120, 31, 27)
            : primary ? Color.FromArgb(41, 54, 152) : Color.FromArgb(215, 224, 255);
        Round(button, 11);
    }

    internal static void StyleInput(Control input)
    {
        input.Font = AppFont(10f);
        input.BackColor = Surface;
        input.ForeColor = Ink;
        if (FramedInputs.Contains(input))
        {
            input.Margin = Padding.Empty;
            switch (input)
            {
                case TextBox textBox:
                    textBox.BorderStyle = BorderStyle.None;
                    break;
                case ComboBox comboBox:
                    comboBox.FlatStyle = FlatStyle.Flat;
                    break;
            }
            return;
        }

        input.MinimumSize = new Size(input.MinimumSize.Width, 42);
        input.Margin = new Padding(0, 4, 0, 8);
        switch (input)
        {
            case TextBox textBox:
                textBox.BorderStyle = BorderStyle.FixedSingle;
                break;
            case ComboBox comboBox:
                comboBox.FlatStyle = FlatStyle.Flat;
                break;
        }
    }

    internal static Panel InputFrame(Control input, int width = 0)
    {
        FramedInputs.Add(input);
        var frame = new Panel
        {
            Name = "LumiInputFrame",
            Height = 44,
            Width = width > 0 ? width : Math.Max(160, input.Width),
            BackColor = ControlLine,
            Padding = new Padding(1),
            Margin = new Padding(0, 3, 0, 8)
        };
        var surface = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Surface,
            Padding = input is ComboBox
                ? new Padding(7, 6, 7, 0)
                : new Padding(10, 9, 10, 0)
        };
        input.Dock = DockStyle.Top;
        input.Margin = Padding.Empty;
        input.BackColor = Surface;
        input.ForeColor = Ink;
        input.Font = AppFont(10f);
        if (input is TextBox textBox) textBox.BorderStyle = BorderStyle.None;
        if (input is ComboBox comboBox) comboBox.FlatStyle = FlatStyle.Flat;
        surface.Controls.Add(input);
        frame.Controls.Add(surface);
        return frame;
    }

    internal static void StyleCard(Control control, int radius = 18)
    {
        control.BackColor = Surface;
        control.ForeColor = Ink;
        Round(control, radius);
    }

    internal static void Round(Control control, int radius)
    {
        void ApplyRegion()
        {
            if (control.Width <= 1 || control.Height <= 1) return;
            using var path = RoundedRectangle(new Rectangle(0, 0, control.Width, control.Height), radius);
            control.Region?.Dispose();
            control.Region = new Region(path);
        }

        ApplyRegion();
        control.Resize += (_, _) => ApplyRegion();
    }

    private static GraphicsPath RoundedRectangle(Rectangle bounds, int radius)
    {
        int diameter = Math.Max(2, radius * 2);
        int right = bounds.Right - 1;
        int bottom = bounds.Bottom - 1;
        var path = new GraphicsPath();
        path.AddArc(bounds.Left, bounds.Top, diameter, diameter, 180, 90);
        path.AddArc(right - diameter, bounds.Top, diameter, diameter, 270, 90);
        path.AddArc(right - diameter, bottom - diameter, diameter, diameter, 0, 90);
        path.AddArc(bounds.Left, bottom - diameter, diameter, diameter, 90, 90);
        path.CloseFigure();
        return path;
    }

    private static bool IsPrimaryAction(string? text)
    {
        string value = (text ?? string.Empty).ToUpperInvariant();
        return value.Contains("XÁC NHẬN") || value.Contains("ĐĂNG NHẬP")
            || value.Contains("LƯU ") || value.StartsWith("LƯU")
            || value.Contains("TRA CỨU") || value.Contains("XỬ LÝ SONG SONG")
            || value.Contains("LÀM MỚI");
    }

    private static bool IsDangerAction(string? text)
    {
        string value = (text ?? string.Empty).ToUpperInvariant();
        return value.Contains("KẾT THÚC PHIÊN") || value.Contains("THU HỒI")
            || value.Contains("XÓA ");
    }
}
