using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using LumiPark.Desktop;
using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace LumiPark.Desktop.Tests;

public sealed class PaymentWindowTests
{
    [Fact]
    public async Task PaymentWindowConstructsAndLoggedOutShellClosesWithoutReentrancy()
    {
        string fixtureDirectory = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../.cache/test-temp", "surface-window-smoke-" + Guid.NewGuid().ToString("N")));
        Directory.CreateDirectory(fixtureDirectory);
        var transport = new DemoTransport();
        var legacy = new OfflineOutboxStore(Path.Combine(fixtureDirectory, "legacy.json"));
        var client = new ParkingApiClient(transport, legacy, new GateStationSettings()) { BaseUrl = "http://localhost:8080" };
        await client.LoginAsync("demo", "demo");
        var journal = await MutationJournal.OpenAsync(Path.Combine(fixtureDirectory, "journal.json"));
        var surfaceServices = new AuthServices(client, journal, legacy);
        var completed = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var thread = new Thread(() =>
        {
            try
            {
                var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
                app.Resources.MergedDictionaries.Add(new ResourceDictionary { Source = new Uri("pack://application:,,,/LumiPark.Desktop;component/Themes/Tokens.xaml") });
                var window = new PaymentPreviewWindow(125000);
                Assert.Equal("125.000 ₫", Assert.IsType<TextBlock>(window.FindName("FeeText")).Text);
                Assert.False(Assert.IsType<Button>(window.FindName("ConfirmButton")).IsDefault);
                Assert.NotNull(window.Content);
                var methods = Assert.IsType<ComboBox>(window.FindName("MethodPicker"));
                methods.SelectedIndex = 1;
                Assert.Equal("BANK_TRANSFER", window.Method);
                Assert.Equal(Visibility.Visible, Assert.IsType<StackPanel>(window.FindName("ReferencePanel")).Visibility);
                Assert.IsType<Button>(window.FindName("ConfirmButton")).RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                Assert.False(string.IsNullOrWhiteSpace(Assert.IsType<TextBlock>(window.FindName("ErrorText")).Text));
                methods.SelectedIndex = 0;
                Assert.Equal(Visibility.Collapsed, Assert.IsType<StackPanel>(window.FindName("ReferencePanel")).Visibility);
                window.Close();
                var pixels = new byte[640 * 480 * 4];
                var source = BitmapSource.Create(640, 480, 96, 96, PixelFormats.Pbgra32, null, pixels, 640 * 4); source.Freeze();
                var zoom = new EvidenceZoomWindow(source, "Ảnh xe"); zoom.SetZoom(4); zoom.Show(); zoom.UpdateLayout();
                app.Dispatcher.Invoke(DispatcherPriority.ApplicationIdle, new Action(() => { }));
                Assert.Equal(4, zoom.ZoomFactor);
                Assert.True(zoom.ImageScrollViewer.ExtentWidth >= source.PixelWidth * 4, "The zoomed image must enlarge the ScrollViewer extent for horizontal panning.");
                Assert.True(zoom.ImageScrollViewer.ExtentHeight >= source.PixelHeight * 4, "The zoomed image must enlarge the ScrollViewer extent for vertical panning.");
                zoom.SetZoom(.4); Assert.Equal(.4, zoom.ZoomFactor); zoom.Close();
                var picker = new ComboBox { Width = 280, ItemsSource = new[] { "Một mục dài để kiểm tra chiều rộng popup", "Mục hai" }, SelectedIndex = 0 };
                var pickerHost = new Window { Width = 360, Height = 140, Content = picker };
                pickerHost.Show(); picker.ApplyTemplate(); picker.IsDropDownOpen = true; picker.UpdateLayout();
                app.Dispatcher.Invoke(DispatcherPriority.ApplicationIdle, new Action(() => { }));
                var popup = Assert.IsType<Popup>(picker.Template.FindName("Popup", picker));
                var popupBorder = Assert.IsType<Border>(popup.Child);
                Assert.True(popupBorder.ActualWidth >= picker.ActualWidth - 1, "The popup must be at least as wide as its ComboBox.");
                pickerHost.Close();
                var dtoPicker = new ComboBox { DisplayMemberPath = nameof(UnassignedSessionResponse.PlateNumber), ItemsSource = new[]
                {
                    new UnassignedSessionResponse(1, "TEST-01", "CAR", "now", "Owner metadata")
                }, SelectedIndex = 0 };
                var dtoHost = new Window { Width = 360, Height = 140, Content = dtoPicker };
                dtoHost.Show(); dtoPicker.ApplyTemplate(); dtoPicker.UpdateLayout();
                app.Dispatcher.Invoke(DispatcherPriority.ApplicationIdle, new Action(() => { }));
                Assert.Contains(VisualDescendants(dtoPicker).OfType<TextBlock>(), text => text.Text == "TEST-01");
                Assert.DoesNotContain(VisualDescendants(dtoPicker).OfType<TextBlock>(), text => text.Text.Contains("Owner metadata", StringComparison.Ordinal));
                dtoHost.Close();
                var stationWindow = new StationSettingsWindow(new GateStationSettings(), true);
                Assert.Equal("pack://application:,,,/Assets/Fonts/#Be Vietnam Pro", stationWindow.FontFamily.Source);
                var stationInputs = Descendants(stationWindow.Content).OfType<Control>().Where(control => control is TextBox or ComboBox).ToArray();
                Assert.Equal(4, stationInputs.Length);
                Assert.All(stationInputs, input => Assert.False(string.IsNullOrWhiteSpace(System.Windows.Automation.AutomationProperties.GetName(input))));
                var summaryData = new ShiftSummary(1, "Ca test", "Nhân viên", DateTimeOffset.UtcNow.AddHours(-1), DateTimeOffset.UtcNow.AddHours(7), DateTimeOffset.UtcNow, "Trực", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                var summaryWindow = new ShiftSummaryWindow(summaryData, null, false, true);
                var summaryTexts = Descendants(summaryWindow.Content).OfType<TextBlock>().Select(block => block.Text).ToArray();
                Assert.Contains(summaryTexts, text => text.Contains("Chưa tải được số liệu", StringComparison.Ordinal));
                Assert.DoesNotContain(summaryTexts, text => text.Contains("Outbox", StringComparison.OrdinalIgnoreCase) || text.Contains("WPF", StringComparison.OrdinalIgnoreCase));
                stationWindow.Close(); summaryWindow.Close();
                var session = new AuthSession(() => throw new InvalidOperationException("Login must not run in this test."));
                var shell = new MainWindow(session, null, false);
                bool closed = false;
                var frame = new DispatcherFrame();
                shell.Closed += (_, _) => { closed = true; frame.Continue = false; };
                var timeout = new DispatcherTimer { Interval = TimeSpan.FromSeconds(2) };
                timeout.Tick += (_, _) => frame.Continue = false;
                shell.Show();
                shell.Close();
                timeout.Start();
                Dispatcher.PushFrame(frame);
                timeout.Stop();
                Assert.True(closed, "Closing an unauthenticated shell must defer the final Close until the first Closing event returns.");

                var mapWindow = new ParkingMapWindow(surfaceServices, _ => { }, _ => { }, 1, () => true);
                var healthWindow = new HealthWindow(surfaceServices, () => true);
                mapWindow.Show(); healthWindow.Show(); mapWindow.UpdateLayout(); healthWindow.UpdateLayout();
                Assert.True(mapWindow.IsVisible); Assert.True(healthWindow.IsVisible);
                var surfaceFrame = new DispatcherFrame();
                var surfaceTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(100) };
                surfaceTimer.Tick += (_, _) => surfaceFrame.Continue = false;
                surfaceTimer.Start(); Dispatcher.PushFrame(surfaceFrame); surfaceTimer.Stop();
                var mapRows = Assert.IsType<ListBox>(VisualDescendants(mapWindow).OfType<ListBox>().First());
                Assert.NotEmpty(mapRows.Items);
                mapRows.SelectedIndex = 0; mapWindow.UpdateLayout();
                var mapTexts = VisualDescendants(mapWindow).OfType<TextBlock>().Select(block => block.Text).ToArray();
                Assert.Contains(mapTexts, text => text.Contains("Ô tô 6-7 chỗ", StringComparison.Ordinal));
                Assert.DoesNotContain(mapTexts, text => text.Contains("CAR_6_7", StringComparison.Ordinal));
                mapWindow.Close(); healthWindow.Close();
                app.Dispatcher.Invoke(DispatcherPriority.ApplicationIdle, new Action(() => { }));
                app.Shutdown();
                completed.TrySetResult();
            }
            catch (Exception ex) { completed.TrySetException(ex); }
        }) { IsBackground = true };
        thread.SetApartmentState(ApartmentState.STA);
        try
        {
            thread.Start();
            await completed.Task.WaitAsync(TimeSpan.FromSeconds(30));
        }
        finally
        {
            surfaceServices.Dispose();
            if (Directory.Exists(fixtureDirectory)) Directory.Delete(fixtureDirectory, true);
        }
    }

    private static IEnumerable<DependencyObject> Descendants(object? root)
    {
        if (root is not DependencyObject current) yield break;
        yield return current;
        foreach (var child in LogicalTreeHelper.GetChildren(current))
            if (child is DependencyObject dependency)
                foreach (var descendant in Descendants(dependency)) yield return descendant;
    }

    private static IEnumerable<DependencyObject> VisualDescendants(DependencyObject current)
    {
        yield return current;
        for (int index = 0; index < VisualTreeHelper.GetChildrenCount(current); index++)
            foreach (var descendant in VisualDescendants(VisualTreeHelper.GetChild(current, index))) yield return descendant;
    }
}
