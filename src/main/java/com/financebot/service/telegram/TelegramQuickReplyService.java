package com.financebot.service.telegram;

import com.financebot.dto.response.AccountResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.dto.response.MonthlySummaryResponse;
import com.financebot.service.AccountService;
import com.financebot.service.CreditCardService;
import com.financebot.service.DebtService;
import com.financebot.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Respuestas de texto para consultas rápidas (solo lectura vía servicios de dominio).
 */
@Service
public class TelegramQuickReplyService {

    private static final Logger log = LoggerFactory.getLogger(TelegramQuickReplyService.class);
    private static final DecimalFormat MONEY_FMT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("es-CO"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        MONEY_FMT = new DecimalFormat("#,##0.00", symbols);
    }

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
                    .append(" → saldo ").append(fmt(a.currentBalance())).append("\n");
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public String verDeudas() {
        StringBuilder sb = new StringBuilder();
        sb.append("Deudas activas:\n");
        for (DebtResponse d : debtService.listActiveDebts()) {
            sb.append("- ").append(d.name())
                    .append(" → pendiente ").append(fmt(d.pendingAmount()))
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
                    .append(" | cupo ").append(fmt(c.totalLimit()))
                    .append(" | usado ").append(fmt(c.usedAmount()))
                    .append(" | disponible ").append(fmt(c.availableCredit())).append("\n");
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public String resumenMes() {
        try {
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
                    fmt(m.totalIncomes()),
                    fmt(m.totalExpensesAll()),
                    fmt(m.totalCreditCardPayments()),
                    fmt(m.totalDebtPayments()),
                    fmt(m.netCashFlow())).trim();
        } catch (Exception ex) {
            log.error("Error al generar resumen mensual para Telegram", ex);
            return "No pude generar el resumen mensual en este momento. Intenta de nuevo en unos segundos.";
        }
    }

    @Transactional(readOnly = true)
    public String balance() {
        StringBuilder sb = new StringBuilder();
        sb.append("Saldos por cuenta:\n");
        for (AccountResponse a : accountService.listAll()) {
            if (a.active()) {
                sb.append("- ").append(a.name()).append(": ").append(fmt(a.currentBalance())).append("\n");
            }
        }
        sb.append("\nTarjetas:\n");
        for (CreditCardResponse c : creditCardService.listActive()) {
            sb.append("- ").append(c.name())
                    .append(" | usado ").append(fmt(c.usedAmount()))
                    .append(" | disponible ").append(fmt(c.availableCredit())).append("\n");
        }
        var debts = debtService.listActiveDebts();
        BigDecimal total = debts.stream()
                .map(DebtResponse::pendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sb.append("\nDeudas activas (total pendiente: ").append(fmt(total)).append("):\n");
        for (DebtResponse d : debts) {
            sb.append("- ")
                    .append(d.name())
                    .append(" | pendiente ").append(fmt(d.pendingAmount()))
                    .append(" | estado ").append(d.status());
            if (d.dueDate() != null) {
                sb.append(" | vence ").append(d.dueDate());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String fmt(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return MONEY_FMT.format(safe);
    }
}
