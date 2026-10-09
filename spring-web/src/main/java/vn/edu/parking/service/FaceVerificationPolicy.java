package vn.edu.parking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FaceVerificationPolicy {
    private final double reviewThreshold;
    private final double passThreshold;

    public FaceVerificationPolicy(
            @Value("${parking.security.face-review-threshold:0.300}") double reviewThreshold,
            @Value("${parking.security.face-pass-threshold:0.363}") double passThreshold) {
        if (!Double.isFinite(reviewThreshold) || !Double.isFinite(passThreshold)
                || reviewThreshold < 0 || passThreshold > 1 || reviewThreshold >= passThreshold) {
            throw new IllegalArgumentException("Face verification thresholds must satisfy 0 <= review < pass <= 1");
        }
        this.reviewThreshold = reviewThreshold;
        this.passThreshold = passThreshold;
    }

    public String decision(double similarity) {
        if (!Double.isFinite(similarity) || similarity < 0 || similarity > 1)
            throw new IllegalArgumentException("Face similarity must be between 0 and 1");
        if (similarity >= passThreshold) return "PASS";
        if (similarity >= reviewThreshold) return "REVIEW";
        return "REJECT";
    }

    public double reviewThreshold() { return reviewThreshold; }
    public double passThreshold() { return passThreshold; }
}
