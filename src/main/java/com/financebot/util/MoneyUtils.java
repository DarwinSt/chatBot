package com.financebot.util;

import com.financebot.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class MoneyUtils {

    public static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtils() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return amount.setScale(SCALE, ROUNDING);
    }

    public static void assertPositive(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("El monto debe ser mayor que cero");
        }
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return normalize(a.add(b));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return normalize(a.subtract(b));
    }

    public static boolean isLessThan(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) < 0;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? normalize(a) : normalize(b);
    }

    /**
     * Garantiza que el valor no sea negativo (útil tras restas, p. ej. usedAmount de tarjeta).
     */
    public static BigDecimal atLeastZero(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return normalize(value);
    }
}
