package com.financebot.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferCreateRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate transferDate,
        @Size(max = 500) String description,
        @NotNull Long sourceAccountId,
        @NotNull Long destinationAccountId
) {
}
