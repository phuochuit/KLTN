package vn.edu.parking.service;

import java.util.Locale;

public final class PlateNormalizer {
    private PlateNormalizer() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
