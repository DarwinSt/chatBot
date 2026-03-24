package com.financebot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreditCardCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive BigDecimal totalLimit,
        @PositiveOrZero BigDecimal usedAmount,
        Short statementCutoffDay,
        Short paymentDueDay,
        @Size(max = 500) String notes
) {
}
