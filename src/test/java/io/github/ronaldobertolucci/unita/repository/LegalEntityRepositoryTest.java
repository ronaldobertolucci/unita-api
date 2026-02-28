package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LegalEntityRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    @Test
    void save_ShouldPersistLegalEntity() {
        User user = saveUser("user@test.com");

        LegalEntity entity = buildLegalEntity("12345678000190", "Empresa Teste LTDA", user);

        LegalEntity saved = legalEntityRepository.save(entity);

        assertNotNull(saved.getId());
        assertEquals("12345678000190", saved.getCnpj());
        assertEquals("Empresa Teste LTDA", saved.getCorporateName());
    }

    @Test
    void findById_WhenExists_ShouldReturnLegalEntity() {
        User user = saveUser("user@test.com");

        LegalEntity saved = legalEntityRepository.save(buildLegalEntity("12345678000190", "Empresa Teste LTDA", user));

        Optional<LegalEntity> found = legalEntityRepository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(legalEntityRepository.findById(999L).isEmpty());
    }

    @Test
    void findAll_ShouldReturnAllLegalEntities() {
        User user = saveUser("user@test.com");

        legalEntityRepository.save(buildLegalEntity("11111111000101", "Empresa A", user));
        legalEntityRepository.save(buildLegalEntity("22222222000102", "Empresa B", user));

        List<LegalEntity> all = legalEntityRepository.findAll();

        assertTrue(all.size() >= 2);
    }

    @Test
    void delete_ShouldRemoveLegalEntity() {
        User user = saveUser("user@test.com");

        LegalEntity saved = legalEntityRepository.save(buildLegalEntity("12345678000190", "Empresa Teste LTDA", user));

        legalEntityRepository.delete(saved);

        assertTrue(legalEntityRepository.findById(saved.getId()).isEmpty());
    }

    private LegalEntity buildLegalEntity(String cnpj, String corporateName, User user) {
        LegalEntity entity = new LegalEntity();
        entity.setCnpj(cnpj);
        entity.setCorporateName(corporateName);
        entity.setTradeName("Empresa Fantasia");
        entity.setUser(user);
        return entity;
    }
}