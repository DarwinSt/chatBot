package com.financebot.mapper;

import com.financebot.dto.response.CreditCardPaymentResponse;
import com.financebot.entity.CreditCardPayment;
import org.springframework.stereotype.Component;

@Component
public class CreditCardPaymentMapper {

    public CreditCardPaymentResponse toResponse(CreditCardPayment payment) {
        return new CreditCardPaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getNotes(),
                payment.getSourceAccount().getId(),
                payment.getSourceAccount().getName(),
                payment.getCreditCard().getId(),
                payment.getCreditCard().getName(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
