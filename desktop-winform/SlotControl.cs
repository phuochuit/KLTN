namespace ParkingGateDesktop;

public sealed class SlotControl : UserControl
{
    private readonly Label _title = new(){Dock=DockStyle.Top,Height=28,TextAlign=ContentAlignment.MiddleCenter,Font=new Font("Segoe UI",11,FontStyle.Bold)};
    private readonly Label _detail = new(){Dock=DockStyle.Fill,TextAlign=ContentAlignment.MiddleCenter};
    public SlotControl(){Size=new Size(145,92);Margin=new Padding(5);BorderStyle=BorderStyle.FixedSingle;Controls.Add(_detail);Controls.Add(_title);}
    public void Update(ParkingSlotResponse slot){_title.Text=slot.SlotCode;_detail.Text=string.IsNullOrWhiteSpace(slot.OccupiedPlate)?(string.IsNullOrWhiteSpace(slot.AssignedPlate)?"Trống":"Cấp: "+slot.AssignedPlate):"Đỗ: "+slot.OccupiedPlate;BackColor=slot.Status switch{"AVAILABLE"=>Color.LightGreen,"RESERVED_EMPTY"=>Color.LightSkyBlue,"OCCUPIED_VALID"=>Color.IndianRed,_=>Color.Khaki};}
}
