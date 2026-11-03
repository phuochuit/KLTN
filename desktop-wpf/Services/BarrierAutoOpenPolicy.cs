namespace LumiPark.Desktop.Services;

public static class BarrierAutoOpenPolicy
{
    public static bool ShouldOpenAfterConfirmation(GateTransactionConfirmed confirmation) =>
        confirmation.Response.Status is "OPEN" or "COMPLETED"
        && (!confirmation.Response.Warning || confirmation.ManualOverride);
}
