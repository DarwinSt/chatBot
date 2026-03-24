package com.financebot.dto.request;

import com.financebot.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType type,
        @NotNull @PositiveOrZero BigDecimal initialBalance,
        @Size(max = 500) String notes,
        Boolean active
) {
}
