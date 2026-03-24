package com.financebot.mapper;

import com.financebot.dto.response.CategoryRefResponse;
import com.financebot.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryRefResponse toRef(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryRefResponse(category.getId(), category.getName(), category.getType());
    }
}
