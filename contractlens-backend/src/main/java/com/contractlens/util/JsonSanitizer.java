package com.contractlens.util;

public final class JsonSanitizer {

    private JsonSanitizer() {
    }

    public static String extractJsonObject(String raw) {
        if (raw == null) {
            return null;
        }

        String s = raw.trim();
        if (s.isEmpty()) {
            return s;
        }

        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0 && firstNewline + 1 < s.length()) {
                s = s.substring(firstNewline + 1);
            }

            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }

            s = s.trim();
        }

        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1).trim();
        }

        return s;
    }
}
