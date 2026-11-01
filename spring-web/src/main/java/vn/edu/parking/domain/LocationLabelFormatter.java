package vn.edu.parking.domain;

public class LocationLabelFormatter {

    public static String format(String floorName, String zoneName, String vehicleTypeName) {
        boolean hasFloor = floorName != null && !floorName.trim().isEmpty() && !floorName.equalsIgnoreCase("null") && !floorName.equalsIgnoreCase("undefined");
        boolean hasZone = zoneName != null && !zoneName.trim().isEmpty() && !zoneName.equalsIgnoreCase("null") && !zoneName.equalsIgnoreCase("undefined");
        boolean hasType = vehicleTypeName != null && !vehicleTypeName.trim().isEmpty() && !vehicleTypeName.equalsIgnoreCase("null") && !vehicleTypeName.equalsIgnoreCase("undefined") && !vehicleTypeName.equalsIgnoreCase("UNKNOWN");

        String typeSuffix = hasType ? " (" + vehicleTypeName.trim() + ")" : "";

        if (hasFloor && hasZone) {
            return floorName.trim() + " - " + zoneName.trim() + typeSuffix;
        } else if (hasZone) {
            return "Chưa xác định tầng - " + zoneName.trim() + typeSuffix;
        } else if (hasFloor) {
            return floorName.trim() + " - Chưa xác định khu" + typeSuffix;
        } else {
            return "Chưa xác định";
        }
    }
}
