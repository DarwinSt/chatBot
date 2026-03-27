package com.financebot.service.impl;

import com.financebot.dto.request.ExpenseCreateRequest;
import com.financebot.dto.response.ExpenseResponse;
import com.financebot.entity.Account;
import com.financebot.entity.Category;
import com.financebot.entity.CreditCard;
import com.financebot.entity.Expense;
import com.financebot.enums.CategoryType;
import com.financebot.enums.ExpenseType;
import com.financebot.exception.BusinessRuleException;
import com.financebot.exception.InsufficientBalanceException;
import com.financebot.exception.InsufficientCreditException;
import com.financebot.exception.InvalidOperationException;
import com.financebot.exception.ResourceNotFoundException;
import com.financebot.mapper.CreditCardMapper;
import com.financebot.mapper.ExpenseMapper;
import com.financebot.repository.AccountRepository;
import com.financebot.repository.CategoryRepository;
import com.financebot.repository.CreditCardRepository;
import com.financebot.repository.ExpenseRepository;
import com.financebot.service.ExpenseService;
import com.financebot.util.MoneyUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseServiceImpl.class);

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final ExpenseMapper expenseMapper;
    private final CreditCardMapper creditCardMapper;

    public ExpenseServiceImpl(
            ExpenseRepository expenseRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            CreditCardRepository creditCardRepository,
            ExpenseMapper expenseMapper,
            CreditCardMapper creditCardMapper) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.expenseMapper = expenseMapper;
        this.creditCardMapper = creditCardMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> findAll() {
        Sort sort = Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"));
        return expenseRepository.findAll(sort).stream()
                .map(expenseMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse getById(Long id) {
        return expenseRepository.findById(id)
                .map(expenseMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado: " + id));
    }

    @Override
    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        MoneyUtils.assertPositive(amount);

        boolean hasAccount = request.paymentAccountId() != null;
        boolean hasCard = request.creditCardId() != null;
        if (hasAccount == hasCard) {
            throw new BusinessRuleException("Debe existir exactamente una fuente: cuenta o tarjeta de crédito");
        }

        Category category = categoryRepository
                .findByIdAndTypeAndActiveTrue(request.categoryId(), CategoryType.GASTO)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría de gasto no encontrada o inválida"));

        Expense expense = new Expense();
        expense.setAmount(amount);
        expense.setExpenseDate(request.expenseDate());
        expense.setDescription(buildDescription(request));
        expense.setCategory(category);

        // `expense_type` en tu BD representa si el gasto es FIJO o VARIABLE.
        // En este flujo no solicitamos ese dato, así que por defecto registramos VARIABLE.
        // (Se puede extender después agregando un paso en el wizard.)
        expense.setExpenseType(ExpenseType.VARIABLE);
        log.info("ExpenseServiceImpl.create expenseType set to {}", expense.getExpenseType());

        if (hasAccount) {
            Account account = accountRepository
                    .findById(request.paymentAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cuenta de pago no encontrada"));
            if (!account.isActive()) {
                throw new InvalidOperationException("La cuenta de pago no está activa");
            }
            if (MoneyUtils.isLessThan(account.getCurrentBalance(), amount)) {
                throw new InsufficientBalanceException("Saldo insuficiente en la cuenta de pago");
            }
            account.setCurrentBalance(MoneyUtils.subtract(account.getCurrentBalance(), amount));
            accountRepository.save(account);
            expense.setPaymentAccount(account);
            expense.setCreditCard(null);
        } else {
            CreditCard card = creditCardRepository
                    .findById(request.creditCardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tarjeta de crédito no encontrada"));
            if (!card.isActive()) {
                throw new InvalidOperationException("La tarjeta de crédito no está activa");
            }
            BigDecimal available = creditCardMapper.availableCredit(card);
            if (MoneyUtils.isLessThan(available, amount)) {
                throw new InsufficientCreditException("Cupo insuficiente en la tarjeta de crédito");
            }
            card.setUsedAmount(MoneyUtils.add(card.getUsedAmount(), amount));
            creditCardRepository.save(card);
            expense.setCreditCard(card);
            expense.setPaymentAccount(null);
        }

        expense = expenseRepository.save(expense);
        return expenseMapper.toResponse(expense);
    }

    private String buildDescription(ExpenseCreateRequest request) {
        if (request.additionalNotes() == null || request.additionalNotes().isBlank()) {
            return request.origin();
        }
        return request.origin() + " — " + request.additionalNotes();
    }
}
