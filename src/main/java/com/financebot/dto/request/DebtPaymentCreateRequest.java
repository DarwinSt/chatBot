package com.financebot.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtPaymentCreateRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paymentDate,
        @Size(max = 500) String notes,
        @NotNull Long sourceAccountId,
        @NotNull Long debtId
) {
}
