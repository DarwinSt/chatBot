package com.financebot.mapper;

import com.financebot.dto.response.IncomeResponse;
import com.financebot.entity.Income;
import org.springframework.stereotype.Component;

@Component
public class IncomeMapper {

    private final CategoryMapper categoryMapper;

    public IncomeMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public IncomeResponse toResponse(Income income) {
        return new IncomeResponse(
                income.getId(),
                income.getAmount(),
                income.getIncomeDate(),
                income.getOrigin(),
                income.getDescription(),
                categoryMapper.toRef(income.getCategory()),
                income.getDestinationAccount().getId(),
                income.getDestinationAccount().getName(),
                income.getCreatedAt(),
                income.getUpdatedAt()
        );
    }
}
