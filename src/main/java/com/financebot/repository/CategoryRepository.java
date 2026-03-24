package com.financebot.repository;

import com.financebot.entity.Category;
import com.financebot.enums.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndActiveTrue(Long id);

    Optional<Category> findByIdAndTypeAndActiveTrue(Long id, CategoryType type);

    List<Category> findAllByTypeAndActiveTrueOrderByNameAsc(CategoryType type);

    Optional<Category> findByNameIgnoreCaseAndType(String name, CategoryType type);
}
