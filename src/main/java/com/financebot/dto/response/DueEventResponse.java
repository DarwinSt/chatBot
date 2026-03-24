package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DueEventResponse(
        DueEventKind kind,
        Long id,
        String title,
        LocalDateTime whenAt,
        BigDecimal amount,
        String notes
) {
}
