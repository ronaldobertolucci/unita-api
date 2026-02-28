package io.github.ronaldobertolucci.unita.service.employer;

import io.github.ronaldobertolucci.unita.dto.employer.*;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.EmployerRepository;
import io.github.ronaldobertolucci.unita.repository.IndividualEmployerRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityEmployerRepository;
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
class EmployerServiceTest {

    @Mock private EmployerRepository employerRepository;
    @Mock private IndividualEmployerRepository individualEmployerRepository;
    @Mock private LegalEntityEmployerRepository legalEntityEmployerRepository;
    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private Authentication authentication;

    @InjectMocks private EmployerService employerService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // createIndividual
    // -------------------------------------------------------------------------

    @Test
    void createIndividual_WhenCpfIsNew_ShouldPersistAndReturnDto() {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        IndividualEmployer saved = buildIndividualEmployer(1L, "12345678901", "João Silva");

        when(individualEmployerRepository.existsByCpfAndUserId("12345678901", currentUser.getId())).thenReturn(false);
        when(individualEmployerRepository.save(any())).thenReturn(saved);

        IndividualEmployerDto result = employerService.createIndividual(dto, authentication);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(individualEmployerRepository).save(any(IndividualEmployer.class));
    }

    @Test
    void createIndividual_WhenCpfAlreadyExists_ShouldThrowIllegalArgumentException() {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        when(individualEmployerRepository.existsByCpfAndUserId("12345678901", currentUser.getId())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> employerService.createIndividual(dto, authentication));
        verify(individualEmployerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findAllIndividual
    // -------------------------------------------------------------------------

    @Test
    void findAllIndividual_ShouldReturnOnlyUserEmployers() {
        when(individualEmployerRepository.findAllByUserId(currentUser.getId()))
                .thenReturn(List.of(
                        buildIndividualEmployer(1L, "11111111111", "Emp A"),
                        buildIndividualEmployer(2L, "22222222222", "Emp B")));

        List<IndividualEmployerDto> result = employerService.findAllIndividual(authentication);

        assertEquals(2, result.size());
    }

    @Test
    void findAllIndividual_WhenEmpty_ShouldReturnEmptyList() {
        when(individualEmployerRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());

        assertTrue(employerService.findAllIndividual(authentication).isEmpty());
    }

    // -------------------------------------------------------------------------
    // findIndividualById
    // -------------------------------------------------------------------------

    @Test
    void findIndividualById_WhenOwned_ShouldReturnDto() {
        IndividualEmployer employer = buildIndividualEmployer(1L, "12345678901", "João");
        when(individualEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));

        IndividualEmployerDto result = employerService.findIndividualById(1L, authentication);

        assertEquals(1L, result.id());
    }

    @Test
    void findIndividualById_WhenNotOwned_ShouldThrow() {
        when(individualEmployerRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.findIndividualById(99L, authentication));
    }

    // -------------------------------------------------------------------------
    // updateIndividual
    // -------------------------------------------------------------------------

    @Test
    void updateIndividual_WhenValid_ShouldUpdateAndReturnDto() {
        IndividualEmployer employer = buildIndividualEmployer(1L, "12345678901", "João");
        IndividualEmployerUpdateDto dto = new IndividualEmployerUpdateDto("12345678901", "João Atualizado");

        when(individualEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(individualEmployerRepository.save(employer)).thenReturn(employer);

        IndividualEmployerDto result = employerService.updateIndividual(1L, dto, authentication);

        assertNotNull(result);
        assertEquals("João Atualizado", employer.getName());
        verify(individualEmployerRepository).save(employer);
    }

    @Test
    void updateIndividual_WhenCpfChangedAndAlreadyExists_ShouldThrowIllegalArgumentException() {
        IndividualEmployer employer = buildIndividualEmployer(1L, "12345678901", "João");
        IndividualEmployerUpdateDto dto = new IndividualEmployerUpdateDto("99999999999", "João");

        when(individualEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(individualEmployerRepository.existsByCpfAndUserId("99999999999", currentUser.getId()))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> employerService.updateIndividual(1L, dto, authentication));
        verify(individualEmployerRepository, never()).save(any());
    }

    @Test
    void updateIndividual_WhenCpfUnchanged_ShouldNotCheckDuplicateAndUpdate() {
        IndividualEmployer employer = buildIndividualEmployer(1L, "12345678901", "João");
        IndividualEmployerUpdateDto dto = new IndividualEmployerUpdateDto("12345678901", "Novo Nome");

        when(individualEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(individualEmployerRepository.save(employer)).thenReturn(employer);

        employerService.updateIndividual(1L, dto, authentication);

        verify(individualEmployerRepository, never()).existsByCpfAndUserId(any(), any());
        verify(individualEmployerRepository).save(employer);
    }

    @Test
    void updateIndividual_WhenNotFound_ShouldThrow() {
        when(individualEmployerRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.updateIndividual(99L,
                        new IndividualEmployerUpdateDto("12345678901", "João"), authentication));
        verify(individualEmployerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // deleteIndividual
    // -------------------------------------------------------------------------

    @Test
    void deleteIndividual_WhenNotInUse_ShouldDelete() {
        IndividualEmployer employer = buildIndividualEmployer(1L, "12345678901", "João");
        when(individualEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(individualEmployerRepository.existsFgtsAccountByEmployerId(1L)).thenReturn(false);

        employerService.deleteIndividual(1L, authentication);

        verify(individualEmployerRepository).deleteById(1L);
    }

    @Test
    void deleteIndividual_WhenNotFound_ShouldThrow() {
        when(individualEmployerRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.deleteIndividual(99L, authentication));
        verify(individualEmployerRepository, never()).deleteById(any());
    }

    @Test
    void deleteIndividual_WhenInUse_ShouldThrowIllegalStateException() {
        IndividualEmployer employer = buildIndividualEmployer(1L, "12345678901", "João");
        when(individualEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(individualEmployerRepository.existsFgtsAccountByEmployerId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> employerService.deleteIndividual(1L, authentication));
        verify(individualEmployerRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // createLegalEntity
    // -------------------------------------------------------------------------

    @Test
    void createLegalEntity_WhenValid_ShouldPersistAndReturnDto() {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(10L);
        LegalEntity legalEntity = buildLegalEntity(10L);
        LegalEntityEmployer saved = buildLegalEntityEmployer(1L, legalEntity);

        when(legalEntityRepository.findByIdAndUserId(10L, currentUser.getId()))
                .thenReturn(Optional.of(legalEntity));
        when(legalEntityEmployerRepository.existsByLegalEntityIdAndUserId(10L, currentUser.getId()))
                .thenReturn(false);
        when(legalEntityEmployerRepository.save(any())).thenReturn(saved);

        LegalEntityEmployerDto result = employerService.createLegalEntity(dto, authentication);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(legalEntityEmployerRepository).save(any(LegalEntityEmployer.class));
    }

    @Test
    void createLegalEntity_WhenAlreadyExists_ShouldThrowIllegalArgumentException() {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(10L);
        LegalEntity legalEntity = buildLegalEntity(10L);

        when(legalEntityRepository.findByIdAndUserId(10L, currentUser.getId()))
                .thenReturn(Optional.of(legalEntity));
        when(legalEntityEmployerRepository.existsByLegalEntityIdAndUserId(10L, currentUser.getId()))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> employerService.createLegalEntity(dto, authentication));
        verify(legalEntityEmployerRepository, never()).save(any());
    }

    @Test
    void createLegalEntity_WhenLegalEntityNotFound_ShouldThrow() {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(99L);
        when(legalEntityRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.createLegalEntity(dto, authentication));
        verify(legalEntityEmployerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findAllLegalEntity
    // -------------------------------------------------------------------------

    @Test
    void findAllLegalEntity_ShouldReturnOnlyUserEmployers() {
        LegalEntity le = buildLegalEntity(10L);
        when(legalEntityEmployerRepository.findAllByUserId(currentUser.getId()))
                .thenReturn(List.of(buildLegalEntityEmployer(1L, le)));

        List<LegalEntityEmployerDto> result = employerService.findAllLegalEntity(authentication);

        assertEquals(1, result.size());
    }

    @Test
    void findAllLegalEntity_WhenEmpty_ShouldReturnEmptyList() {
        when(legalEntityEmployerRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());

        assertTrue(employerService.findAllLegalEntity(authentication).isEmpty());
    }

    // -------------------------------------------------------------------------
    // findLegalEntityById
    // -------------------------------------------------------------------------

    @Test
    void findLegalEntityById_WhenOwned_ShouldReturnDto() {
        LegalEntity le = buildLegalEntity(10L);
        LegalEntityEmployer employer = buildLegalEntityEmployer(1L, le);
        when(legalEntityEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));

        LegalEntityEmployerDto result = employerService.findLegalEntityById(1L, authentication);

        assertEquals(1L, result.id());
    }

    @Test
    void findLegalEntityById_WhenNotOwned_ShouldThrow() {
        when(legalEntityEmployerRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.findLegalEntityById(99L, authentication));
    }

    // -------------------------------------------------------------------------
    // updateLegalEntity
    // -------------------------------------------------------------------------

    @Test
    void updateLegalEntity_WhenValid_ShouldUpdateAndReturnDto() {
        LegalEntity oldLe = buildLegalEntity(10L);
        LegalEntity newLe = buildLegalEntity(20L);
        LegalEntityEmployer employer = buildLegalEntityEmployer(1L, oldLe);
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(20L);

        when(legalEntityEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(legalEntityRepository.findByIdAndUserId(20L, currentUser.getId()))
                .thenReturn(Optional.of(newLe));
        when(legalEntityEmployerRepository.existsByLegalEntityIdAndUserId(20L, currentUser.getId()))
                .thenReturn(false);
        when(legalEntityEmployerRepository.save(employer)).thenReturn(employer);

        LegalEntityEmployerDto result = employerService.updateLegalEntity(1L, dto, authentication);

        assertNotNull(result);
        assertEquals(newLe, employer.getLegalEntity());
        verify(legalEntityEmployerRepository).save(employer);
    }

    @Test
    void updateLegalEntity_WhenNewLegalEntityAlreadyInUse_ShouldThrowIllegalArgumentException() {
        LegalEntity oldLe = buildLegalEntity(10L);
        LegalEntity newLe = buildLegalEntity(20L);
        LegalEntityEmployer employer = buildLegalEntityEmployer(1L, oldLe);
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(20L);

        when(legalEntityEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(legalEntityRepository.findByIdAndUserId(20L, currentUser.getId()))
                .thenReturn(Optional.of(newLe));
        when(legalEntityEmployerRepository.existsByLegalEntityIdAndUserId(20L, currentUser.getId()))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> employerService.updateLegalEntity(1L, dto, authentication));
        verify(legalEntityEmployerRepository, never()).save(any());
    }

    @Test
    void updateLegalEntity_WhenLegalEntityUnchanged_ShouldNotCheckDuplicateAndUpdate() {
        LegalEntity le = buildLegalEntity(10L);
        LegalEntityEmployer employer = buildLegalEntityEmployer(1L, le);
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(10L);

        when(legalEntityEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(legalEntityRepository.findByIdAndUserId(10L, currentUser.getId()))
                .thenReturn(Optional.of(le));
        when(legalEntityEmployerRepository.save(employer)).thenReturn(employer);

        employerService.updateLegalEntity(1L, dto, authentication);

        verify(legalEntityEmployerRepository, never()).existsByLegalEntityIdAndUserId(any(), any());
        verify(legalEntityEmployerRepository).save(employer);
    }

    @Test
    void updateLegalEntity_WhenNotFound_ShouldThrow() {
        when(legalEntityEmployerRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.updateLegalEntity(99L,
                        new LegalEntityEmployerUpdateDto(10L), authentication));
        verify(legalEntityEmployerRepository, never()).save(any());
    }

    @Test
    void updateLegalEntity_WhenNewLegalEntityNotFound_ShouldThrow() {
        LegalEntity le = buildLegalEntity(10L);
        LegalEntityEmployer employer = buildLegalEntityEmployer(1L, le);
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(99L);

        when(legalEntityEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(legalEntityRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.updateLegalEntity(1L, dto, authentication));
        verify(legalEntityEmployerRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // deleteLegalEntity
    // -------------------------------------------------------------------------

    @Test
    void deleteLegalEntity_WhenNotInUse_ShouldDelete() {
        LegalEntity le = buildLegalEntity(10L);
        LegalEntityEmployer employer = buildLegalEntityEmployer(1L, le);

        when(legalEntityEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(legalEntityEmployerRepository.existsFgtsAccountByEmployerId(1L)).thenReturn(false);

        employerService.deleteLegalEntity(1L, authentication);

        verify(legalEntityEmployerRepository).deleteById(1L);
    }

    @Test
    void deleteLegalEntity_WhenNotFound_ShouldThrow() {
        when(legalEntityEmployerRepository.findByIdAndUserId(99L, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> employerService.deleteLegalEntity(99L, authentication));
        verify(legalEntityEmployerRepository, never()).deleteById(any());
    }

    @Test
    void deleteLegalEntity_WhenInUse_ShouldThrowIllegalStateException() {
        LegalEntity le = buildLegalEntity(10L);
        LegalEntityEmployer employer = buildLegalEntityEmployer(1L, le);

        when(legalEntityEmployerRepository.findByIdAndUserId(1L, currentUser.getId()))
                .thenReturn(Optional.of(employer));
        when(legalEntityEmployerRepository.existsFgtsAccountByEmployerId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> employerService.deleteLegalEntity(1L, authentication));
        verify(legalEntityEmployerRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private IndividualEmployer buildIndividualEmployer(Long id, String cpf, String name) {
        IndividualEmployer employer = new IndividualEmployer();
        employer.setId(id);
        employer.setCpf(cpf);
        employer.setName(name);
        employer.setUser(currentUser);
        return employer;
    }

    private LegalEntityEmployer buildLegalEntityEmployer(Long id, LegalEntity legalEntity) {
        LegalEntityEmployer employer = new LegalEntityEmployer();
        employer.setId(id);
        employer.setLegalEntity(legalEntity);
        employer.setUser(currentUser);
        return employer;
    }

    private LegalEntity buildLegalEntity(Long id) {
        LegalEntity entity = new LegalEntity();
        entity.setId(id);
        entity.setCnpj("12345678000190");
        entity.setCorporateName("Empresa LTDA");
        entity.setUser(currentUser);
        return entity;
    }
}