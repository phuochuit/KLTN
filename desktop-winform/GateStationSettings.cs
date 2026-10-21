namespace ParkingGateDesktop;

public sealed class GateStationSettings
{
    public string VehicleCameraSource { get; set; } = "0";
    public string FaceCameraSource { get; set; } = "0";
    public int CameraFrameCount { get; set; } = 8;
    public int BarrierAutoCloseSeconds { get; set; } = 5;
    public string BarrierDriver { get; set; } = "SIMULATOR";

    public int VehicleCameraIndex => CameraIndex(VehicleCameraSource, 0);
    public int FaceCameraIndex => CameraIndex(FaceCameraSource, 0);
    public bool UsesNetworkCamera => IsNetworkSource(VehicleCameraSource) || IsNetworkSource(FaceCameraSource);

    public void Validate()
    {
        ValidateCameraSource(VehicleCameraSource, "Camera xe");
        ValidateCameraSource(FaceCameraSource, "Camera khuôn mặt");
        if (CameraFrameCount is < 1 or > 60)
            throw new ArgumentException("Số khung hình phải từ 1 đến 60.");
        if (BarrierAutoCloseSeconds is < 1 or > 120)
            throw new ArgumentException("Thời gian tự đóng barrier phải từ 1 đến 120 giây.");
        if (!string.Equals(BarrierDriver, "SIMULATOR", StringComparison.OrdinalIgnoreCase))
            throw new ArgumentException("Driver barrier chưa được hỗ trợ. Hiện tại hãy chọn Mô phỏng.");
    }

    public GateStationSettings Copy() => new()
    {
        VehicleCameraSource = VehicleCameraSource,
        FaceCameraSource = FaceCameraSource,
        CameraFrameCount = CameraFrameCount,
        BarrierAutoCloseSeconds = BarrierAutoCloseSeconds,
        BarrierDriver = BarrierDriver
    };

    public static bool IsNetworkSource(string? source) =>
        Uri.TryCreate(source, UriKind.Absolute, out var uri)
        && (uri.Scheme.Equals("rtsp", StringComparison.OrdinalIgnoreCase)
            || uri.Scheme.Equals("http", StringComparison.OrdinalIgnoreCase)
            || uri.Scheme.Equals("https", StringComparison.OrdinalIgnoreCase));

    private static int CameraIndex(string? source, int fallback) =>
        int.TryParse(source, out int value) && value >= 0 ? value : fallback;

    private static void ValidateCameraSource(string? source, string label)
    {
        if (string.IsNullOrWhiteSpace(source)) throw new ArgumentException($"{label} không được để trống.");
        if (int.TryParse(source, out int index))
        {
            if (index < 0) throw new ArgumentException($"{label} phải có chỉ số từ 0 trở lên.");
            return;
        }
        if (!IsNetworkSource(source))
            throw new ArgumentException($"{label} phải là chỉ số USB hoặc địa chỉ RTSP/HTTP hợp lệ.");
    }
}
