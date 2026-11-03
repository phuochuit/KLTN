using System.Globalization;
using System.Windows;
using System.Windows.Controls;

namespace LumiPark.Desktop;

public partial class PaymentPreviewWindow : Window
{
    public string Method { get; private set; } = "CASH";
    public string Reference => ReferenceBox.Text.Trim();
    public bool RequiresPayment { get; }
    public PaymentPreviewWindow(decimal fee)
    {
        InitializeComponent();
        MaxHeight = Math.Min(760, SystemParameters.WorkArea.Height);
        MinHeight = Math.Min(420, MaxHeight);
        RequiresPayment = fee > 0;
        FeeText.Text = fee.ToString("N0", CultureInfo.GetCultureInfo("vi-VN")) + " ₫";
        if (!RequiresPayment) { MethodPicker.Visibility = Visibility.Collapsed; ReferencePanel.Visibility = Visibility.Collapsed; ConfirmButton.Content = "Xác nhận xe ra"; }
        else { MethodPicker.SelectionChanged += Method_Changed; Method_Changed(this, null); }
    }
    private void Method_Changed(object sender, SelectionChangedEventArgs? e)
    {
        Method = (MethodPicker.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "CASH";
        ReferencePanel.Visibility = Method == "CASH" ? Visibility.Collapsed : Visibility.Visible;
    }
    private void Confirm_Click(object sender, RoutedEventArgs e)
    {
        Method = (MethodPicker.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "CASH";
        if (Method != "CASH" && string.IsNullOrWhiteSpace(Reference))
        { ErrorText.Text = "Vui lòng nhập mã tham chiếu thanh toán."; ReferenceBox.Focus(); return; }
        DialogResult = true;
    }
}
