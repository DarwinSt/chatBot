package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record IncomeResponse(
        Long id,
        BigDecimal amount,
        LocalDate incomeDate,
        String origin,
        String description,
        CategoryRefResponse category,
        Long destinationAccountId,
        String destinationAccountName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
