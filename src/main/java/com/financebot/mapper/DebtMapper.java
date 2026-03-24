package com.financebot.mapper;

import com.financebot.dto.response.DebtResponse;
import com.financebot.entity.Debt;
import org.springframework.stereotype.Component;

@Component
public class DebtMapper {

    private final CategoryMapper categoryMapper;

    public DebtMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public DebtResponse toResponse(Debt debt) {
        return new DebtResponse(
                debt.getId(),
                debt.getName(),
                debt.getTotalAmount(),
                debt.getPendingAmount(),
                debt.getStartDate(),
                debt.getDueDate(),
                debt.getCreditor(),
                debt.getNotes(),
                debt.getStatus(),
                categoryMapper.toRef(debt.getCategory()),
                debt.getCreatedAt(),
                debt.getUpdatedAt()
        );
    }
}
