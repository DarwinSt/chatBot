package com.financebot.service.impl;

import com.financebot.dto.response.CategoryRefResponse;
import com.financebot.enums.CategoryType;
import com.financebot.mapper.CategoryMapper;
import com.financebot.repository.CategoryRepository;
import com.financebot.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryRefResponse> listActiveByType(CategoryType type) {
        return categoryRepository.findAllByTypeAndActiveTrueOrderByNameAsc(type).stream()
                .map(categoryMapper::toRef)
                .toList();
    }
}
