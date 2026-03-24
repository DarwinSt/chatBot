package com.financebot.dto.response;

import com.financebot.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
