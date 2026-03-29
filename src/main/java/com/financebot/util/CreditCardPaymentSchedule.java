package com.financebot.util;

import java.time.LocalDate;

/**
 * Calcula la próxima fecha de vencimiento de pago a partir del día del mes configurado en la tarjeta.
 */
public final class CreditCardPaymentSchedule {

    private CreditCardPaymentSchedule() {
    }

    /**
     * Próxima fecha en la que vence el pago (incluye hoy si hoy es el día de pago).
     *
     * @param from           fecha de referencia (normalmente hoy)
     * @param paymentDueDay  día del mes (1–31), o null si no está configurado
     */
    public static LocalDate nextPaymentDueDate(LocalDate from, Short paymentDueDay) {
        if (from == null || paymentDueDay == null) {
            return null;
        }
        int day = paymentDueDay.intValue();
        if (day < 1 || day > 31) {
            return null;
        }
        LocalDate inThisMonth = from.withDayOfMonth(Math.min(day, from.lengthOfMonth()));
        if (!inThisMonth.isBefore(from)) {
            return inThisMonth;
        }
        LocalDate nextMonth = from.plusMonths(1);
        return nextMonth.withDayOfMonth(Math.min(day, nextMonth.lengthOfMonth()));
    }
}
