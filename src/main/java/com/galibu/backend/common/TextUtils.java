package com.galibu.backend.common;

public final class TextUtils {

    private TextUtils() {
    }

    public static String capitalizeFirst(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String trimmed = text.stripLeading();
        int prefixLen = text.length() - trimmed.length();
        return text.substring(0, prefixLen)
            + Character.toUpperCase(trimmed.charAt(0))
            + trimmed.substring(1);
    }
}
