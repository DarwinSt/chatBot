package com.financebot.dto.response;

import java.math.BigDecimal;

public record CreditCardDebtResponse(
        Long id,
        String name,
        BigDecimal totalLimit,
        BigDecimal usedAmount,
        BigDecimal availableCredit,
        boolean active
) {
}
