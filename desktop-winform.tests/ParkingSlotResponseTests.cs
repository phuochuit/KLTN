using System.Text.Json;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ParkingGateDesktop;

namespace ParkingGateDesktop.Tests;

[TestClass]
public sealed class ParkingSlotResponseTests
{
    [TestMethod]
    public void OperationalSlotPayloadWithoutResidentContactsDeserializesForMap()
    {
        const string payload = """
            {
              "id": 17,
              "slotCode": "C01",
              "zoneName": "Khu C",
              "floor": "Tầng B1",
              "slotType": "RESIDENT_RESERVED",
              "allowedVehicleType": "MOTORBIKE",
              "statusOverride": "NORMAL",
              "assignedPlate": "51H12345",
              "occupiedPlate": "51H12345",
              "occupiedEntryTime": "2026-10-09T12:30:00",
              "borrowedPlate": "",
              "borrowedUntil": null,
              "borrowNotes": "",
              "overdue": false,
              "status": "OCCUPIED_VALID",
              "statusDescription": "Đang đỗ đúng xe"
            }
            """;

        ParkingSlotResponse slot = JsonSerializer.Deserialize<ParkingSlotResponse>(payload)!;

        Assert.AreEqual("C01", slot.SlotCode);
        Assert.AreEqual("Khu C", slot.ZoneName);
        Assert.AreEqual("51H12345", slot.AssignedPlate);
        Assert.AreEqual("OCCUPIED_VALID", slot.Status);
        Assert.IsNull(slot.AssignedOwnerName);
        Assert.IsNull(slot.AssignedOwnerPhone);
        Assert.IsNull(slot.AssignedApartment);
    }
}
