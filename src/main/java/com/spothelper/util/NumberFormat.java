package com.spothelper.util;

public final class NumberFormat {

    private NumberFormat() {}

    public static String format(long value) {
        boolean negative = value < 0;
        String s = Long.toString(Math.abs(value));

        StringBuilder sb = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }

        return negative ? "-" + sb : sb.toString();
    }

    public static String format(int value) {
        return format((long) value);
    }
}