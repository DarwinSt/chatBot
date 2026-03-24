package com.financebot.dto.response;

import com.financebot.enums.AccountType;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        Long id,
        String name,
        AccountType type,
        BigDecimal currentBalance,
        boolean active
) {
}
