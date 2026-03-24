package com.financebot.mapper;

import com.financebot.dto.response.DebtPaymentResponse;
import com.financebot.entity.DebtPayment;
import org.springframework.stereotype.Component;

@Component
public class DebtPaymentMapper {

    public DebtPaymentResponse toResponse(DebtPayment payment) {
        return new DebtPaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getNotes(),
                payment.getSourceAccount().getId(),
                payment.getSourceAccount().getName(),
                payment.getDebt().getId(),
                payment.getDebt().getName(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
