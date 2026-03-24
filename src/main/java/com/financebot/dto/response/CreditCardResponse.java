package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditCardResponse(
        Long id,
        String name,
        BigDecimal totalLimit,
        BigDecimal usedAmount,
        BigDecimal availableCredit,
        boolean active,
        Short statementCutoffDay,
        Short paymentDueDay,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
