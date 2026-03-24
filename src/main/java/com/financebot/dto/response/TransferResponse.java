package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransferResponse(
        Long id,
        BigDecimal amount,
        LocalDate transferDate,
        String description,
        Long sourceAccountId,
        String sourceAccountName,
        Long destinationAccountId,
        String destinationAccountName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
