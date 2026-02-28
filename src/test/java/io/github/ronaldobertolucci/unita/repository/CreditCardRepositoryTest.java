package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreditCardRepositoryTest extends BaseRepositoryTest {

    @Autowired private CreditCardRepository creditCardRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private CardBrandRepository cardBrandRepository;

    private User user;
    private User otherUser;
    private CreditCard savedCard;
    private LegalEntity legalEntity;
    private CardBrand cardBrand;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");

        legalEntity = new LegalEntity();
        legalEntity.setCnpj("12345678000190");
        legalEntity.setCorporateName("Banco Emissor");
        legalEntity.setUser(user);
        legalEntityRepository.save(legalEntity);

        cardBrand = cardBrandRepository.findAll().get(0);

        savedCard = creditCardRepository.save(buildCard(user));
    }

    @Test
    void findAllByUserId_ShouldReturnOnlyUserCards() {
        creditCardRepository.save(buildCard(otherUser));

        List<CreditCard> cards = creditCardRepository.findAllByUserId(user.getId());

        assertEquals(1, cards.size());
        assertEquals(user.getId(), cards.get(0).getUser().getId());
    }

    @Test
    void findAllByUserId_ShouldFetchLegalEntityAndBrand() {
        List<CreditCard> cards = creditCardRepository.findAllByUserId(user.getId());

        assertNotNull(cards.get(0).getLegalEntity());
        assertNotNull(cards.get(0).getCardBrand());
    }

    @Test
    void findAllByUserId_WhenNoCards_ShouldReturnEmpty() {
        assertTrue(creditCardRepository.findAllByUserId(otherUser.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenOwner_ShouldReturnCardWithRelations() {
        Optional<CreditCard> found = creditCardRepository.findByIdAndUserId(savedCard.getId(), user.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getLegalEntity());
        assertNotNull(found.get().getCardBrand());
    }

    @Test
    void findByIdAndUserId_WhenNotOwner_ShouldReturnEmpty() {
        assertTrue(creditCardRepository.findByIdAndUserId(savedCard.getId(), otherUser.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(creditCardRepository.findByIdAndUserId(999L, user.getId()).isEmpty());
    }

    @Test
    void existsByIdAndUserId_WhenOwner_ShouldReturnTrue() {
        assertTrue(creditCardRepository.existsByIdAndUserId(savedCard.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwner_ShouldReturnFalse() {
        assertFalse(creditCardRepository.existsByIdAndUserId(savedCard.getId(), otherUser.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(creditCardRepository.existsByIdAndUserId(999L, user.getId()));
    }

    private CreditCard buildCard(User owner) {
        return CreditCard.builder()
                .user(owner)
                .legalEntity(legalEntity)
                .lastFourDigits("1234")
                .cardBrand(cardBrand)
                .creditLimit(new BigDecimal("5000.00"))
                .closingDay(10)
                .dueDay(20)
                .build();
    }
}