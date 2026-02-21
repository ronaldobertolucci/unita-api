package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LegalEntityEmployerRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private LegalEntityEmployerRepository legalEntityEmployerRepository;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    private LegalEntity savedLegalEntity;
    private LegalEntityEmployer savedEmployer;

    @BeforeEach
    void setUp() {
        savedLegalEntity = saveLegalEntity("12345678000190", "Empresa LTDA");

        LegalEntityEmployer employer = new LegalEntityEmployer();
        employer.setLegalEntity(savedLegalEntity);
        savedEmployer = legalEntityEmployerRepository.save(employer);
    }

    @Test
    void existsByLegalEntityId_WhenExists_ShouldReturnTrue() {
        assertTrue(legalEntityEmployerRepository.existsByLegalEntityId(savedLegalEntity.getId()));
    }

    @Test
    void existsByLegalEntityId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(legalEntityEmployerRepository.existsByLegalEntityId(999L));
    }

    @Test
    void save_ShouldPersistEmployer() {
        assertNotNull(savedEmployer.getId());
        assertEquals(savedLegalEntity.getId(), savedEmployer.getLegalEntity().getId());
    }

    @Test
    void findById_WhenExists_ShouldReturnEmployer() {
        assertTrue(legalEntityEmployerRepository.findById(savedEmployer.getId()).isPresent());
    }

    private LegalEntity saveLegalEntity(String cnpj, String name) {
        LegalEntity entity = new LegalEntity();
        entity.setCnpj(cnpj);
        entity.setCorporateName(name);
        return legalEntityRepository.save(entity);
    }
}