package com.financebot.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class TelegramParsingUtils {

    private TelegramParsingUtils() {
    }

    public static Optional<BigDecimal> parseAmount(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            String normalized = text.trim().replace(',', '.');
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Optional<LocalDate> parseIsoDate(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text.trim()));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    public static Optional<Integer> parsePositiveInt(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            int n = Integer.parseInt(text.trim());
            if (n <= 0) {
                return Optional.empty();
            }
            return Optional.of(n);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
