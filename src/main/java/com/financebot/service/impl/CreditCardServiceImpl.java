package com.financebot.service.impl;

import com.financebot.dto.request.CreditCardCreateRequest;
import com.financebot.dto.request.CreditCardPaymentCreateRequest;
import com.financebot.dto.request.CreditCardPaymentRequest;
import com.financebot.dto.response.CreditCardPaymentResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.entity.Account;
import com.financebot.entity.CreditCard;
import com.financebot.entity.CreditCardPayment;
import com.financebot.exception.BusinessRuleException;
import com.financebot.exception.InsufficientBalanceException;
import com.financebot.exception.InvalidOperationException;
import com.financebot.exception.ResourceNotFoundException;
import com.financebot.mapper.CreditCardMapper;
import com.financebot.mapper.CreditCardPaymentMapper;
import com.financebot.repository.AccountRepository;
import com.financebot.repository.CreditCardPaymentRepository;
import com.financebot.repository.CreditCardRepository;
import com.financebot.service.CreditCardService;
import com.financebot.util.MoneyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CreditCardServiceImpl implements CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final CreditCardPaymentRepository creditCardPaymentRepository;
    private final AccountRepository accountRepository;
    private final CreditCardMapper creditCardMapper;
    private final CreditCardPaymentMapper creditCardPaymentMapper;

    public CreditCardServiceImpl(
            CreditCardRepository creditCardRepository,
            CreditCardPaymentRepository creditCardPaymentRepository,
            AccountRepository accountRepository,
            CreditCardMapper creditCardMapper,
            CreditCardPaymentMapper creditCardPaymentMapper) {
        this.creditCardRepository = creditCardRepository;
        this.creditCardPaymentRepository = creditCardPaymentRepository;
        this.accountRepository = accountRepository;
        this.creditCardMapper = creditCardMapper;
        this.creditCardPaymentMapper = creditCardPaymentMapper;
    }

    @Override
    @Transactional
    public CreditCardResponse create(CreditCardCreateRequest request) {
        BigDecimal totalLimit = MoneyUtils.normalize(request.totalLimit());
        MoneyUtils.assertPositive(totalLimit);

        BigDecimal used = request.usedAmount() != null
                ? MoneyUtils.normalize(request.usedAmount())
                : MoneyUtils.normalize(BigDecimal.ZERO);

        if (used.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("El monto usado no puede ser negativo");
        }
        if (used.compareTo(totalLimit) > 0) {
            throw new BusinessRuleException("El monto usado no puede superar el cupo total");
        }

        CreditCard card = new CreditCard();
        card.setName(request.name().trim());
        card.setTotalLimit(totalLimit);
        card.setUsedAmount(used);
        card.setStatementCutoffDay(request.statementCutoffDay());
        card.setPaymentDueDay(request.paymentDueDay());
        card.setNotes(request.notes());

        card = creditCardRepository.save(card);
        return creditCardMapper.toResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditCardResponse getById(Long id) {
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta de crédito no encontrada: " + id));
        return creditCardMapper.toResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditCardResponse> listAll() {
        return creditCardRepository.findAllByOrderByNameAsc().stream()
                .map(creditCardMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditCardResponse> listActive() {
        return creditCardRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(creditCardMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAvailableCredit(Long creditCardId) {
        CreditCard card = creditCardRepository.findById(creditCardId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta de crédito no encontrada: " + creditCardId));
        return creditCardMapper.availableCredit(card);
    }

    @Override
    @Transactional
    public CreditCardPaymentResponse registerPayment(CreditCardPaymentCreateRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        MoneyUtils.assertPositive(amount);

        Account account = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta origen no encontrada"));
        if (!account.isActive()) {
            throw new InvalidOperationException("La cuenta origen no está activa");
        }
        if (MoneyUtils.isLessThan(account.getCurrentBalance(), amount)) {
            throw new InsufficientBalanceException("Saldo insuficiente en la cuenta origen");
        }

        CreditCard card = creditCardRepository.findById(request.creditCardId())
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta de crédito no encontrada"));
        if (!card.isActive()) {
            throw new InvalidOperationException("La tarjeta de crédito no está activa");
        }

        BigDecimal newUsed = MoneyUtils.atLeastZero(MoneyUtils.subtract(card.getUsedAmount(), amount));
        card.setUsedAmount(newUsed);

        account.setCurrentBalance(MoneyUtils.subtract(account.getCurrentBalance(), amount));

        CreditCardPayment payment = new CreditCardPayment();
        payment.setAmount(amount);
        payment.setPaymentDate(request.paymentDate());
        payment.setNotes(request.notes());
        payment.setSourceAccount(account);
        payment.setCreditCard(card);

        creditCardRepository.save(card);
        accountRepository.save(account);
        payment = creditCardPaymentRepository.save(payment);

        return creditCardPaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public CreditCardPaymentResponse registerPayment(Long creditCardId, CreditCardPaymentRequest request) {
        CreditCardPaymentCreateRequest full = new CreditCardPaymentCreateRequest(
                request.amount(),
                request.paymentDate(),
                request.notes(),
                request.sourceAccountId(),
                creditCardId
        );
        return registerPayment(full);
    }

    @Override
    @Transactional
    public CreditCardResponse updateBasic(
            Long id,
            String name,
            BigDecimal totalLimit,
            Short statementCutoffDay,
            Short paymentDueDay,
            String notes,
            boolean active
    ) {
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta de crédito no encontrada: " + id));
        BigDecimal normalizedLimit = MoneyUtils.normalize(totalLimit);
        MoneyUtils.assertPositive(normalizedLimit);
        if (MoneyUtils.isLessThan(normalizedLimit, card.getUsedAmount())) {
            throw new BusinessRuleException("El cupo total no puede ser menor al monto usado actual");
        }
        card.setName(name.trim());
        card.setTotalLimit(normalizedLimit);
        card.setStatementCutoffDay(statementCutoffDay);
        card.setPaymentDueDay(paymentDueDay);
        card.setNotes(notes);
        card.setActive(active);
        card = creditCardRepository.save(card);
        return creditCardMapper.toResponse(card);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta de crédito no encontrada: " + id));
        card.setActive(false);
        creditCardRepository.save(card);
    }
}
