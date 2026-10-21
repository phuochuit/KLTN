namespace ParkingGateDesktop;

public enum BarrierPosition { Closed, Open, Fault }

public sealed record BarrierStatus(BarrierPosition Position, string Message, DateTimeOffset ChangedAt);

public interface IBarrierController : IDisposable
{
    BarrierStatus Status { get; }
    event Action<BarrierStatus>? StatusChanged;
    Task OpenAsync(string reason, CancellationToken cancellationToken = default);
    Task CloseAsync(string reason, CancellationToken cancellationToken = default);
}

public sealed class SimulatedBarrierController : IBarrierController
{
    public BarrierStatus Status { get; private set; } =
        new(BarrierPosition.Closed, "Mô phỏng: đang đóng", DateTimeOffset.Now);
    public event Action<BarrierStatus>? StatusChanged;

    public Task OpenAsync(string reason, CancellationToken cancellationToken = default) =>
        ChangeAsync(BarrierPosition.Open, reason, cancellationToken);

    public Task CloseAsync(string reason, CancellationToken cancellationToken = default) =>
        ChangeAsync(BarrierPosition.Closed, reason, cancellationToken);

    private async Task ChangeAsync(BarrierPosition position, string reason, CancellationToken cancellationToken)
    {
        await Task.Delay(120, cancellationToken);
        Status = new BarrierStatus(position, $"Mô phỏng: {reason}", DateTimeOffset.Now);
        StatusChanged?.Invoke(Status);
    }

    public void Dispose() { }
}
