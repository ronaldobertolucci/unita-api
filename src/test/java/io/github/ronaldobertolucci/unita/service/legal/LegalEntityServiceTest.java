package io.github.ronaldobertolucci.unita.service.legal;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.repository.LegalEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalEntityServiceTest {

    @Mock private LegalEntityRepository legalEntityRepository;

    @InjectMocks private LegalEntityService legalEntityService;

    @Test
    void create_ShouldPersistAndReturnDto() {
        LegalEntityCreateDto dto = new LegalEntityCreateDto("12345678000190", "Empresa LTDA", "Fantasia", "123456");
        LegalEntity saved = buildLegalEntity(1L, "12345678000190", "Empresa LTDA");
        when(legalEntityRepository.save(any())).thenReturn(saved);

        LegalEntityDto result = legalEntityService.create(dto);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("12345678000190", result.cnpj());
        verify(legalEntityRepository).save(any(LegalEntity.class));
    }

    @Test
    void findAll_ShouldReturnAllMappedDtos() {
        when(legalEntityRepository.findAll()).thenReturn(List.of(
                buildLegalEntity(1L, "11111111000101", "Empresa A"),
                buildLegalEntity(2L, "22222222000102", "Empresa B")));

        List<LegalEntityDto> result = legalEntityService.findAll();

        assertEquals(2, result.size());
        verify(legalEntityRepository).findAll();
    }

    @Test
    void findAll_WhenEmpty_ShouldReturnEmptyList() {
        when(legalEntityRepository.findAll()).thenReturn(List.of());

        assertTrue(legalEntityService.findAll().isEmpty());
    }

    @Test
    void findById_WhenExists_ShouldReturnDto() {
        LegalEntity entity = buildLegalEntity(1L, "12345678000190", "Empresa LTDA");
        when(legalEntityRepository.findById(1L)).thenReturn(Optional.of(entity));

        LegalEntityDto result = legalEntityService.findById(1L);

        assertEquals(1L, result.id());
        assertEquals("Empresa LTDA", result.corporateName());
    }

    @Test
    void findById_WhenNotExists_ShouldThrowEntityNotFoundException() {
        when(legalEntityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> legalEntityService.findById(99L));
    }

    private LegalEntity buildLegalEntity(Long id, String cnpj, String corporateName) {
        LegalEntity entity = new LegalEntity();
        entity.setId(id);
        entity.setCnpj(cnpj);
        entity.setCorporateName(corporateName);
        return entity;
    }
}