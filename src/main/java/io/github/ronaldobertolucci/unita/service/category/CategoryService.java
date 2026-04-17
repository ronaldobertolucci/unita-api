package io.github.ronaldobertolucci.unita.service.category;

import io.github.ronaldobertolucci.unita.dto.category.CategoryAdminCreateDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryCreateDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryUpdateDto;
import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // -------------------------------------------------------------------------
    // User categories
    // -------------------------------------------------------------------------

    public List<CategoryDto> findAllAvailableForUser(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return categoryRepository.findAllAvailableForUser(currentUser.getId())
                .stream()
                .map(CategoryDto::new)
                .toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (categoryRepository.findPersonalByNameAndTypeAndUserId(
                dto.name(), dto.type(), currentUser.getId()).isPresent()) {
            throw new IllegalArgumentException("Category with this name and type already exists");
        }

        Category category = Category.builder()
                .user(currentUser)
                .name(dto.name())
                .type(dto.type())
                .system(false)
                .build();

        return new CategoryDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        if (!categoryRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("Category not found");
        }

        if (category.isSystem()) {
            throw new IllegalStateException("System categories cannot be edited");
        }

        if (categoryRepository.findPersonalByNameAndTypeAndUserId(
                dto.name(), dto.type(), currentUser.getId())
                .filter(c -> !c.getId().equals(id))
                .isPresent()) {
            throw new IllegalArgumentException("Category with this name and type already exists");
        }

        category.setName(dto.name());
        category.setType(dto.type());

        return new CategoryDto(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        if (!categoryRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("Category not found");
        }

        if (category.isSystem()) {
            throw new IllegalStateException("System categories cannot be deleted");
        }

        if (isCategoryInUse(id)) {
            throw new IllegalStateException("Category is in use and cannot be deleted");
        }

        categoryRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Admin categories
    // -------------------------------------------------------------------------

    @Transactional
    public CategoryDto createGlobalCategory(CategoryAdminCreateDto dto) {
        if (categoryRepository.findGlobalByNameAndType(dto.name(), dto.type()).isPresent()) {
            throw new IllegalArgumentException("Global category with this name and type already exists");
        }

        Category category = Category.builder()
                .user(null)
                .name(dto.name())
                .type(dto.type())
                .system(false)
                .build();

        return new CategoryDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateGlobalCategory(Long id, CategoryUpdateDto dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        if (category.getUser() != null) {
            throw new EntityNotFoundException("Category not found");
        }

        if (category.isSystem()) {
            throw new IllegalStateException("System categories cannot be edited");
        }

        if (categoryRepository.findGlobalByNameAndType(dto.name(), dto.type())
                .filter(c -> !c.getId().equals(id))
                .isPresent()) {
            throw new IllegalArgumentException("Global category with this name and type already exists");
        }

        category.setName(dto.name());
        category.setType(dto.type());

        return new CategoryDto(categoryRepository.save(category));
    }

    @Transactional
    public void deleteGlobalCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        if (category.getUser() != null) {
            throw new EntityNotFoundException("Category not found");
        }

        if (category.isSystem()) {
            throw new IllegalStateException("System categories cannot be deleted");
        }

        if (isCategoryInUse(id)) {
            throw new IllegalStateException("Category is in use and cannot be deleted");
        }

        categoryRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    public Category findSystemByName(String name) {
        return categoryRepository.findSystemByName(name)
                .orElseThrow(() -> new EntityNotFoundException("System category not found: " + name));
    }

    public Category resolveCategory(Long categoryId, User user, Set<CategoryType> allowedTypes) {
        Category category = resolveCategory(categoryId, user);
        if (!allowedTypes.contains(category.getType())) {
            throw new IllegalArgumentException(
                    "Category type " + category.getType() + " is not allowed in this context");
        }
        return category;
    }

    public Category resolveCategory(Long categoryId, User user) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        boolean isGlobal = category.getUser() == null;
        boolean isOwned = !isGlobal && category.getUser().getId().equals(user.getId());

        if (!isGlobal && !isOwned) {
            throw new EntityNotFoundException("Category not found");
        }

        return category;
    }

    private boolean isCategoryInUse(Long categoryId) {
        return categoryRepository.existsTransactionByCategoryId(categoryId)
                || categoryRepository.existsInstallmentByCategoryId(categoryId)
                || categoryRepository.existsRefundByCategoryId(categoryId)
                || categoryRepository.existsRecurringTransactionByCategoryId(categoryId);
    }
}