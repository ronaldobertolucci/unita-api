package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LegalEntityEmployerRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private LegalEntityEmployerRepository legalEntityEmployerRepository;

    @Autowired
    private LegalEntityRepository legalEntityRepository;

    private User user;
    private User otherUser;
    private LegalEntity savedLegalEntity;
    private LegalEntityEmployer savedEmployer;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");

        savedLegalEntity = saveLegalEntity("12345678000190", "Empresa LTDA", user);

        savedEmployer = saveLegalEntityEmployer(savedLegalEntity, user);
    }

    // -------------------------------------------------------------------------
    // existsByLegalEntityId (método original mantido)
    // -------------------------------------------------------------------------

    @Test
    void existsByLegalEntityId_WhenExists_ShouldReturnTrue() {
        assertTrue(legalEntityEmployerRepository.existsByLegalEntityId(savedLegalEntity.getId()));
    }

    @Test
    void existsByLegalEntityId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(legalEntityEmployerRepository.existsByLegalEntityId(999L));
    }

    // -------------------------------------------------------------------------
    // findAllByUserId
    // -------------------------------------------------------------------------

    @Test
    void findAllByUserId_WhenUserHasEmployers_ShouldReturnList() {
        LegalEntity le2 = saveLegalEntity("99999999000199", "Outra Empresa", user);
        saveLegalEntityEmployer(le2, user);

        List<LegalEntityEmployer> result = legalEntityEmployerRepository.findAllByUserId(user.getId());

        assertEquals(2, result.size());
    }

    @Test
    void findAllByUserId_WhenUserHasNoEmployers_ShouldReturnEmptyList() {
        List<LegalEntityEmployer> result = legalEntityEmployerRepository.findAllByUserId(otherUser.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllByUserId_ShouldNotReturnOtherUsersEmployers() {
        LegalEntity otherLe = saveLegalEntity("99999999000199", "Outra Empresa", otherUser);
        saveLegalEntityEmployer(otherLe, otherUser);

        List<LegalEntityEmployer> result = legalEntityEmployerRepository.findAllByUserId(user.getId());

        assertEquals(1, result.size());
        assertEquals(savedLegalEntity.getId(), result.get(0).getLegalEntity().getId());
    }

    // -------------------------------------------------------------------------
    // findByIdAndUserId
    // -------------------------------------------------------------------------

    @Test
    void findByIdAndUserId_WhenOwned_ShouldReturnEmployer() {
        Optional<LegalEntityEmployer> result = legalEntityEmployerRepository
                .findByIdAndUserId(savedEmployer.getId(), user.getId());

        assertTrue(result.isPresent());
        assertEquals(savedLegalEntity.getId(), result.get().getLegalEntity().getId());
    }

    @Test
    void findByIdAndUserId_WhenNotOwned_ShouldReturnEmpty() {
        Optional<LegalEntityEmployer> result = legalEntityEmployerRepository
                .findByIdAndUserId(savedEmployer.getId(), otherUser.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenNotExists_ShouldReturnEmpty() {
        Optional<LegalEntityEmployer> result = legalEntityEmployerRepository
                .findByIdAndUserId(999L, user.getId());

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // existsByIdAndUserId
    // -------------------------------------------------------------------------

    @Test
    void existsByIdAndUserId_WhenOwned_ShouldReturnTrue() {
        assertTrue(legalEntityEmployerRepository.existsByIdAndUserId(savedEmployer.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwned_ShouldReturnFalse() {
        assertFalse(legalEntityEmployerRepository.existsByIdAndUserId(savedEmployer.getId(), otherUser.getId()));
    }

    // -------------------------------------------------------------------------
    // existsByLegalEntityIdAndUserId
    // -------------------------------------------------------------------------

    @Test
    void existsByLegalEntityIdAndUserId_WhenExists_ShouldReturnTrue() {
        assertTrue(legalEntityEmployerRepository
                .existsByLegalEntityIdAndUserId(savedLegalEntity.getId(), user.getId()));
    }

    @Test
    void existsByLegalEntityIdAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(legalEntityEmployerRepository
                .existsByLegalEntityIdAndUserId(savedLegalEntity.getId(), otherUser.getId()));
    }

    @Test
    void existsByLegalEntityIdAndUserId_WhenSameLegalEntityDifferentUser_ShouldReturnFalse() {
        assertFalse(legalEntityEmployerRepository
                .existsByLegalEntityIdAndUserId(savedLegalEntity.getId(), otherUser.getId()));
    }

    // -------------------------------------------------------------------------
    // existsFgtsAccountByEmployerId
    // -------------------------------------------------------------------------

    @Test
    void existsFgtsAccountByEmployerId_WhenNoFgtsAccount_ShouldReturnFalse() {
        assertFalse(legalEntityEmployerRepository.existsFgtsAccountByEmployerId(savedEmployer.getId()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LegalEntity saveLegalEntity(String cnpj, String name, User user) {
        LegalEntity entity = new LegalEntity();
        entity.setCnpj(cnpj);
        entity.setCorporateName(name);
        entity.setUser(user);
        return legalEntityRepository.save(entity);
    }

    private LegalEntityEmployer saveLegalEntityEmployer(LegalEntity legalEntity, User user) {
        LegalEntityEmployer employer = new LegalEntityEmployer();
        employer.setLegalEntity(legalEntity);
        employer.setUser(user);
        return legalEntityEmployerRepository.save(employer);
    }
}