package com.financebot.service.impl;

import com.financebot.dto.response.CategoryRefResponse;
import com.financebot.entity.Category;
import com.financebot.enums.CategoryType;
import com.financebot.exception.BusinessRuleException;
import com.financebot.exception.ResourceNotFoundException;
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

    @Override
    @Transactional
    public CategoryRefResponse create(String name, CategoryType type) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("El nombre de categoría no puede estar vacío");
        }
        if (categoryRepository.findByNameIgnoreCaseAndType(trimmed, type).isPresent()) {
            throw new BusinessRuleException("Ya existe una categoría con ese nombre y tipo");
        }
        Category c = new Category();
        c.setName(trimmed);
        c.setType(type);
        c.setActive(true);
        c = categoryRepository.save(c);
        return categoryMapper.toRef(c);
    }

    @Override
    @Transactional
    public CategoryRefResponse update(Long id, String name, CategoryType type, boolean active) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("El nombre de categoría no puede estar vacío");
        }
        categoryRepository.findByNameIgnoreCaseAndType(trimmed, type)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessRuleException("Ya existe otra categoría con ese nombre y tipo");
                });
        c.setName(trimmed);
        c.setType(type);
        c.setActive(active);
        c = categoryRepository.save(c);
        return categoryMapper.toRef(c);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
        c.setActive(false);
        categoryRepository.save(c);
    }
}
