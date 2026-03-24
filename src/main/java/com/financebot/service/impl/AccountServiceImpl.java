package com.financebot.service.impl;

import com.financebot.dto.request.AccountCreateRequest;
import com.financebot.dto.response.AccountResponse;
import com.financebot.entity.Account;
import com.financebot.enums.AccountType;
import com.financebot.exception.ResourceNotFoundException;
import com.financebot.mapper.AccountMapper;
import com.financebot.repository.AccountRepository;
import com.financebot.service.AccountService;
import com.financebot.util.MoneyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    public AccountServiceImpl(AccountRepository accountRepository, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    @Override
    @Transactional
    public AccountResponse create(AccountCreateRequest request) {
        BigDecimal initial = MoneyUtils.normalize(request.initialBalance());
        boolean active = request.active() == null || request.active();

        Account account = new Account();
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setInitialBalance(initial);
        account.setCurrentBalance(initial);
        account.setActive(active);
        account.setNotes(request.notes());

        account = accountRepository.save(account);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> listAll() {
        return accountRepository.findAllByOrderByNameAsc().stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> listActive() {
        return accountRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getById(Long id) {
        return accountRepository.findById(id)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada: " + id));
    }

    @Override
    @Transactional
    public AccountResponse updateBasic(Long id, String name, AccountType type, String notes, boolean active) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada: " + id));
        account.setName(name.trim());
        account.setType(type);
        account.setNotes(notes);
        account.setActive(active);
        account = accountRepository.save(account);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada: " + id));
        account.setActive(false);
        accountRepository.save(account);
    }
}
