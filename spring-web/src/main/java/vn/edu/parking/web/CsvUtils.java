package vn.edu.parking.web;

public final class CsvUtils {
    public static final String UTF_8_BOM = "\uFEFF";

    private CsvUtils() {}

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
