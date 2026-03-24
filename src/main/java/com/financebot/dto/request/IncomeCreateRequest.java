package com.financebot.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeCreateRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate incomeDate,
        @Size(max = 100) String origin,
        @Size(max = 500) String description,
        @NotNull Long categoryId,
        @NotNull Long destinationAccountId
) {
}
