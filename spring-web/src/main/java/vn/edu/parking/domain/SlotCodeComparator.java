package vn.edu.parking.domain;

import java.io.Serializable;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlotCodeComparator implements Comparator<String>, Serializable {

    public static final SlotCodeComparator INSTANCE = new SlotCodeComparator();

    public static final Comparator<ParkingSlot> SLOT_COMPARATOR = (s1, s2) -> {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;
        return INSTANCE.compare(s1.getSlotCode(), s2.getSlotCode());
    };

    private static final Pattern CODE_PATTERN = Pattern.compile("^([A-Za-z_-]*)(\\d+)(.*)$");

    @Override
    public int compare(String c1, String c2) {
        if (c1 == null && c2 == null) return 0;
        if (c1 == null) return -1;
        if (c2 == null) return 1;

        String t1 = c1.trim();
        String t2 = c2.trim();

        Matcher m1 = CODE_PATTERN.matcher(t1);
        Matcher m2 = CODE_PATTERN.matcher(t2);

        if (m1.matches() && m2.matches()) {
            String p1 = m1.group(1).toUpperCase();
            String p2 = m2.group(1).toUpperCase();
            int prefixCmp = p1.compareTo(p2);
            if (prefixCmp != 0) {
                return prefixCmp;
            }

            try {
                long num1 = Long.parseLong(m1.group(2));
                long num2 = Long.parseLong(m2.group(2));
                int numCmp = Long.compare(num1, num2);
                if (numCmp != 0) {
                    return numCmp;
                }
            } catch (NumberFormatException ignored) {
                // Fallback to string comparison if number is too large
            }

            String s1 = m1.group(3);
            String s2 = m2.group(3);
            int suffixCmp = s1.compareToIgnoreCase(s2);
            if (suffixCmp != 0) {
                return suffixCmp;
            }

            return t1.compareToIgnoreCase(t2);
        }

        return t1.compareToIgnoreCase(t2);
    }
}
