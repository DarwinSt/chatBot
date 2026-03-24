package com.financebot.mapper;

import com.financebot.dto.response.ExpenseResponse;
import com.financebot.entity.Expense;
import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper {

    private final CategoryMapper categoryMapper;

    public ExpenseMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public ExpenseResponse toResponse(Expense expense) {
        Long paymentAccountId = expense.getPaymentAccount() != null ? expense.getPaymentAccount().getId() : null;
        String paymentAccountName = expense.getPaymentAccount() != null ? expense.getPaymentAccount().getName() : null;
        Long creditCardId = expense.getCreditCard() != null ? expense.getCreditCard().getId() : null;
        String creditCardName = expense.getCreditCard() != null ? expense.getCreditCard().getName() : null;

        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getExpenseType(),
                categoryMapper.toRef(expense.getCategory()),
                paymentAccountId,
                paymentAccountName,
                creditCardId,
                creditCardName,
                expense.getCreatedAt(),
                expense.getUpdatedAt()
        );
    }
}
