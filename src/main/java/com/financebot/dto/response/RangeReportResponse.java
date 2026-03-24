package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RangeReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<IncomeResponse> incomes,
        List<ExpenseResponse> expenses,
        List<CreditCardPaymentResponse> creditCardPayments,
        List<DebtPaymentResponse> debtPayments,
        List<TransferResponse> transfers,
        List<CategoryAmountResponse> incomesByCategory,
        List<CategoryAmountResponse> expensesByCategory,
        BigDecimal totalIncomes,
        BigDecimal totalExpensesAll,
        BigDecimal totalExpensesFromAccounts,
        BigDecimal totalCreditCardCharges,
        BigDecimal totalCreditCardPayments,
        BigDecimal totalDebtPayments,
        BigDecimal netCashFlow
) {
}
