package com.financebot.service;

import com.financebot.dto.request.DebtCreateRequest;
import com.financebot.dto.request.DebtPaymentCreateRequest;
import com.financebot.dto.request.DebtPaymentRequest;
import com.financebot.dto.response.DebtPaymentResponse;
import com.financebot.dto.response.DebtResponse;
import com.financebot.entity.Debt;

import java.util.List;

public interface DebtService {

    DebtResponse create(DebtCreateRequest request);

    DebtResponse getById(Long id);

    List<DebtResponse> findAll();

    List<DebtResponse> listActiveDebts();

    DebtPaymentResponse registerPayment(DebtPaymentCreateRequest request);

    DebtPaymentResponse registerPayment(Long debtId, DebtPaymentRequest request);

    /**
     * Si la deuda está vencida y aún hay saldo pendiente, actualiza el estado a {@code VENCIDA}.
     */
    Debt syncOverdueState(Debt debt);
}
