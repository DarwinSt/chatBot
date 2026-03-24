package com.financebot.service.telegram;

import com.financebot.dto.response.AccountResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.dto.response.MonthlySummaryResponse;
import com.financebot.service.AccountService;
import com.financebot.service.CreditCardService;
import com.financebot.service.DebtService;
import com.financebot.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Respuestas de texto para consultas rápidas (solo lectura vía servicios de dominio).
 */
@Service
public class TelegramQuickReplyService {

    private final AccountService accountService;
    private final DebtService debtService;
    private final CreditCardService creditCardService;
    private final ReportService reportService;

    public TelegramQuickReplyService(
            AccountService accountService,
            DebtService debtService,
            CreditCardService creditCardService,
            ReportService reportService) {
        this.accountService = accountService;
        this.debtService = debtService;
        this.creditCardService = creditCardService;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public String verCuentas() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cuentas activas:\n");
        for (AccountResponse a : accountService.listActive()) {
            sb.append("- ").append(a.name())
                    .append(" → saldo ").append(a.currentBalance()).append("\n");
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public String verDeudas() {
        StringBuilder sb = new StringBuilder();
        sb.append("Deudas activas:\n");
        for (DebtResponse d : debtService.listActiveDebts()) {
            sb.append("- ").append(d.name())
                    .append(" → pendiente ").append(d.pendingAmount())
                    .append(" (").append(d.status()).append(")\n");
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public String verTarjetas() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tarjetas activas:\n");
        for (var c : creditCardService.listActive()) {
            sb.append("- ").append(c.name())
                    .append(" | cupo ").append(c.totalLimit())
                    .append(" | usado ").append(c.usedAmount())
                    .append(" | disponible ").append(c.availableCredit()).append("\n");
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public String resumenMes() {
        MonthlySummaryResponse m = reportService.getMonthlySummary();
        return """
                Resumen del mes (%s → %s):
                - Ingresos: %s
                - Gastos (todos): %s
                - Pagos a tarjetas: %s
                - Abonos a deudas: %s
                - Balance neto (caja): %s
                """.formatted(
                m.periodStart(),
                m.periodEnd(),
                m.totalIncomes(),
                m.totalExpensesAll(),
                m.totalCreditCardPayments(),
                m.totalDebtPayments(),
                m.netCashFlow()).trim();
    }

    @Transactional(readOnly = true)
    public String balance() {
        StringBuilder sb = new StringBuilder();
        sb.append("Saldos por cuenta:\n");
        for (AccountResponse a : accountService.listAll()) {
            if (a.active()) {
                sb.append("- ").append(a.name()).append(": ").append(a.currentBalance()).append("\n");
            }
        }
        sb.append("\nTarjetas:\n");
        for (CreditCardResponse c : creditCardService.listAll()) {
            sb.append("- ").append(c.name())
                    .append(" | usado ").append(c.usedAmount())
                    .append(" | disponible ").append(c.availableCredit()).append("\n");
        }
        sb.append("\nDeudas activas (total pendiente): ");
        BigDecimal total = debtService.listActiveDebts().stream()
                .map(DebtResponse::pendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sb.append(total);
        return sb.toString().trim();
    }
}
