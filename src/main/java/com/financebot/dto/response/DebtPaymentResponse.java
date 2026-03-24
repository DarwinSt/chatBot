package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DebtPaymentResponse(
        Long id,
        BigDecimal amount,
        LocalDate paymentDate,
        String notes,
        Long sourceAccountId,
        String sourceAccountName,
        Long debtId,
        String debtName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
