namespace ParkingGateDesktop;

public sealed class OfflineQueuedException : Exception
{
    public int PendingCount { get; }

    public OfflineQueuedException(int pendingCount, Exception innerException)
        : base($"Mất kết nối Spring. Thao tác đã được lưu an toàn và sẽ tự gửi lại " +
            $"sau khi kết nối phục hồi. Đang chờ: {pendingCount} thao tác.", innerException)
    {
        PendingCount = pendingCount;
    }
}
