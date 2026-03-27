package com.financebot.service.impl;

import com.financebot.dto.request.IncomeCreateRequest;
import com.financebot.dto.response.IncomeResponse;
import com.financebot.entity.Account;
import com.financebot.entity.Category;
import com.financebot.entity.Income;
import com.financebot.enums.CategoryType;
import com.financebot.exception.InvalidOperationException;
import com.financebot.exception.ResourceNotFoundException;
import com.financebot.mapper.IncomeMapper;
import com.financebot.repository.AccountRepository;
import com.financebot.repository.CategoryRepository;
import com.financebot.repository.IncomeRepository;
import com.financebot.service.IncomeService;
import com.financebot.util.MoneyUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class IncomeServiceImpl implements IncomeService {

    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final IncomeMapper incomeMapper;

    public IncomeServiceImpl(
            IncomeRepository incomeRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            IncomeMapper incomeMapper) {
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.incomeMapper = incomeMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomeResponse> findAll() {
        Sort sort = Sort.by(Sort.Order.desc("incomeDate"), Sort.Order.desc("id"));
        return incomeRepository.findAll(sort).stream()
                .map(incomeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeResponse getById(Long id) {
        Income income = incomeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingreso no encontrado: " + id));
        return incomeMapper.toResponse(income);
    }

    @Override
    @Transactional
    public IncomeResponse create(IncomeCreateRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        MoneyUtils.assertPositive(amount);

        Category category = categoryRepository
                .findByIdAndTypeAndActiveTrue(request.categoryId(), CategoryType.INGRESO)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría de ingreso no encontrada o inválida"));

        Account account = accountRepository
                .findById(request.destinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta destino no encontrada"));

        if (!account.isActive()) {
            throw new InvalidOperationException("La cuenta destino no está activa");
        }

        Income income = new Income();
        income.setAmount(amount);
        income.setIncomeDate(request.incomeDate());
        income.setOrigin(request.origin());
        income.setDescription(request.description());
        income.setCategory(category);
        income.setDestinationAccount(account);

        income = incomeRepository.save(income);

        account.setCurrentBalance(MoneyUtils.add(account.getCurrentBalance(), amount));
        accountRepository.save(account);

        return incomeMapper.toResponse(income);
    }
}
