package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.BankAccountType;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccount;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccountStatus;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BankAccountRepositoryTest extends BaseRepositoryTest {

    @Autowired private BankAccountRepository bankAccountRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private BankAccountTypeRepository bankAccountTypeRepository;

    private User user;
    private User otherUser;
    private BankAccount savedAccount;
    private LegalEntity legalEntity;
    private BankAccountType accountType;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");

        legalEntity = new LegalEntity();
        legalEntity.setCnpj("12345678000190");
        legalEntity.setCorporateName("Banco Teste");
        legalEntity.setUser(user);
        legalEntityRepository.save(legalEntity);

        accountType = bankAccountTypeRepository.findAll().get(0);

        savedAccount = bankAccountRepository.save(BankAccount.builder()
                .user(user)
                .legalEntity(legalEntity)
                .number("12345-6")
                .agency("0001")
                .bankAccountType(accountType)
                .status(BankAccountStatus.ACTIVE)
                .build());
    }

    @Test
    void findByIdAndUserId_WhenOwnerAndExists_ShouldReturnWithFetchedRelations() {
        Optional<BankAccount> found = bankAccountRepository.findByIdAndUserId(savedAccount.getId(), user.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getLegalEntity());
        assertNotNull(found.get().getBankAccountType());
    }

    @Test
    void findByIdAndUserId_WhenNotOwner_ShouldReturnEmpty() {
        assertTrue(bankAccountRepository.findByIdAndUserId(savedAccount.getId(), otherUser.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(bankAccountRepository.findByIdAndUserId(999L, user.getId()).isEmpty());
    }

    @Test
    void existsByIdAndUserId_WhenOwner_ShouldReturnTrue() {
        assertTrue(bankAccountRepository.existsByIdAndUserId(savedAccount.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwner_ShouldReturnFalse() {
        assertFalse(bankAccountRepository.existsByIdAndUserId(savedAccount.getId(), otherUser.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(bankAccountRepository.existsByIdAndUserId(999L, user.getId()));
    }
}