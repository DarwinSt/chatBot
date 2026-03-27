package com.financebot.enums;

import java.util.Locale;

public enum DebtStatus {
    ACTIVA,
    PAGADA,
    VENCIDA,
    CANCELADA;

    public static DebtStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "ACTIVA", "ACTIVE" -> ACTIVA;
            case "PAGADA", "PAID" -> PAGADA;
            case "VENCIDA", "OVERDUE" -> VENCIDA;
            case "CANCELADA", "CANCELLED" -> CANCELADA;
            default -> {
                try {
                    yield DebtStatus.valueOf(t);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }
}
