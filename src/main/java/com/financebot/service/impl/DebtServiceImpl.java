package com.financebot.service.impl;

import com.financebot.dto.request.DebtCreateRequest;
import com.financebot.dto.request.DebtPaymentCreateRequest;
import com.financebot.dto.request.DebtPaymentRequest;
import com.financebot.dto.response.DebtPaymentResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.entity.Account;
import com.financebot.entity.Category;
import com.financebot.entity.Debt;
import com.financebot.entity.DebtPayment;
import com.financebot.enums.CategoryType;
import com.financebot.enums.DebtStatus;
import com.financebot.exception.BusinessRuleException;
import com.financebot.exception.InsufficientBalanceException;
import com.financebot.exception.InvalidOperationException;
import com.financebot.exception.ResourceNotFoundException;
import com.financebot.mapper.DebtMapper;
import com.financebot.mapper.DebtPaymentMapper;
import com.financebot.repository.AccountRepository;
import com.financebot.repository.CategoryRepository;
import com.financebot.repository.DebtPaymentRepository;
import com.financebot.repository.DebtRepository;
import com.financebot.service.DebtService;
import com.financebot.util.MoneyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DebtServiceImpl implements DebtService {

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final DebtMapper debtMapper;
    private final DebtPaymentMapper debtPaymentMapper;

    public DebtServiceImpl(
            DebtRepository debtRepository,
            DebtPaymentRepository debtPaymentRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            DebtMapper debtMapper,
            DebtPaymentMapper debtPaymentMapper) {
        this.debtRepository = debtRepository;
        this.debtPaymentRepository = debtPaymentRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.debtMapper = debtMapper;
        this.debtPaymentMapper = debtPaymentMapper;
    }

    @Override
    @Transactional
    public DebtResponse create(DebtCreateRequest request) {
        BigDecimal total = MoneyUtils.normalize(request.totalAmount());
        MoneyUtils.assertPositive(total);

        BigDecimal pending = request.pendingAmount() != null
                ? MoneyUtils.normalize(request.pendingAmount())
                : total;

        if (pending.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("El saldo pendiente no puede ser negativo");
        }
        if (pending.compareTo(total) > 0) {
            throw new BusinessRuleException("El saldo pendiente no puede superar el monto total");
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository
                    .findByIdAndTypeAndActiveTrue(request.categoryId(), CategoryType.DEUDA)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría de deuda no encontrada o inválida"));
        }

        DebtStatus status = request.status() != null ? request.status() : DebtStatus.ACTIVA;

        Debt debt = new Debt();
        debt.setName(request.name().trim());
        debt.setTotalAmount(total);
        debt.setPendingAmount(pending);
        debt.setStartDate(request.startDate() != null ? request.startDate() : LocalDate.now());
        debt.setDueDate(request.dueDate());
        debt.setCreditor(request.creditor());
        debt.setNotes(request.notes());
        debt.setStatus(status);
        debt.setCategory(category);

        debt = debtRepository.save(debt);
        return debtMapper.toResponse(syncOverdueState(debt));
    }

    @Override
    @Transactional
    public DebtResponse getById(Long id) {
        Debt debt = debtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deuda no encontrada: " + id));
        return debtMapper.toResponse(syncOverdueState(debt));
    }

    @Override
    @Transactional
    public List<DebtResponse> findAll() {
        return debtRepository.findAllByOrderByIdDesc().stream()
                .map(this::syncOverdueState)
                .map(debtMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<DebtResponse> listActiveDebts() {
        List<Debt> debts = debtRepository.findAllByPendingAmountGreaterThanAndStatusInOrderByDueDateAsc(
                BigDecimal.ZERO,
                List.of(DebtStatus.ACTIVA, DebtStatus.VENCIDA));
        return debts.stream()
                .map(this::syncOverdueState)
                .map(debtMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DebtPaymentResponse registerPayment(DebtPaymentCreateRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        MoneyUtils.assertPositive(amount);

        Debt debt = debtRepository.findById(request.debtId())
                .orElseThrow(() -> new ResourceNotFoundException("Deuda no encontrada"));
        syncOverdueState(debt);

        if (debt.getStatus() == DebtStatus.PAGADA || debt.getStatus() == DebtStatus.CANCELADA) {
            throw new InvalidOperationException("No se puede abonar una deuda en estado " + debt.getStatus());
        }

        if (amount.compareTo(debt.getPendingAmount()) > 0) {
            throw new BusinessRuleException("El abono no puede superar el saldo pendiente");
        }

        Account account = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta origen no encontrada"));
        if (!account.isActive()) {
            throw new InvalidOperationException("La cuenta origen no está activa");
        }
        if (MoneyUtils.isLessThan(account.getCurrentBalance(), amount)) {
            throw new InsufficientBalanceException("Saldo insuficiente en la cuenta origen");
        }

        account.setCurrentBalance(MoneyUtils.subtract(account.getCurrentBalance(), amount));
        accountRepository.save(account);

        BigDecimal newPending = MoneyUtils.atLeastZero(MoneyUtils.subtract(debt.getPendingAmount(), amount));
        debt.setPendingAmount(newPending);
        if (newPending.compareTo(BigDecimal.ZERO) == 0) {
            debt.setStatus(DebtStatus.PAGADA);
        }

        DebtPayment payment = new DebtPayment();
        payment.setAmount(amount);
        payment.setPaymentDate(request.paymentDate());
        payment.setNotes(request.notes());
        payment.setSourceAccount(account);
        payment.setDebt(debt);

        debtRepository.save(debt);
        payment = debtPaymentRepository.save(payment);

        return debtPaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public DebtPaymentResponse registerPayment(Long debtId, DebtPaymentRequest request) {
        DebtPaymentCreateRequest full = new DebtPaymentCreateRequest(
                request.amount(),
                request.paymentDate(),
                request.notes(),
                request.sourceAccountId(),
                debtId
        );
        return registerPayment(full);
    }

    @Override
    @Transactional
    public Debt syncOverdueState(Debt debt) {
        if (debt.getStatus() == DebtStatus.PAGADA || debt.getStatus() == DebtStatus.CANCELADA) {
            return debt;
        }
        if (debt.getDueDate() != null
                && debt.getPendingAmount().compareTo(BigDecimal.ZERO) > 0
                && LocalDate.now().isAfter(debt.getDueDate())
                && debt.getStatus() == DebtStatus.ACTIVA) {
            debt.setStatus(DebtStatus.VENCIDA);
            return debtRepository.save(debt);
        }
        return debt;
    }
}
