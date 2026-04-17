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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private CategoryService categoryService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Category buildPersonalCategory(Long id, String name, CategoryType type) {
        Category c = Category.builder()
                .user(currentUser).name(name).type(type).system(false).build();
        c.setId(id);
        return c;
    }

    private Category buildGlobalCategory(Long id, String name, CategoryType type) {
        Category c = Category.builder()
                .user(null).name(name).type(type).system(false).build();
        c.setId(id);
        return c;
    }

    private Category buildSystemCategory(Long id, String name) {
        Category c = Category.builder()
                .user(null).name(name).type(CategoryType.NEUTRAL).system(true).build();
        c.setId(id);
        return c;
    }

    // -------------------------------------------------------------------------
    // findAllAvailableForUser
    // -------------------------------------------------------------------------

    @Test
    void findAllAvailableForUser_ShouldReturnGlobalAndPersonalCategories() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category global = buildGlobalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        Category personal = buildPersonalCategory(2L, "Assinatura Premium", CategoryType.EXPENSE);
        when(categoryRepository.findAllAvailableForUser(currentUser.getId()))
                .thenReturn(List.of(global, personal));

        List<CategoryDto> result = categoryService.findAllAvailableForUser(authentication);

        assertEquals(2, result.size());
        assertTrue(result.get(0).global());
        assertFalse(result.get(1).global());
    }

    // -------------------------------------------------------------------------
    // createCategory (personal)
    // -------------------------------------------------------------------------

    @Test
    void createCategory_WhenValid_ShouldPersistAndReturnDto() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        CategoryCreateDto dto = new CategoryCreateDto("Alimentação", CategoryType.EXPENSE);
        Category saved = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);

        when(categoryRepository.findPersonalByNameAndTypeAndUserId("Alimentação", CategoryType.EXPENSE, currentUser.getId()))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenReturn(saved);

        CategoryDto result = categoryService.createCategory(dto, authentication);

        assertNotNull(result);
        assertEquals("Alimentação", result.name());
        assertFalse(result.global());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategory_WhenDuplicate_ShouldThrowIllegalArgumentException() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        CategoryCreateDto dto = new CategoryCreateDto("Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findPersonalByNameAndTypeAndUserId("Alimentação", CategoryType.EXPENSE, currentUser.getId()))
                .thenReturn(Optional.of(buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE)));

        assertThrows(IllegalArgumentException.class,
                () -> categoryService.createCategory(dto, authentication));
        verify(categoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // updateCategory (personal)
    // -------------------------------------------------------------------------

    @Test
    void updateCategory_WhenValid_ShouldUpdateAndReturnDto() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category category = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        CategoryUpdateDto dto = new CategoryUpdateDto("Alimentação e Bebidas", CategoryType.EXPENSE);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(categoryRepository.findPersonalByNameAndTypeAndUserId("Alimentação e Bebidas", CategoryType.EXPENSE, currentUser.getId()))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryDto result = categoryService.updateCategory(1L, dto, authentication);

        assertNotNull(result);
        assertEquals("Alimentação e Bebidas", category.getName());
        verify(categoryRepository).save(category);
    }

    @Test
    void updateCategory_WhenNotFound_ShouldThrow() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.updateCategory(99L,
                        new CategoryUpdateDto("Teste", CategoryType.EXPENSE), authentication));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_WhenNotOwned_ShouldThrow() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category category = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.updateCategory(1L,
                        new CategoryUpdateDto("Teste", CategoryType.EXPENSE), authentication));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_WhenSystem_ShouldThrowIllegalStateException() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category system = buildSystemCategory(1L, "Pagamento de Cartão");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(system));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> categoryService.updateCategory(1L,
                        new CategoryUpdateDto("Outro", CategoryType.NEUTRAL), authentication));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_WhenDuplicateName_ShouldThrowIllegalArgumentException() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category category = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        Category other = buildPersonalCategory(2L, "Alimentação e Bebidas", CategoryType.EXPENSE);
        CategoryUpdateDto dto = new CategoryUpdateDto("Alimentação e Bebidas", CategoryType.EXPENSE);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(categoryRepository.findPersonalByNameAndTypeAndUserId("Alimentação e Bebidas", CategoryType.EXPENSE, currentUser.getId()))
                .thenReturn(Optional.of(other));

        assertThrows(IllegalArgumentException.class,
                () -> categoryService.updateCategory(1L, dto, authentication));
        verify(categoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // deleteCategory (personal)
    // -------------------------------------------------------------------------

    @Test
    void deleteCategory_WhenValid_ShouldDelete() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category category = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(categoryRepository.existsTransactionByCategoryId(1L)).thenReturn(false);
        when(categoryRepository.existsInstallmentByCategoryId(1L)).thenReturn(false);
        when(categoryRepository.existsRefundByCategoryId(1L)).thenReturn(false);
        when(categoryRepository.existsRecurringTransactionByCategoryId(1L)).thenReturn(false);

        categoryService.deleteCategory(1L, authentication);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteCategory_WhenNotFound_ShouldThrow() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.deleteCategory(99L, authentication));
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteCategory_WhenNotOwned_ShouldThrow() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category category = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.deleteCategory(1L, authentication));
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteCategory_WhenSystem_ShouldThrowIllegalStateException() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category system = buildSystemCategory(1L, "Pagamento de Cartão");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(system));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> categoryService.deleteCategory(1L, authentication));
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteCategory_WhenInUse_ShouldThrowIllegalStateException() {
        when(authentication.getPrincipal()).thenReturn(currentUser);

        Category category = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(categoryRepository.existsTransactionByCategoryId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> categoryService.deleteCategory(1L, authentication));
        verify(categoryRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // createGlobalCategory (admin)
    // -------------------------------------------------------------------------

    @Test
    void createGlobalCategory_WhenValid_ShouldPersistAndReturnDto() {
        CategoryAdminCreateDto dto = new CategoryAdminCreateDto("Investimentos", CategoryType.INCOME);
        Category saved = buildGlobalCategory(10L, "Investimentos", CategoryType.INCOME);

        when(categoryRepository.findGlobalByNameAndType("Investimentos", CategoryType.INCOME))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenReturn(saved);

        CategoryDto result = categoryService.createGlobalCategory(dto);

        assertNotNull(result);
        assertTrue(result.global());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createGlobalCategory_WhenDuplicate_ShouldThrowIllegalArgumentException() {
        CategoryAdminCreateDto dto = new CategoryAdminCreateDto("Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findGlobalByNameAndType("Alimentação", CategoryType.EXPENSE))
                .thenReturn(Optional.of(buildGlobalCategory(1L, "Alimentação", CategoryType.EXPENSE)));

        assertThrows(IllegalArgumentException.class,
                () -> categoryService.createGlobalCategory(dto));
        verify(categoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // updateGlobalCategory (admin)
    // -------------------------------------------------------------------------

    @Test
    void updateGlobalCategory_WhenValid_ShouldUpdateAndReturnDto() {
        Category category = buildGlobalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        CategoryUpdateDto dto = new CategoryUpdateDto("Alimentação e Bebidas", CategoryType.EXPENSE);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findGlobalByNameAndType("Alimentação e Bebidas", CategoryType.EXPENSE))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryDto result = categoryService.updateGlobalCategory(1L, dto);

        assertNotNull(result);
        assertEquals("Alimentação e Bebidas", category.getName());
    }

    @Test
    void updateGlobalCategory_WhenNotFound_ShouldThrow() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.updateGlobalCategory(99L,
                        new CategoryUpdateDto("Teste", CategoryType.EXPENSE)));
    }

    @Test
    void updateGlobalCategory_WhenPersonal_ShouldThrow() {
        Category personal = buildPersonalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(personal));

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.updateGlobalCategory(1L,
                        new CategoryUpdateDto("Teste", CategoryType.EXPENSE)));
    }

    @Test
    void updateGlobalCategory_WhenSystem_ShouldThrowIllegalStateException() {
        Category system = buildSystemCategory(1L, "Pagamento de Cartão");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(system));

        assertThrows(IllegalStateException.class,
                () -> categoryService.updateGlobalCategory(1L,
                        new CategoryUpdateDto("Outro", CategoryType.NEUTRAL)));
    }

    // -------------------------------------------------------------------------
    // deleteGlobalCategory (admin)
    // -------------------------------------------------------------------------

    @Test
    void deleteGlobalCategory_WhenValid_ShouldDelete() {
        Category category = buildGlobalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsTransactionByCategoryId(1L)).thenReturn(false);
        when(categoryRepository.existsInstallmentByCategoryId(1L)).thenReturn(false);
        when(categoryRepository.existsRefundByCategoryId(1L)).thenReturn(false);
        when(categoryRepository.existsRecurringTransactionByCategoryId(1L)).thenReturn(false);

        categoryService.deleteGlobalCategory(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteGlobalCategory_WhenSystem_ShouldThrowIllegalStateException() {
        Category system = buildSystemCategory(1L, "Pagamento de Cartão");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(system));

        assertThrows(IllegalStateException.class,
                () -> categoryService.deleteGlobalCategory(1L));
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteGlobalCategory_WhenInUse_ShouldThrowIllegalStateException() {
        Category category = buildGlobalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsTransactionByCategoryId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> categoryService.deleteGlobalCategory(1L));
        verify(categoryRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // resolveCategory
    // -------------------------------------------------------------------------

    @Test
    void resolveCategory_WhenGlobal_ShouldReturn() {
        Category global = buildGlobalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(global));

        Category result = categoryService.resolveCategory(1L, currentUser);

        assertNotNull(result);
        assertNull(result.getUser());
    }

    @Test
    void resolveCategory_WhenPersonalOwned_ShouldReturn() {
        Category personal = buildPersonalCategory(1L, "Minha Categoria", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(personal));

        Category result = categoryService.resolveCategory(1L, currentUser);

        assertNotNull(result);
        assertEquals(currentUser.getId(), result.getUser().getId());
    }

    @Test
    void resolveCategory_WhenPersonalNotOwned_ShouldThrow() {
        User otherUser = new User();
        otherUser.setId(99L);
        Category personal = buildPersonalCategory(1L, "Alheia", CategoryType.EXPENSE);
        personal.setUser(otherUser);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(personal));

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.resolveCategory(1L, currentUser));
    }

    @Test
    void resolveCategory_WhenNotFound_ShouldThrow() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.resolveCategory(99L, currentUser));
    }

    @Test
    void resolveCategory_WhenTypeAllowed_ShouldReturn() {
        Category category = buildGlobalCategory(1L, "Alimentação", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        Category result = categoryService.resolveCategory(1L, currentUser,
                EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL));

        assertNotNull(result);
        assertEquals(CategoryType.EXPENSE, result.getType());
    }

    @Test
    void resolveCategory_WhenTypeNotAllowed_ShouldThrowIllegalArgumentException() {
        Category category = buildGlobalCategory(1L, "Salário", CategoryType.INCOME);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThrows(IllegalArgumentException.class,
                () -> categoryService.resolveCategory(1L, currentUser,
                        EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL)));
    }

    @Test
    void resolveCategory_WhenNeutralTypeAlwaysAllowed_ShouldReturn() {
        Category category = buildGlobalCategory(1L, "Ajuste de Saldo", CategoryType.NEUTRAL);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        Category result = categoryService.resolveCategory(1L, currentUser,
                EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL));

        assertNotNull(result);
    }

    // -------------------------------------------------------------------------
    // findSystemByName
    // -------------------------------------------------------------------------

    @Test
    void findSystemByName_WhenExists_ShouldReturn() {
        Category system = buildSystemCategory(1L, "Pagamento de Cartão");
        when(categoryRepository.findSystemByName("Pagamento de Cartão")).thenReturn(Optional.of(system));

        Category result = categoryService.findSystemByName("Pagamento de Cartão");

        assertNotNull(result);
        assertTrue(result.isSystem());
    }

    @Test
    void findSystemByName_WhenNotFound_ShouldThrow() {
        when(categoryRepository.findSystemByName("Inexistente")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> categoryService.findSystemByName("Inexistente"));
    }
}