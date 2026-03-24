package com.financebot.dto.request;

import com.financebot.enums.DebtStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive BigDecimal totalAmount,
        BigDecimal pendingAmount,
        LocalDate startDate,
        LocalDate dueDate,
        @Size(max = 120) String creditor,
        @Size(max = 500) String notes,
        DebtStatus status,
        Long categoryId
) {
}
