package io.github.ronaldobertolucci.unita.dto.category;

import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;

public record CategoryDto(Long id, String name, CategoryType type, boolean global) {
    public CategoryDto(Category category) {
        this(
            category.getId(),
            category.getName(),
            category.getType(),
            category.getUser() == null
        );
    }
}