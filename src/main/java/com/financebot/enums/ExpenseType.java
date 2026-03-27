package com.financebot.enums;

import java.util.Locale;

public enum ExpenseType {
    FIJO,
    VARIABLE;

    public static ExpenseType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "FIJO", "FIXED" -> FIJO;
            case "VARIABLE" -> VARIABLE;
            default -> {
                try {
                    yield ExpenseType.valueOf(t);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }
}
