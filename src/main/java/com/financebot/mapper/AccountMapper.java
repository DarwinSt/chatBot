package com.financebot.mapper;

import com.financebot.dto.response.AccountBalanceResponse;
import com.financebot.dto.response.AccountResponse;
import com.financebot.entity.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalance(),
                account.getCurrentBalance(),
                account.isActive(),
                account.getNotes(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    public AccountBalanceResponse toBalanceResponse(Account account) {
        return new AccountBalanceResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrentBalance(),
                account.isActive()
        );
    }
}
