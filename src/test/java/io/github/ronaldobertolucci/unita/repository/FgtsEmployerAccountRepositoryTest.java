package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccount;
import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccountStatus;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FgtsEmployerAccountRepositoryTest extends BaseRepositoryTest {

    @Autowired private FgtsEmployerAccountRepository fgtsRepository;
    @Autowired private IndividualEmployerRepository individualEmployerRepository;

    private User user;
    private User otherUser;
    private FgtsEmployerAccount savedAccount;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");

        IndividualEmployer employer = new IndividualEmployer();
        employer.setCpf("12345678901");
        employer.setName("Empregador Teste");
        individualEmployerRepository.save(employer);

        savedAccount = fgtsRepository.save(FgtsEmployerAccount.builder()
                .user(user)
                .employer(employer)
                .admissionDate(LocalDate.of(2020, 1, 1))
                .status(FgtsEmployerAccountStatus.ACTIVE)
                .build());
    }

    @Test
    void findByIdAndUserId_WhenOwnerAndExists_ShouldReturnWithFetchedEmployer() {
        Optional<FgtsEmployerAccount> found = fgtsRepository.findByIdAndUserId(savedAccount.getId(), user.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getEmployer());
    }

    @Test
    void findByIdAndUserId_WhenNotOwner_ShouldReturnEmpty() {
        assertTrue(fgtsRepository.findByIdAndUserId(savedAccount.getId(), otherUser.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(fgtsRepository.findByIdAndUserId(999L, user.getId()).isEmpty());
    }

    @Test
    void existsByIdAndUserId_WhenOwner_ShouldReturnTrue() {
        assertTrue(fgtsRepository.existsByIdAndUserId(savedAccount.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwner_ShouldReturnFalse() {
        assertFalse(fgtsRepository.existsByIdAndUserId(savedAccount.getId(), otherUser.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(fgtsRepository.existsByIdAndUserId(999L, user.getId()));
    }
}