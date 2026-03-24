package com.financebot.dto.response;

import java.math.BigDecimal;

public record CategoryAmountResponse(
        Long categoryId,
        String categoryName,
        BigDecimal totalAmount
) {
}
