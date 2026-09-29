package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PlateNormalizerTest {
    @Test
    void normalizesVietnamesePlateFormatting() {
        assertThat(PlateNormalizer.normalize("59-A1 123.45")).isEqualTo("59A112345");
    }
}
