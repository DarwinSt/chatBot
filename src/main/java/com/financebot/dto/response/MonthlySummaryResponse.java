package com.financebot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Período: desde el día 1 del mes actual hasta la fecha actual (inclusive).
 * <p>
 * {@code netCashFlow} = ingresos - gastos desde cuenta - pagos a tarjeta - abonos a deudas
 * (los cargos a tarjeta no descuentan cuenta hasta que se paguen).
 */
public record MonthlySummaryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalIncomes,
        BigDecimal totalExpensesAll,
        BigDecimal totalExpensesFromAccounts,
        BigDecimal totalCreditCardCharges,
        BigDecimal totalCreditCardPayments,
        BigDecimal totalDebtPayments,
        BigDecimal netCashFlow,
        List<AccountBalanceResponse> accountBalances,
        List<CreditCardDebtResponse> creditCardDebts,
        List<DebtResponse> activeDebts,
        List<DueEventResponse> upcomingDueDates,
        List<CategoryAmountResponse> incomesByCategory,
        List<CategoryAmountResponse> expensesByCategory
) {
}
