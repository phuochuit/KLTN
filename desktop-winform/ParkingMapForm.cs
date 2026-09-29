namespace ParkingGateDesktop;

public sealed class ParkingMapForm : Form
{
    private readonly ParkingApiClient _api;
    private readonly FlowLayoutPanel _panel=new(){Dock=DockStyle.Fill,AutoScroll=true,Padding=new Padding(10)};
    public ParkingMapForm(ParkingApiClient api){_api=api;Text="Sơ đồ hầm gửi xe - dữ liệu thật";Size=new Size(1000,700);StartPosition=FormStartPosition.CenterParent;Controls.Add(_panel);Shown+=async(_,_)=>await ReloadAsync();}
    private async Task ReloadAsync(){try{_panel.Controls.Clear();foreach(var item in await _api.GetSlotsAsync()){var control=new SlotControl();control.Update(item);_panel.Controls.Add(control);}}catch(Exception ex){MessageBox.Show(ex.Message,"Không tải được sơ đồ",MessageBoxButtons.OK,MessageBoxIcon.Error);}}
}
