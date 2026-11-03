using LumiPark.Desktop.Services;
using ParkingGateDesktop;
using Xunit;

namespace LumiPark.Desktop.Tests;

public sealed class BarrierAutomationTests
{
    [Theory]
    [InlineData("OPEN", false, false, true)]
    [InlineData("COMPLETED", false, false, true)]
    [InlineData("COMPLETED", true, false, false)]
    [InlineData("COMPLETED", true, true, true)]
    [InlineData("PREVIEW", false, true, false)]
    [InlineData("UNKNOWN", false, true, false)]
    public void AutomaticOpenRequiresConfirmedEligibleResponse(string status, bool warning, bool manualOverride, bool expected)
    {
        var now = DateTime.UtcNow;
        var response = new ParkingResponse(1, "TEST-01", "Demo", "CAR", "", status, now, now, 0, "Demo response", warning, null, null, null);
        Assert.Equal(expected, BarrierAutoOpenPolicy.ShouldOpenAfterConfirmation(
            new GateTransactionConfirmed(response, manualOverride, GateDirection.Exit)));
    }

    [Fact]
    public async Task SimulatorHonorsCancellationAndRemainsExplicitlyNonPhysical()
    {
        using var barrier = new SimulatedBarrierController();
        await barrier.OpenAsync("test confirmed event");
        Assert.Equal(BarrierPosition.Open, barrier.Status.Position);
        using var cancelled = new CancellationTokenSource(); cancelled.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => barrier.CloseAsync("cancelled close", cancelled.Token));
        Assert.Equal(BarrierPosition.Open, barrier.Status.Position);
        barrier.Dispose();
        Assert.Equal(BarrierPosition.Open, barrier.Status.Position);
    }
}
