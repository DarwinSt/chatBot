package com.financebot.service;

import com.financebot.dto.request.AccountCreateRequest;
import com.financebot.dto.response.AccountResponse;
import com.financebot.enums.AccountType;

import java.util.List;

public interface AccountService {

    List<AccountResponse> listAll();

    List<AccountResponse> listActive();

    AccountResponse getById(Long id);

    AccountResponse create(AccountCreateRequest request);

    AccountResponse updateBasic(Long id, String name, AccountType type, String notes, boolean active);

    void deactivate(Long id);
}
