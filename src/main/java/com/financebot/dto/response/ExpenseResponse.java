package com.financebot.dto.response;

import com.financebot.enums.ExpenseType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpenseResponse(
        Long id,
        BigDecimal amount,
        LocalDate expenseDate,
        String description,
        ExpenseType expenseType,
        CategoryRefResponse category,
        Long paymentAccountId,
        String paymentAccountName,
        Long creditCardId,
        String creditCardName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
