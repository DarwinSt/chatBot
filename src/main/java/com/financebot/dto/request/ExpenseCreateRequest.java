package com.financebot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Origen del gasto obligatorio vía {@code origin} (se persiste en la descripción del movimiento).
 */
public record ExpenseCreateRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate expenseDate,
        @NotBlank @Size(max = 500) String origin,
        @Size(max = 500) String additionalNotes,
        @NotNull Long categoryId,
        Long paymentAccountId,
        Long creditCardId
) {
}
