package com.financebot.service;

import com.financebot.dto.request.TransferCreateRequest;
import com.financebot.dto.response.TransferResponse;

import java.util.List;

public interface TransferService {

    List<TransferResponse> findAll();

    TransferResponse create(TransferCreateRequest request);
}
