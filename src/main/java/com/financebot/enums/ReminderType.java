package com.financebot.enums;

import java.util.Locale;

public enum ReminderType {
    PAGO_DEUDA,
    PAGO_TARJETA,
    GASTO_FIJO,
    GENERAL;

    public static ReminderType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (t) {
            case "PAGO_DEUDA", "DEBT_PAYMENT" -> PAGO_DEUDA;
            case "PAGO_TARJETA", "CREDIT_CARD_PAYMENT" -> PAGO_TARJETA;
            case "GASTO_FIJO", "FIXED_EXPENSE" -> GASTO_FIJO;
            case "GENERAL" -> GENERAL;
            default -> {
                try {
                    yield ReminderType.valueOf(t);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }
}
