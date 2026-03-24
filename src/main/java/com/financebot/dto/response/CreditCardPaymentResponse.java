package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreditCardPaymentResponse(
        Long id,
        BigDecimal amount,
        LocalDate paymentDate,
        String notes,
        Long sourceAccountId,
        String sourceAccountName,
        Long creditCardId,
        String creditCardName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
