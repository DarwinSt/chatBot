package com.financebot.enums;

import java.util.Locale;

public enum CategoryType {
    INGRESO,
    GASTO,
    DEUDA;

    public static CategoryType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "INGRESO", "INCOME", "INGRESOS" -> INGRESO;
            case "GASTO", "EXPENSE", "GASTOS" -> GASTO;
            case "DEUDA", "DEBT", "DEUDAS", "DEBIT", "DEBITO" -> DEUDA;
            default -> {
                try {
                    yield CategoryType.valueOf(t);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }
}
