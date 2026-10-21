namespace ParkingGateDesktop;

public sealed class DesktopAuthorizationException : Exception
{
    public DesktopAuthorizationException(string message) : base(message) { }
}
