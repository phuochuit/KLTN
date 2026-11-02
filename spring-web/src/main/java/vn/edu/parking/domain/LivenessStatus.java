package vn.edu.parking.domain;

public enum LivenessStatus {
    NOT_EVALUATED, PASS, REVIEW, FAIL;

    /** Map the ANPR liveness verdict onto the stored gateway status. */
    public static LivenessStatus fromDecision(String decision) {
        if (decision == null) return NOT_EVALUATED;
        return switch (decision.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "LIVE" -> PASS;
            case "SPOOF" -> FAIL;
            case "REVIEW" -> REVIEW;
            default -> NOT_EVALUATED;
        };
    }
}
