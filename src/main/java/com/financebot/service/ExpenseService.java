package com.financebot.service;

import com.financebot.dto.request.ExpenseCreateRequest;
import com.financebot.dto.response.ExpenseResponse;

import java.util.List;

public interface ExpenseService {

    List<ExpenseResponse> findAll();

    ExpenseResponse getById(Long id);

    ExpenseResponse create(ExpenseCreateRequest request);
}
