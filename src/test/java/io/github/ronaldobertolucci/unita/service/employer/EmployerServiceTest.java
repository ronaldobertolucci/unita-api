package io.github.ronaldobertolucci.unita.service.employer;

import io.github.ronaldobertolucci.unita.dto.employer.EmployerDto;
import io.github.ronaldobertolucci.unita.dto.employer.IndividualEmployerCreateDto;
import io.github.ronaldobertolucci.unita.dto.employer.LegalEntityEmployerCreateDto;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.repository.EmployerRepository;
import io.github.ronaldobertolucci.unita.repository.IndividualEmployerRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityEmployerRepository;
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
class EmployerServiceTest {

    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private IndividualEmployerRepository individualEmployerRepository;
    @Mock
    private LegalEntityEmployerRepository legalEntityEmployerRepository;
    @Mock
    private LegalEntityRepository legalEntityRepository;

    @InjectMocks
    private EmployerService employerService;

    // -------------------------------------------------------------------------
    // createIndividual
    // -------------------------------------------------------------------------

    @Test
    void createIndividual_WhenCpfIsNew_ShouldPersistAndReturnDto() {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        IndividualEmployer saved = buildIndividualEmployer(1L, "12345678901", "João Silva");

        when(individualEmployerRepository.existsByCpf("12345678901")).thenReturn(false);
        when(individualEmployerRepository.save(any())).thenReturn(saved);

        EmployerDto result = employerService.createIndividual(dto);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(individualEmployerRepository).save(any(IndividualEmployer.class));
    }

    @Test
    void createIndividual_WhenCpfAlreadyExists_ShouldThrowIllegalStateException() {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        when(individualEmployerRepository.existsByCpf("12345678901")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> employerService.createIndividual(dto));
        verify(individualEmployerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // createLegalEntity
    // -------------------------------------------------------------------------

    @Test
    void createLegalEntity_WhenLegalEntityIsNew_ShouldPersistAndReturnDto() {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(10L);
        LegalEntity legalEntity = buildLegalEntity(10L);
        LegalEntityEmployer saved = buildLegalEntityEmployer(1L, legalEntity);

        when(legalEntityEmployerRepository.existsByLegalEntityId(10L)).thenReturn(false);
        when(legalEntityRepository.findById(10L)).thenReturn(Optional.of(legalEntity));
        when(legalEntityEmployerRepository.save(any())).thenReturn(saved);

        EmployerDto result = employerService.createLegalEntity(dto);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(legalEntityEmployerRepository).save(any(LegalEntityEmployer.class));
    }

    @Test
    void createLegalEntity_WhenAlreadyExists_ShouldThrowIllegalStateException() {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(10L);
        when(legalEntityEmployerRepository.existsByLegalEntityId(10L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> employerService.createLegalEntity(dto));
        verify(legalEntityEmployerRepository, never()).save(any());
    }

    @Test
    void createLegalEntity_WhenLegalEntityNotFound_ShouldThrowEntityNotFoundException() {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(99L);
        when(legalEntityEmployerRepository.existsByLegalEntityId(99L)).thenReturn(false);
        when(legalEntityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> employerService.createLegalEntity(dto));
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_ShouldReturnAllMappedDtos() {
        IndividualEmployer e1 = buildIndividualEmployer(1L, "11111111111", "Emp A");
        IndividualEmployer e2 = buildIndividualEmployer(2L, "22222222222", "Emp B");
        when(employerRepository.findAll()).thenReturn(List.of(e1, e2));

        List<EmployerDto> result = employerService.findAll();

        assertEquals(2, result.size());
        verify(employerRepository).findAll();
    }

    @Test
    void findAll_WhenEmpty_ShouldReturnEmptyList() {
        when(employerRepository.findAll()).thenReturn(List.of());

        assertTrue(employerService.findAll().isEmpty());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_WhenExists_ShouldReturnDto() {
        IndividualEmployer employer = buildIndividualEmployer(1L, "12345678901", "João");
        when(employerRepository.findById(1L)).thenReturn(Optional.of(employer));

        EmployerDto result = employerService.findById(1L);

        assertEquals(1L, result.id());
    }

    @Test
    void findById_WhenNotExists_ShouldThrowEntityNotFoundException() {
        when(employerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> employerService.findById(99L));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private IndividualEmployer buildIndividualEmployer(Long id, String cpf, String name) {
        IndividualEmployer employer = new IndividualEmployer();
        employer.setId(id);
        employer.setCpf(cpf);
        employer.setName(name);
        return employer;
    }

    private LegalEntityEmployer buildLegalEntityEmployer(Long id, LegalEntity legalEntity) {
        LegalEntityEmployer employer = new LegalEntityEmployer();
        employer.setId(id);
        employer.setLegalEntity(legalEntity);
        return employer;
    }

    private LegalEntity buildLegalEntity(Long id) {
        LegalEntity entity = new LegalEntity();
        entity.setId(id);
        entity.setCnpj("12345678000190");
        entity.setCorporateName("Empresa LTDA");
        return entity;
    }
}