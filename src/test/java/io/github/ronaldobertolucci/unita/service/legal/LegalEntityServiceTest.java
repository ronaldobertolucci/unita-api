package io.github.ronaldobertolucci.unita.service.legal;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityUpdateDto;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.BankAccountRepository;
import io.github.ronaldobertolucci.unita.repository.BenefitAccountRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalEntityServiceTest {

    @Mock
    private LegalEntityRepository legalEntityRepository;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private BenefitAccountRepository benefitAccountRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private LegalEntityService legalEntityService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_WhenCnpjIsNew_ShouldPersistAndReturnDto() {
        LegalEntityCreateDto dto = new LegalEntityCreateDto("12345678000190", "Empresa LTDA", "Fantasia", "123456");
        LegalEntity saved = buildLegalEntity(1L, "12345678000190", "Empresa LTDA");

        when(legalEntityRepository.existsByCnpjAndUserId("12345678000190", currentUser.getId())).thenReturn(false);
        when(legalEntityRepository.save(any())).thenReturn(saved);

        LegalEntityDto result = legalEntityService.create(dto, authentication);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("12345678000190", result.cnpj());
        verify(legalEntityRepository).save(any(LegalEntity.class));
    }

    @Test
    void create_WhenCnpjAlreadyExists_ShouldThrowIllegalArgumentException() {
        LegalEntityCreateDto dto = new LegalEntityCreateDto("12345678000190", "Empresa LTDA", null, null);
        when(legalEntityRepository.existsByCnpjAndUserId("12345678000190", currentUser.getId())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> legalEntityService.create(dto, authentication));
        verify(legalEntityRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_ShouldReturnOnlyUserEntities() {
        when(legalEntityRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of(
                buildLegalEntity(1L, "11111111000101", "Empresa A"),
                buildLegalEntity(2L, "22222222000102", "Empresa B")));

        List<LegalEntityDto> result = legalEntityService.findAll(authentication);

        assertEquals(2, result.size());
        verify(legalEntityRepository).findAllByUserId(currentUser.getId());
    }

    @Test
    void findAll_WhenEmpty_ShouldReturnEmptyList() {
        when(legalEntityRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());

        assertTrue(legalEntityService.findAll(authentication).isEmpty());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_WhenOwned_ShouldReturnDto() {
        LegalEntity entity = buildLegalEntity(1L, "12345678000190", "Empresa LTDA");
        when(legalEntityRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(entity));

        LegalEntityDto result = legalEntityService.findById(1L, authentication);

        assertEquals(1L, result.id());
    }

    @Test
    void findById_WhenNotOwned_ShouldThrow() {
        when(legalEntityRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> legalEntityService.findById(99L, authentication));
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_WhenValid_ShouldUpdateAndReturnDto() {
        LegalEntity entity = buildLegalEntity(1L, "12345678000190", "Empresa LTDA");
        LegalEntityUpdateDto dto = new LegalEntityUpdateDto("12345678000190", "Empresa Atualizada", null, null);

        when(legalEntityRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(entity));
        when(legalEntityRepository.save(entity)).thenReturn(entity);

        LegalEntityDto result = legalEntityService.update(1L, dto, authentication);

        assertNotNull(result);
        assertEquals("Empresa Atualizada", entity.getCorporateName());
        verify(legalEntityRepository).save(entity);
    }

    @Test
    void update_WhenCnpjChangedAndAlreadyExists_ShouldThrowIllegalArgumentException() {
        LegalEntity entity = buildLegalEntity(1L, "12345678000190", "Empresa LTDA");
        LegalEntityUpdateDto dto = new LegalEntityUpdateDto("99999999000199", "Empresa LTDA", null, null);

        when(legalEntityRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(entity));
        when(legalEntityRepository.existsByCnpjAndUserId("99999999000199", currentUser.getId())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> legalEntityService.update(1L, dto, authentication));
        verify(legalEntityRepository, never()).save(any());
    }

    @Test
    void update_WhenCnpjUnchanged_ShouldNotCheckDuplicateAndUpdate() {
        LegalEntity entity = buildLegalEntity(1L, "12345678000190", "Empresa LTDA");
        LegalEntityUpdateDto dto = new LegalEntityUpdateDto("12345678000190", "Novo Nome", null, null);

        when(legalEntityRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(entity));
        when(legalEntityRepository.save(entity)).thenReturn(entity);

        legalEntityService.update(1L, dto, authentication);

        verify(legalEntityRepository, never()).existsByCnpjAndUserId(any(), any());
        verify(legalEntityRepository).save(entity);
    }

    @Test
    void update_WhenNotFound_ShouldThrow() {
        when(legalEntityRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> legalEntityService.update(99L,
                        new LegalEntityUpdateDto("12345678000190", "Empresa", null, null),
                        authentication));
        verify(legalEntityRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_WhenNotInUse_ShouldDelete() {
        when(legalEntityRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(bankAccountRepository.existsByLegalEntityId(1L)).thenReturn(false);
        when(benefitAccountRepository.existsByLegalEntityId(1L)).thenReturn(false);
        when(creditCardRepository.existsByLegalEntityId(1L)).thenReturn(false);

        legalEntityService.delete(1L, authentication);

        verify(legalEntityRepository).deleteById(1L);
    }

    @Test
    void delete_WhenNotFound_ShouldThrow() {
        when(legalEntityRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> legalEntityService.delete(99L, authentication));
        verify(legalEntityRepository, never()).deleteById(any());
    }

    @Test
    void delete_WhenUsedByBankAccount_ShouldThrowIllegalStateException() {
        when(legalEntityRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(bankAccountRepository.existsByLegalEntityId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> legalEntityService.delete(1L, authentication));
        verify(legalEntityRepository, never()).deleteById(any());
    }

    @Test
    void delete_WhenUsedByBenefitAccount_ShouldThrowIllegalStateException() {
        when(legalEntityRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(bankAccountRepository.existsByLegalEntityId(1L)).thenReturn(false);
        when(benefitAccountRepository.existsByLegalEntityId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> legalEntityService.delete(1L, authentication));
        verify(legalEntityRepository, never()).deleteById(any());
    }

    @Test
    void delete_WhenUsedByCreditCard_ShouldThrowIllegalStateException() {
        when(legalEntityRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(bankAccountRepository.existsByLegalEntityId(1L)).thenReturn(false);
        when(benefitAccountRepository.existsByLegalEntityId(1L)).thenReturn(false);
        when(creditCardRepository.existsByLegalEntityId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> legalEntityService.delete(1L, authentication));
        verify(legalEntityRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private LegalEntity buildLegalEntity(Long id, String cnpj, String corporateName) {
        LegalEntity entity = new LegalEntity();
        entity.setId(id);
        entity.setCnpj(cnpj);
        entity.setCorporateName(corporateName);
        entity.setUser(currentUser);
        return entity;
    }
}