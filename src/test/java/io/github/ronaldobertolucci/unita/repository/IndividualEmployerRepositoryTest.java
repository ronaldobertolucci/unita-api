package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IndividualEmployerRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private IndividualEmployerRepository individualEmployerRepository;

    private User user;
    private User otherUser;
    private IndividualEmployer savedEmployer;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");

        savedEmployer = saveIndividualEmployer("12345678901", "João Silva", user);
    }

    // -------------------------------------------------------------------------
    // findAllByUserId
    // -------------------------------------------------------------------------

    @Test
    void findAllByUserId_WhenUserHasEmployers_ShouldReturnList() {
        saveIndividualEmployer("98765432100", "Maria", user);

        List<IndividualEmployer> result = individualEmployerRepository.findAllByUserIdOrderByName(user.getId());

        assertEquals(2, result.size());
    }

    @Test
    void findAllByUserId_WhenUserHasNoEmployers_ShouldReturnEmptyList() {
        List<IndividualEmployer> result = individualEmployerRepository.findAllByUserIdOrderByName(otherUser.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllByUserId_ShouldNotReturnOtherUsersEmployers() {
        saveIndividualEmployer("98765432100", "Maria", otherUser);

        List<IndividualEmployer> result = individualEmployerRepository.findAllByUserIdOrderByName(user.getId());

        assertEquals(1, result.size());
        assertEquals("João Silva", result.get(0).getName());
    }

    // -------------------------------------------------------------------------
    // findByIdAndUserId
    // -------------------------------------------------------------------------

    @Test
    void findByIdAndUserId_WhenOwned_ShouldReturnEmployer() {
        Optional<IndividualEmployer> result = individualEmployerRepository
                .findByIdAndUserId(savedEmployer.getId(), user.getId());

        assertTrue(result.isPresent());
        assertEquals("João Silva", result.get().getName());
    }

    @Test
    void findByIdAndUserId_WhenNotOwned_ShouldReturnEmpty() {
        Optional<IndividualEmployer> result = individualEmployerRepository
                .findByIdAndUserId(savedEmployer.getId(), otherUser.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenNotExists_ShouldReturnEmpty() {
        Optional<IndividualEmployer> result = individualEmployerRepository
                .findByIdAndUserId(999L, user.getId());

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // existsByIdAndUserId
    // -------------------------------------------------------------------------

    @Test
    void existsByIdAndUserId_WhenOwned_ShouldReturnTrue() {
        assertTrue(individualEmployerRepository.existsByIdAndUserId(savedEmployer.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwned_ShouldReturnFalse() {
        assertFalse(individualEmployerRepository.existsByIdAndUserId(savedEmployer.getId(), otherUser.getId()));
    }

    // -------------------------------------------------------------------------
    // existsByCpfAndUserId
    // -------------------------------------------------------------------------

    @Test
    void existsByCpfAndUserId_WhenExists_ShouldReturnTrue() {
        assertTrue(individualEmployerRepository.existsByCpfAndUserId("12345678901", user.getId()));
    }

    @Test
    void existsByCpfAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(individualEmployerRepository.existsByCpfAndUserId("99999999999", user.getId()));
    }

    @Test
    void existsByCpfAndUserId_WhenSameCpfDifferentUser_ShouldReturnFalse() {
        assertFalse(individualEmployerRepository.existsByCpfAndUserId("12345678901", otherUser.getId()));
    }

    @Test
    void existsByCpfAndUserId_WhenSameCpfSameUser_ShouldReturnTrue() {
        saveIndividualEmployer("12345678901", "João Clone", otherUser);

        assertTrue(individualEmployerRepository.existsByCpfAndUserId("12345678901", user.getId()));
        assertTrue(individualEmployerRepository.existsByCpfAndUserId("12345678901", otherUser.getId()));
    }

    // -------------------------------------------------------------------------
    // existsFgtsAccountByEmployerId
    // -------------------------------------------------------------------------

    @Test
    void existsFgtsAccountByEmployerId_WhenNoFgtsAccount_ShouldReturnFalse() {
        assertFalse(individualEmployerRepository.existsFgtsAccountByEmployerId(savedEmployer.getId()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private IndividualEmployer saveIndividualEmployer(String cpf, String name, User user) {
        IndividualEmployer employer = new IndividualEmployer();
        employer.setCpf(cpf);
        employer.setName(name);
        employer.setUser(user);
        return individualEmployerRepository.save(employer);
    }
}