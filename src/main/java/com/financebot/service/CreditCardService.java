package com.financebot.service;

import com.financebot.dto.request.CreditCardCreateRequest;
import com.financebot.dto.request.CreditCardPaymentCreateRequest;
import com.financebot.dto.request.CreditCardPaymentRequest;
import com.financebot.dto.response.CreditCardPaymentResponse;
import com.financebot.dto.response.CreditCardResponse;

import java.math.BigDecimal;
import java.util.List;

public interface CreditCardService {

    CreditCardResponse create(CreditCardCreateRequest request);

    CreditCardResponse getById(Long id);

    List<CreditCardResponse> listAll();

    List<CreditCardResponse> listActive();

    BigDecimal getAvailableCredit(Long creditCardId);

    CreditCardPaymentResponse registerPayment(CreditCardPaymentCreateRequest request);

    CreditCardPaymentResponse registerPayment(Long creditCardId, CreditCardPaymentRequest request);

    CreditCardResponse updateBasic(
            Long id,
            String name,
            BigDecimal totalLimit,
            Short statementCutoffDay,
            Short paymentDueDay,
            String notes,
            boolean active
    );

    void deactivate(Long id);
}
