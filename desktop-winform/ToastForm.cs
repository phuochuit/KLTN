using System;
using System.Drawing;
using System.Windows.Forms;

namespace ParkingGateDesktop;

/// <summary>
/// Thẻ thông báo nhỏ ở góc phải dưới màn hình. Tự ẩn sau 4 giây,
/// không chặn thao tác và vẫn hiển thị khi cửa sổ trạm gác đã đóng.
/// </summary>
public sealed class ToastForm : Form
{
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 4000 };

    public ToastForm(string message, bool warning = false)
    {
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.Manual;
        ShowInTaskbar = false;
        TopMost = true;
        BackColor = warning ? LumiTheme.Warning : LumiTheme.Good;
        ForeColor = Color.White;
        Font = LumiTheme.AppFont(10f, FontStyle.Bold);

        var area = Screen.PrimaryScreen?.WorkingArea ?? new Rectangle(0, 0, 1024, 768);
        int width = Math.Min(460, Math.Max(300, area.Width - 48));
        var size = TextRenderer.MeasureText(message, Font, new Size(width - 48, int.MaxValue),
            TextFormatFlags.WordBreak);
        Width = width;
        Height = Math.Max(82, size.Height + 38);
        Location = new Point(area.Right - Width - 24, area.Bottom - Height - 24);

        Controls.Add(new Label
        {
            Text = message,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            ForeColor = Color.White,
            Padding = new Padding(24, 14, 24, 14),
            Font = LumiTheme.AppFont(10f, FontStyle.Bold)
        });
        LumiTheme.Round(this, 16);
        _timer.Tick += (_, _) => { _timer.Stop(); Close(); };
    }

    protected override void OnShown(EventArgs e)
    {
        base.OnShown(e);
        _timer.Start();
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        _timer.Stop();
        _timer.Dispose();
        base.OnFormClosed(e);
    }
}
