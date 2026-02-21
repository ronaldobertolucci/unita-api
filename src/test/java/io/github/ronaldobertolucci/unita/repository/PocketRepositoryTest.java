package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.BankAccountType;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccount;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccountStatus;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PocketRepositoryTest extends BaseRepositoryTest {

    @Autowired private PocketRepository pocketRepository;
    @Autowired private BankAccountRepository bankAccountRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private BankAccountTypeRepository bankAccountTypeRepository;

    private User user;
    private User otherUser;
    private BankAccount savedAccount;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");
        savedAccount = saveBankAccount(user);
    }

    @Test
    void findAllByUserId_ShouldReturnOnlyUserPockets() {
        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000191");
        le.setCorporateName("Banco Teste");
        legalEntityRepository.save(le);

        BankAccountType type = bankAccountTypeRepository.findAll().get(0);

        BankAccount account = BankAccount.builder()
                .user(otherUser)
                .legalEntity(le)
                .number("12345")
                .agency("0001")
                .bankAccountType(type)
                .status(BankAccountStatus.ACTIVE)
                .build();

        List<Pocket> pockets = pocketRepository.findAllByUserId(user.getId());

        assertEquals(1, pockets.size());
        assertEquals(user.getId(), pockets.get(0).getUser().getId());
    }

    @Test
    void findAllByUserId_WhenNoPockets_ShouldReturnEmpty() {
        User emptyUser = saveUser("empty@test.com");

        List<Pocket> pockets = pocketRepository.findAllByUserId(emptyUser.getId());

        assertTrue(pockets.isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenOwner_ShouldReturnPocket() {
        Optional<Pocket> found = pocketRepository.findByIdAndUserId(savedAccount.getId(), user.getId());

        assertTrue(found.isPresent());
        assertEquals(savedAccount.getId(), found.get().getId());
    }

    @Test
    void findByIdAndUserId_WhenNotOwner_ShouldReturnEmpty() {
        Optional<Pocket> found = pocketRepository.findByIdAndUserId(savedAccount.getId(), otherUser.getId());

        assertTrue(found.isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(pocketRepository.findByIdAndUserId(999L, user.getId()).isEmpty());
    }

    @Test
    void existsByIdAndUserId_WhenOwner_ShouldReturnTrue() {
        assertTrue(pocketRepository.existsByIdAndUserId(savedAccount.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwner_ShouldReturnFalse() {
        assertFalse(pocketRepository.existsByIdAndUserId(savedAccount.getId(), otherUser.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(pocketRepository.existsByIdAndUserId(999L, user.getId()));
    }

    private BankAccount saveBankAccount(User owner) {
        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        legalEntityRepository.save(le);

        BankAccountType type = bankAccountTypeRepository.findAll().get(0);

        BankAccount account = BankAccount.builder()
                .user(owner)
                .legalEntity(le)
                .number("12345")
                .agency("0001")
                .bankAccountType(type)
                .status(BankAccountStatus.ACTIVE)
                .build();
        return bankAccountRepository.save(account);
    }
}