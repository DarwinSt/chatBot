package com.financebot.dto.response;

import com.financebot.enums.CategoryType;

public record CategoryRefResponse(
        Long id,
        String name,
        CategoryType type
) {
}
