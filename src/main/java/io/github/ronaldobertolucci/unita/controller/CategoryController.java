package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.category.CategoryCreateDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryUpdateDto;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryDto>> findAllAvailableForUser(Authentication authentication) {
        return ResponseEntity.ok(categoryService.findAllAvailableForUser(authentication));
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(
            @RequestBody @Valid CategoryCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(dto, authentication));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid CategoryUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(categoryService.updateCategory(id, dto, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            Authentication authentication) {
        categoryService.deleteCategory(id, authentication);
        return ResponseEntity.noContent().build();
    }
}