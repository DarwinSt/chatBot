package com.financebot.enums;

import java.util.Locale;

public enum AccountType {
    CORRIENTE,
    AHORROS,
    EFECTIVO,
    BILLETERA_DIGITAL;

    /** Acepta etiquetas en español o sinónimos en inglés (API / comandos antiguos). */
    public static AccountType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (t) {
            case "CORRIENTE", "CHECKING" -> CORRIENTE;
            case "AHORROS", "SAVINGS" -> AHORROS;
            case "EFECTIVO", "CASH" -> EFECTIVO;
            case "BILLETERA_DIGITAL", "DIGITAL_WALLET", "WALLET" -> BILLETERA_DIGITAL;
            default -> {
                try {
                    yield AccountType.valueOf(t);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }
}
