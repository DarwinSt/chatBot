package com.financebot.service.telegram;

import com.financebot.dto.response.AccountResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.dto.response.MonthlySummaryResponse;
import com.financebot.service.AccountService;
import com.financebot.service.CreditCardService;
import com.financebot.service.DebtService;
import com.financebot.service.ReportService;
import com.financebot.util.CreditCardPaymentSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * Respuestas de texto para consultas rápidas (solo lectura vía servicios de dominio).
 */
@Service
public class TelegramQuickReplyService {

    private static final Logger log = LoggerFactory.getLogger(TelegramQuickReplyService.class);
    private static final DecimalFormat MONEY_FMT;
    private static final DateTimeFormatter FECHA_CORTA =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag("es-CO"));

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
        appendDeudasFormatoBloque(sb, debtService.listActiveDebts());
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public String verTarjetas() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tarjetas activas:\n");
        appendTarjetasFormatoBloque(sb, creditCardService.listActive());
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
        appendTarjetasFormatoBloque(sb, creditCardService.listActive());
        var debts = debtService.listActiveDebts();
        BigDecimal total = debts.stream()
                .map(DebtResponse::pendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sb.append("\nDeudas activas (total pendiente: ").append(fmt(total)).append("):\n");
        appendDeudasFormatoBloque(sb, debts);
        return sb.toString().trim();
    }

    private String fmt(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return MONEY_FMT.format(safe);
    }

    private void appendTarjetasFormatoBloque(StringBuilder sb, List<CreditCardResponse> cards) {
        if (cards.isEmpty()) {
            sb.append("(No hay tarjetas activas.)\n");
            return;
        }
        boolean first = true;
        for (CreditCardResponse c : cards) {
            if (!first) {
                sb.append('\n');
            }
            first = false;
            appendTarjetaBloque(sb, c);
        }
    }

    /**
     * Formato tipo:
     * <pre>
     * *(Nombre)*:
     * Cupo total:
     * Cupo usado:
     * Disponible:
     * Fecha de pago:
     * </pre>
     */
    private void appendTarjetaBloque(StringBuilder sb, CreditCardResponse c) {
        sb.append(c.name()).append(":\n");
        sb.append("- Cupo total: ").append(fmt(c.totalLimit())).append('\n');
        sb.append("- Cupo usado: ").append(fmt(c.usedAmount())).append('\n');
        sb.append("- Disponible: ").append(fmt(c.availableCredit())).append('\n');
        sb.append("- Fecha de pago: ").append(textoFechaPagoTarjeta(c)).append('\n');
    }

    private String textoFechaPagoTarjeta(CreditCardResponse c) {
        if (c.paymentDueDay() == null) {
            return "(sin día configurado)";
        }
        LocalDate proximo = CreditCardPaymentSchedule.nextPaymentDueDate(LocalDate.now(), c.paymentDueDay());
        if (proximo == null) {
            return "(día inválido)";
        }
        return proximo.format(FECHA_CORTA) + " (día " + c.paymentDueDay() + " del mes)";
    }

    private void appendDeudasFormatoBloque(StringBuilder sb, List<DebtResponse> debts) {
        if (debts.isEmpty()) {
            sb.append("(No hay deudas activas.)\n");
            return;
        }
        boolean first = true;
        for (DebtResponse d : debts) {
            if (!first) {
                sb.append('\n');
            }
            first = false;
            appendDeudaBloque(sb, d);
        }
    }

    /**
     * Formato tipo:
     * <pre>
     * (Nombre):
     * - Pendiente:
     * - Acreedor:
     * </pre>
     */
    private void appendDeudaBloque(StringBuilder sb, DebtResponse d) {
        sb.append(d.name()).append(":\n");
        sb.append("- Pendiente: ").append(fmt(d.pendingAmount())).append('\n');
        sb.append("- Acreedor: ").append(textoAcreedor(d.creditor())).append('\n');
    }

    private String textoAcreedor(String creditor) {
        if (creditor == null || creditor.isBlank()) {
            return "(sin acreedor)";
        }
        return creditor.trim();
    }
}
