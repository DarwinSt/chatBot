package com.financebot.service;

import com.financebot.dto.response.CategoryRefResponse;
import com.financebot.enums.CategoryType;

import java.util.List;

/**
 * Consultas de catálogo de categorías (solo lectura) para capas de presentación (REST, Telegram).
 */
public interface CategoryService {

    List<CategoryRefResponse> listActiveByType(CategoryType type);
}
