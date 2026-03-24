package com.financebot.controller;

import com.financebot.dto.request.RangeReportQuery;
import com.financebot.dto.response.MonthlySummaryResponse;
import com.financebot.dto.response.RangeReportResponse;
import com.financebot.service.ReportService;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/monthly")
    public MonthlySummaryResponse monthlySummary() {
        return reportService.getMonthlySummary();
    }

    @GetMapping("/range")
    public RangeReportResponse rangeReport(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.getRangeReport(new RangeReportQuery(startDate, endDate));
    }
}
