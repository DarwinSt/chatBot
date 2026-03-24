package com.financebot.service.impl;

import com.financebot.dto.request.TransferCreateRequest;
import com.financebot.dto.response.TransferResponse;
import com.financebot.entity.Account;
import com.financebot.entity.Transfer;
import com.financebot.exception.BusinessRuleException;
import com.financebot.exception.InsufficientBalanceException;
import com.financebot.exception.InvalidOperationException;
import com.financebot.exception.ResourceNotFoundException;
import com.financebot.mapper.TransferMapper;
import com.financebot.repository.AccountRepository;
import com.financebot.repository.TransferRepository;
import com.financebot.service.TransferService;
import com.financebot.util.MoneyUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransferServiceImpl implements TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransferMapper transferMapper;

    public TransferServiceImpl(
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            TransferMapper transferMapper) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.transferMapper = transferMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferResponse> findAll() {
        Sort sort = Sort.by(Sort.Order.desc("transferDate"), Sort.Order.desc("id"));
        return transferRepository.findAll(sort).stream()
                .map(transferMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public TransferResponse create(TransferCreateRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        MoneyUtils.assertPositive(amount);

        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new BusinessRuleException("La cuenta origen y destino deben ser distintas");
        }

        Account source = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta origen no encontrada"));
        Account destination = accountRepository.findById(request.destinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta destino no encontrada"));

        if (!source.isActive() || !destination.isActive()) {
            throw new InvalidOperationException("Ambas cuentas deben estar activas");
        }

        if (MoneyUtils.isLessThan(source.getCurrentBalance(), amount)) {
            throw new InsufficientBalanceException("Saldo insuficiente en la cuenta origen");
        }

        source.setCurrentBalance(MoneyUtils.subtract(source.getCurrentBalance(), amount));
        destination.setCurrentBalance(MoneyUtils.add(destination.getCurrentBalance(), amount));

        accountRepository.save(source);
        accountRepository.save(destination);

        Transfer transfer = new Transfer();
        transfer.setAmount(amount);
        transfer.setTransferDate(request.transferDate());
        transfer.setDescription(request.description());
        transfer.setSourceAccount(source);
        transfer.setDestinationAccount(destination);

        transfer = transferRepository.save(transfer);
        return transferMapper.toResponse(transfer);
    }
}
