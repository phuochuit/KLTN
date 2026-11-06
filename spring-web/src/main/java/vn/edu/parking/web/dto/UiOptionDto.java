package vn.edu.parking.web.dto;

public class UiOptionDto {
    private String value;
    private String label;
    private int useCount;

    public UiOptionDto() {}

    public UiOptionDto(String value, String label, int useCount) {
        this.value = value;
        this.label = label;
        this.useCount = useCount;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) { this.useCount = useCount; }
}
