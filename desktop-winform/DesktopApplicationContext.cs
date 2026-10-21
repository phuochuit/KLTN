namespace ParkingGateDesktop;

internal sealed class DesktopApplicationContext : ApplicationContext
{
    private readonly ParkingApiClient _parkingApi = new();
    private readonly GateStationSettingsStore _settingsStore = new();
    private readonly IBarrierController _barrier = new SimulatedBarrierController();
    private bool _returnToLogin;

    public DesktopApplicationContext()
    {
        ShowLogin();
    }

    private void ShowLogin()
    {
        var login = new DesktopLoginDialog(_parkingApi.LoginAsync);
        MainForm = login;
        login.LoginSucceeded += () =>
        {
            login.Hide();
            ShowGate(login);
        };
        login.FormClosed += (_, _) =>
        {
            if (ReferenceEquals(MainForm, login)) ExitApplication();
        };
        login.Show();
    }

    private void ShowGate(Form login)
    {
        var gate = new MainForm(_parkingApi, _settingsStore, _barrier);
        MainForm = gate;
        gate.LogoutCompleted += () => _returnToLogin = true;
        gate.FormClosed += (_, _) =>
        {
            login.Dispose();
            if (_returnToLogin)
            {
                _returnToLogin = false;
                ShowLogin();
            }
            else ExitApplication();
        };
        gate.Show();
    }

    private void ExitApplication()
    {
        _parkingApi.Dispose();
        _barrier.Dispose();
        ExitThread();
    }
}
