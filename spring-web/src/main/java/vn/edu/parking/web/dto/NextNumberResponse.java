package vn.edu.parking.web.dto;

public class NextNumberResponse {
    private String prefix;
    private String floor;
    private int nextStartNumber;
    private int currentFloorUnits;
    private int maxFloorUnits;

    public NextNumberResponse() {}

    public NextNumberResponse(String prefix, String floor, int nextStartNumber, int currentFloorUnits, int maxFloorUnits) {
        this.prefix = prefix;
        this.floor = floor;
        this.nextStartNumber = nextStartNumber;
        this.currentFloorUnits = currentFloorUnits;
        this.maxFloorUnits = maxFloorUnits;
    }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public int getNextStartNumber() { return nextStartNumber; }
    public void setNextStartNumber(int nextStartNumber) { this.nextStartNumber = nextStartNumber; }

    public int getCurrentFloorUnits() { return currentFloorUnits; }
    public void setCurrentFloorUnits(int currentFloorUnits) { this.currentFloorUnits = currentFloorUnits; }

    public int getMaxFloorUnits() { return maxFloorUnits; }
    public void setMaxFloorUnits(int maxFloorUnits) { this.maxFloorUnits = maxFloorUnits; }
}
