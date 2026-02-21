package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.BenefitType;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccount;
import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccountStatus;
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
class BenefitAccountRepositoryTest extends BaseRepositoryTest {

    @Autowired private BenefitAccountRepository benefitAccountRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private BenefitTypeRepository benefitTypeRepository;

    private User user;
    private User otherUser;
    private BenefitAccount savedAccount;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");

        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Operadora Benefício");
        legalEntityRepository.save(le);

        BenefitType benefitType = benefitTypeRepository.findAll().get(0);

        savedAccount = benefitAccountRepository.save(BenefitAccount.builder()
                .user(user)
                .legalEntity(le)
                .benefitType(benefitType)
                .status(BenefitAccountStatus.ACTIVE)
                .build());
    }

    @Test
    void findByIdAndUserId_WhenOwnerAndExists_ShouldReturnWithFetchedRelations() {
        Optional<BenefitAccount> found = benefitAccountRepository.findByIdAndUserId(savedAccount.getId(), user.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getLegalEntity());
        assertNotNull(found.get().getBenefitType());
    }

    @Test
    void findByIdAndUserId_WhenNotOwner_ShouldReturnEmpty() {
        assertTrue(benefitAccountRepository.findByIdAndUserId(savedAccount.getId(), otherUser.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(benefitAccountRepository.findByIdAndUserId(999L, user.getId()).isEmpty());
    }

    @Test
    void existsByIdAndUserId_WhenOwner_ShouldReturnTrue() {
        assertTrue(benefitAccountRepository.existsByIdAndUserId(savedAccount.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwner_ShouldReturnFalse() {
        assertFalse(benefitAccountRepository.existsByIdAndUserId(savedAccount.getId(), otherUser.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(benefitAccountRepository.existsByIdAndUserId(999L, user.getId()));
    }
}