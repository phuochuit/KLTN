using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using LumiPark.Desktop.Services;

namespace LumiPark.Desktop;

public sealed class DemoToolsWindow : Window
{
    public DemoToolsWindow(DemoTransport transport)
    {
        Title = "Công cụ trình diễn"; SurfaceUi.Configure(this, 440, 520, 420, 480);
        var panel = new StackPanel { Margin = new Thickness(24) };
        panel.Children.Add(SurfaceUi.Text("CHỈ DỮ LIỆU MINH HỌA · KHÔNG KẾT NỐI DỊCH VỤ THẬT", 12, FontWeights.Bold));
        panel.Children.Add(new TextBlock { Text = "Chỉ điều khiển dữ liệu minh họa trong bộ nhớ. Không thay đổi hoặc xóa journal thao tác.", TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 14) });
        AddToggle(panel, "Hồ sơ thành viên đã đăng ký", transport.RegisteredMember, value => transport.RegisteredMember = value);
        AddToggle(panel, "Có ảnh khuôn mặt lúc xe vào (khách)", transport.GuestEntryFaceAvailable, value => transport.GuestEntryFaceAvailable = value);
        AddToggle(panel, "Gói thành viên hết hạn", transport.ExpiredPass, value => transport.ExpiredPass = value);
        AddToggle(panel, "Đối chiếu khuôn mặt cần xem xét", transport.FaceReview, value => transport.FaceReview = value);
        AddToggle(panel, "Phản hồi chậm 2 giây", transport.SlowResponses, value => transport.SlowResponses = value);
        AddToggle(panel, "Lỗi dịch vụ AI", transport.ErrorResponses, value => transport.ErrorResponses = value);
        AddToggle(panel, "Thao tác trả 503 · trạng thái chưa rõ", transport.UnknownMutation, value => transport.UnknownMutation = value);
        var expire = new Button { Content = "Kết thúc phiên minh họa", Margin = new Thickness(0, 12, 0, 0) };
        var restore = new Button { Content = "Khôi phục phiên minh họa", Margin = new Thickness(0, 8, 0, 0) };
        expire.Click += (_, _) => transport.SessionValid = false; restore.Click += (_, _) => transport.SessionValid = true;
        panel.Children.Add(expire); panel.Children.Add(restore); Content = panel;
    }
    private static void AddToggle(Panel panel, string label, bool initial, Action<bool> update)
    {
        var check = new CheckBox { Content = label, IsChecked = initial, Margin = new Thickness(0, 4, 0, 4), MinHeight = 30 };
        AutomationProperties.SetName(check, label);
        check.Checked += (_, _) => update(true); check.Unchecked += (_, _) => update(false); panel.Children.Add(check);
    }
}
