package com.financebot.mapper;

import com.financebot.dto.response.CreditCardDebtResponse;
import com.financebot.dto.response.CreditCardResponse;
import com.financebot.entity.CreditCard;
import com.financebot.util.MoneyUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CreditCardMapper {

    public CreditCardResponse toResponse(CreditCard card) {
        BigDecimal available = availableCredit(card);
        return new CreditCardResponse(
                card.getId(),
                card.getName(),
                card.getTotalLimit(),
                card.getUsedAmount(),
                available,
                card.isActive(),
                card.getStatementCutoffDay(),
                card.getPaymentDueDay(),
                card.getNotes(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    public CreditCardDebtResponse toDebtSummary(CreditCard card) {
        return new CreditCardDebtResponse(
                card.getId(),
                card.getName(),
                card.getTotalLimit(),
                card.getUsedAmount(),
                availableCredit(card),
                card.isActive()
        );
    }

    public BigDecimal availableCredit(CreditCard card) {
        return MoneyUtils.normalize(card.getTotalLimit().subtract(card.getUsedAmount()));
    }
}
