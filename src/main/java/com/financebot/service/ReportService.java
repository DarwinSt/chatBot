package com.financebot.service;

import com.financebot.dto.request.RangeReportQuery;
import com.financebot.dto.response.MonthlySummaryResponse;
import com.financebot.dto.response.RangeReportResponse;

public interface ReportService {

    MonthlySummaryResponse getMonthlySummary();

    RangeReportResponse getRangeReport(RangeReportQuery query);
}
