package vn.edu.parking.domain;

public enum PaymentMethod {
    CASH("Tiền mặt", false),
    BANK_TRANSFER("Chuyển khoản", true),
    POS("Máy POS", true);

    private final String displayName;
    private final boolean externalReferenceRequired;
    PaymentMethod(String displayName, boolean externalReferenceRequired) {
        this.displayName = displayName;
        this.externalReferenceRequired = externalReferenceRequired;
    }
    public String getDisplayName() { return displayName; }
    public boolean isExternalReferenceRequired() { return externalReferenceRequired; }
}
