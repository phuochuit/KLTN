package vn.edu.parking.domain;

public enum OutboxStatus {
    PENDING("Chờ xử lý"),
    SENT("Đã gửi"),
    FAILED_RETRY("Lỗi - Chờ thử lại"),
    DEAD_LETTER("Chết - Cần can thiệp");

    private final String displayName;

    OutboxStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
