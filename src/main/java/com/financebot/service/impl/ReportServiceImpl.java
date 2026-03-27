package com.financebot.service.impl;

import com.financebot.dto.request.RangeReportQuery;
import com.financebot.dto.response.AccountBalanceResponse;
import com.financebot.dto.response.CategoryAmountResponse;
import com.financebot.dto.response.CreditCardDebtResponse;
import com.financebot.dto.response.CreditCardPaymentResponse;
import com.financebot.dto.response.DebtPaymentResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.dto.response.DueEventKind;
import com.financebot.dto.response.DueEventResponse;
import com.financebot.dto.response.ExpenseResponse;
import com.financebot.dto.response.IncomeResponse;
import com.financebot.dto.response.MonthlySummaryResponse;
import com.financebot.dto.response.RangeReportResponse;
import com.financebot.dto.response.TransferResponse;
import com.financebot.entity.Debt;
import com.financebot.entity.Reminder;
import com.financebot.enums.DebtStatus;
import com.financebot.exception.BusinessRuleException;
import com.financebot.mapper.*;
import com.financebot.repository.AccountRepository;
import com.financebot.repository.CreditCardPaymentRepository;
import com.financebot.repository.CreditCardRepository;
import com.financebot.repository.DebtRepository;
import com.financebot.repository.DebtPaymentRepository;
import com.financebot.repository.ExpenseRepository;
import com.financebot.repository.IncomeRepository;
import com.financebot.repository.ReminderRepository;
import com.financebot.repository.TransferRepository;
import com.financebot.service.DebtService;
import com.financebot.service.ReportService;
import com.financebot.util.MoneyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ReportServiceImpl implements ReportService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final CreditCardPaymentRepository creditCardPaymentRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final ReminderRepository reminderRepository;
    private final DebtRepository debtRepository;

    private final DebtService debtService;

    private final AccountMapper accountMapper;
    private final CreditCardMapper creditCardMapper;
    private final IncomeMapper incomeMapper;
    private final ExpenseMapper expenseMapper;
    private final CreditCardPaymentMapper creditCardPaymentMapper;
    private final DebtPaymentMapper debtPaymentMapper;
    private final TransferMapper transferMapper;

    public ReportServiceImpl(
            IncomeRepository incomeRepository,
            ExpenseRepository expenseRepository,
            CreditCardPaymentRepository creditCardPaymentRepository,
            DebtPaymentRepository debtPaymentRepository,
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            CreditCardRepository creditCardRepository,
            ReminderRepository reminderRepository,
            DebtRepository debtRepository,
            DebtService debtService,
            AccountMapper accountMapper,
            CreditCardMapper creditCardMapper,
            IncomeMapper incomeMapper,
            ExpenseMapper expenseMapper,
            CreditCardPaymentMapper creditCardPaymentMapper,
            DebtPaymentMapper debtPaymentMapper,
            TransferMapper transferMapper) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.creditCardPaymentRepository = creditCardPaymentRepository;
        this.debtPaymentRepository = debtPaymentRepository;
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.reminderRepository = reminderRepository;
        this.debtRepository = debtRepository;
        this.debtService = debtService;
        this.accountMapper = accountMapper;
        this.creditCardMapper = creditCardMapper;
        this.incomeMapper = incomeMapper;
        this.expenseMapper = expenseMapper;
        this.creditCardPaymentMapper = creditCardPaymentMapper;
        this.debtPaymentMapper = debtPaymentMapper;
        this.transferMapper = transferMapper;
    }

    @Override
    @Transactional
    public MonthlySummaryResponse getMonthlySummary() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today;

        BigDecimal totalIncomes = nz(incomeRepository.sumAmountBetween(start, end));
        BigDecimal totalExpensesAll = nz(expenseRepository.sumAmountBetween(start, end));
        BigDecimal totalExpensesFromAccounts = nz(
                expenseRepository.sumAmountBetweenAndPaymentAccountPresent(start, end));
        BigDecimal totalCreditCardCharges = nz(
                expenseRepository.sumAmountBetweenAndCreditCardPresent(start, end));
        BigDecimal totalCreditCardPayments = nz(creditCardPaymentRepository.sumAmountBetween(start, end));
        BigDecimal totalDebtPayments = nz(debtPaymentRepository.sumAmountBetween(start, end));

        BigDecimal netCashFlow = MoneyUtils.normalize(
                totalIncomes
                        .subtract(totalExpensesFromAccounts)
                        .subtract(totalCreditCardPayments)
                        .subtract(totalDebtPayments));

        List<AccountBalanceResponse> accountBalances =
                accountRepository.findAllByOrderByNameAsc().stream()
                        .map(accountMapper::toBalanceResponse)
                        .toList();

        List<CreditCardDebtResponse> creditCardDebts = creditCardRepository.findAllByOrderByNameAsc().stream()
                .map(creditCardMapper::toDebtSummary)
                .toList();

        List<DebtResponse> activeDebts = debtService.listActiveDebts();

        List<DueEventResponse> upcomingDueDates = buildUpcomingDueEvents(today);

        List<CategoryAmountResponse> incomesByCategory =
                mapCategoryRows(incomeRepository.sumAmountGroupedByCategory(start, end));
        List<CategoryAmountResponse> expensesByCategory =
                mapCategoryRows(expenseRepository.sumAmountGroupedByCategory(start, end));

        return new MonthlySummaryResponse(
                start,
                end,
                totalIncomes,
                totalExpensesAll,
                totalExpensesFromAccounts,
                totalCreditCardCharges,
                totalCreditCardPayments,
                totalDebtPayments,
                netCashFlow,
                accountBalances,
                creditCardDebts,
                activeDebts,
                upcomingDueDates,
                incomesByCategory,
                expensesByCategory
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RangeReportResponse getRangeReport(RangeReportQuery query) {
        LocalDate start = query.startDate();
        LocalDate end = query.endDate();
        if (start.isAfter(end)) {
            throw new BusinessRuleException("La fecha de inicio no puede ser posterior a la fecha fin");
        }

        BigDecimal totalIncomes = nz(incomeRepository.sumAmountBetween(start, end));
        BigDecimal totalExpensesAll = nz(expenseRepository.sumAmountBetween(start, end));
        BigDecimal totalExpensesFromAccounts = nz(
                expenseRepository.sumAmountBetweenAndPaymentAccountPresent(start, end));
        BigDecimal totalCreditCardCharges = nz(
                expenseRepository.sumAmountBetweenAndCreditCardPresent(start, end));
        BigDecimal totalCreditCardPayments = nz(creditCardPaymentRepository.sumAmountBetween(start, end));
        BigDecimal totalDebtPayments = nz(debtPaymentRepository.sumAmountBetween(start, end));

        BigDecimal netCashFlow = MoneyUtils.normalize(
                totalIncomes
                        .subtract(totalExpensesFromAccounts)
                        .subtract(totalCreditCardPayments)
                        .subtract(totalDebtPayments));

        List<IncomeResponse> incomes = incomeRepository
                .findByIncomeDateBetweenOrderByIncomeDateAsc(start, end)
                .stream()
                .map(incomeMapper::toResponse)
                .toList();

        List<ExpenseResponse> expenses = expenseRepository
                .findByExpenseDateBetweenOrderByExpenseDateAsc(start, end)
                .stream()
                .map(expenseMapper::toResponse)
                .toList();

        List<CreditCardPaymentResponse> creditCardPayments = creditCardPaymentRepository
                .findByPaymentDateBetweenOrderByPaymentDateAsc(start, end)
                .stream()
                .map(creditCardPaymentMapper::toResponse)
                .toList();

        List<DebtPaymentResponse> debtPayments = debtPaymentRepository
                .findByPaymentDateBetweenOrderByPaymentDateAsc(start, end)
                .stream()
                .map(debtPaymentMapper::toResponse)
                .toList();

        List<TransferResponse> transfers = transferRepository
                .findByTransferDateBetweenOrderByTransferDateAsc(start, end)
                .stream()
                .map(transferMapper::toResponse)
                .toList();

        List<CategoryAmountResponse> incomesByCategory =
                mapCategoryRows(incomeRepository.sumAmountGroupedByCategory(start, end));
        List<CategoryAmountResponse> expensesByCategory =
                mapCategoryRows(expenseRepository.sumAmountGroupedByCategory(start, end));

        return new RangeReportResponse(
                start,
                end,
                incomes,
                expenses,
                creditCardPayments,
                debtPayments,
                transfers,
                incomesByCategory,
                expensesByCategory,
                totalIncomes,
                totalExpensesAll,
                totalExpensesFromAccounts,
                totalCreditCardCharges,
                totalCreditCardPayments,
                totalDebtPayments,
                netCashFlow
        );
    }

    private List<DueEventResponse> buildUpcomingDueEvents(LocalDate today) {
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        List<Reminder> reminders =
                reminderRepository.findByActiveTrueAndReminderDateBetweenOrderByReminderDateAsc(today, monthEnd);

        List<Debt> debts = debtRepository.findUpcomingByDueDateBetween(
                List.of(DebtStatus.ACTIVA, DebtStatus.VENCIDA),
                today,
                monthEnd);

        List<DueEventResponse> events = new ArrayList<>();
        for (Reminder r : reminders) {
            events.add(new DueEventResponse(
                    DueEventKind.REMINDER,
                    r.getId(),
                    r.getTitle(),
                    r.getReminderDate() != null ? r.getReminderDate().atStartOfDay() : null,
                    null,
                    r.getNotes()));
        }
        for (Debt d : debts) {
            Debt synced = debtService.syncOverdueState(d);
            LocalDateTime whenAt = synced.getDueDate() != null ? synced.getDueDate().atStartOfDay() : null;
            events.add(new DueEventResponse(
                    DueEventKind.DEBT,
                    synced.getId(),
                    synced.getName(),
                    whenAt,
                    synced.getPendingAmount(),
                    synced.getNotes()));
        }

        events.sort(Comparator.comparing(DueEventResponse::whenAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    private List<CategoryAmountResponse> mapCategoryRows(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new CategoryAmountResponse(
                        (Long) r[0],
                        (String) r[1],
                        MoneyUtils.normalize(toBigDecimal(r[2]))))
                .toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        throw new BusinessRuleException("Valor numérico inesperado en agrupación por categoría");
    }

    private BigDecimal nz(BigDecimal value) {
        return MoneyUtils.normalize(value != null ? value : BigDecimal.ZERO);
    }
}
