using System.Windows;
using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Windows.Media;
using LumiPark.Desktop.Services;

namespace LumiPark.Desktop;

public partial class App : Application
{
    private void OnStartup(object sender, StartupEventArgs e)
    {
        bool demo = e.Args.Contains("--demo", StringComparer.OrdinalIgnoreCase);
        var screen = e.Args.FirstOrDefault(a => a.StartsWith("--screen=", StringComparison.OrdinalIgnoreCase))?[9..] ?? "login";
        DemoTransport? transport = null;
        AuthSession session;
        if (demo)
        {
            try
            {
                var root = new DirectoryInfo(AppContext.BaseDirectory);
                while (root is not null && !File.Exists(Path.Combine(root.FullName, "desktop-wpf", "LumiPark.Desktop.csproj"))) root = root.Parent;
                if (root is null) throw new ArgumentException("Không tìm thấy thư mục dự án cho trình diễn.");
                var fixture = e.Args.FirstOrDefault(a => a.StartsWith("--fixture-dir=", StringComparison.OrdinalIgnoreCase))?[14..] ?? "";
                var path = AuthComposition.ValidateFixturePath(fixture, root.FullName);
                transport = new DemoTransport();
                var fake = transport;
                session = new AuthSession(() => AuthComposition.DemoAsync(path, fake)) { BaseUrl = "https://demo.invalid" };
            }
            catch (Exception ex) when (ex is ArgumentException or IOException or UnauthorizedAccessException)
            { MessageBox.Show("Không thể mở dữ liệu trình diễn. Hãy kiểm tra thư mục trong .artifacts.", "LumiPark"); Shutdown(1); return; }
        }
        else session = new AuthSession(AuthComposition.ProductionAsync);
        var startup = Stopwatch.StartNew();
        var window = new MainWindow(session, transport, demo && screen.Equals("workspace", StringComparison.OrdinalIgnoreCase));
        // Explicit fixture sizes exercise native WPF layout without changing Windows display settings.
        foreach (var dimension in new[] { "width", "height" })
        {
            var value = e.Args.FirstOrDefault(a => a.StartsWith($"--{dimension}=", StringComparison.OrdinalIgnoreCase))?[(dimension.Length + 3)..];
            if (!double.TryParse(value, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var size) || !double.IsFinite(size)) continue;
            if (dimension == "width") window.Width = Math.Clamp(size, window.MinWidth, window.MaxWidth);
            else window.Height = Math.Clamp(size, window.MinHeight, window.MaxHeight);
        }
        window.Left = SystemParameters.WorkArea.Left + (SystemParameters.WorkArea.Width - window.Width) / 2;
        window.Top = SystemParameters.WorkArea.Top + (SystemParameters.WorkArea.Height - window.Height) / 2;
        var diagnostics = e.Args.FirstOrDefault(a => a.StartsWith("--diagnostics=", StringComparison.OrdinalIgnoreCase))?[14..];
        if (!string.IsNullOrWhiteSpace(diagnostics))
            window.ContentRendered += async (_, _) =>
            {
                var dpi = VisualTreeHelper.GetDpi(window);
                using var process = Process.GetCurrentProcess();
                var sample = new
                {
                    kind = "synthetic first ContentRendered sample, not an end-to-end startup benchmark",
                    firstContentRenderedMs = startup.Elapsed.TotalMilliseconds,
                    dpiX = dpi.DpiScaleX, dpiY = dpi.DpiScaleY,
                    logicalWidth = window.ActualWidth, logicalHeight = window.ActualHeight,
                    workAreaWidth = SystemParameters.WorkArea.Width, workAreaHeight = SystemParameters.WorkArea.Height,
                    workingSetBytes = process.WorkingSet64, privateBytes = process.PrivateMemorySize64,
                    cpuSeconds = process.TotalProcessorTime.TotalSeconds
                };
                try
                {
                    var path = Path.GetFullPath(diagnostics);
                    Directory.CreateDirectory(Path.GetDirectoryName(path)!);
                    await File.WriteAllTextAsync(path, JsonSerializer.Serialize(sample, new JsonSerializerOptions { WriteIndented = true }));
                }
                catch (IOException) { /* Optional local diagnostics cannot block the prototype. */ }
                catch (UnauthorizedAccessException) { }
            };
        MainWindow = window;
        window.Show();
    }
}
