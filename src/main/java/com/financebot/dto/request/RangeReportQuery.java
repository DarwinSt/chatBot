package com.financebot.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RangeReportQuery(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
