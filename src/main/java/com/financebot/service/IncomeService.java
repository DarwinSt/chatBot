package com.financebot.service;

import com.financebot.dto.request.IncomeCreateRequest;
import com.financebot.dto.response.IncomeResponse;

import java.util.List;

public interface IncomeService {

    List<IncomeResponse> findAll();

    IncomeResponse getById(Long id);

    IncomeResponse create(IncomeCreateRequest request);
}
