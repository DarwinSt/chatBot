package com.financebot.dto.response;

import com.financebot.enums.DebtStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DebtResponse(
        Long id,
        String name,
        BigDecimal totalAmount,
        BigDecimal pendingAmount,
        LocalDate startDate,
        LocalDate dueDate,
        String creditor,
        String notes,
        DebtStatus status,
        CategoryRefResponse category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
