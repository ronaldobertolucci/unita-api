package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardPurchase;
import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreditCardPurchaseRepositoryTest extends BaseRepositoryTest {

    @Autowired private CreditCardPurchaseRepository purchaseRepository;
    @Autowired private CreditCardRepository creditCardRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private CardBrandRepository cardBrandRepository;

    private CreditCard card;
    private CreditCard otherCard;

    @BeforeEach
    void setUp() {
        User user = saveUser("user@test.com");
        User other = saveUser("other@test.com");

        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco");
        le.setUser(user);
        legalEntityRepository.save(le);

        CardBrand brand = cardBrandRepository.findAll().get(0);

        card = creditCardRepository.save(CreditCard.builder()
                .user(user).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20).build());
        otherCard = creditCardRepository.save(CreditCard.builder()
                .user(other).legalEntity(le).lastFourDigits("5678")
                .cardBrand(brand).creditLimit(new BigDecimal("3000")).closingDay(15).dueDay(25).build());
    }

    @Test
    void findAllByCreditCardId_ShouldReturnOnlyCardPurchases() {
        savePurchase(card, LocalDate.of(2024, 1, 5));
        savePurchase(otherCard, LocalDate.of(2024, 1, 5));

        List<CreditCardPurchase> result = purchaseRepository.findAllByCreditCardId(card.getId());

        assertEquals(1, result.size());
        assertEquals(card.getId(), result.get(0).getCreditCard().getId());
    }

    @Test
    void findAllByCreditCardId_ShouldReturnOrderedByPurchaseDateDesc() {
        savePurchase(card, LocalDate.of(2024, 1, 1));
        savePurchase(card, LocalDate.of(2024, 3, 1));
        savePurchase(card, LocalDate.of(2024, 2, 1));

        List<CreditCardPurchase> result = purchaseRepository.findAllByCreditCardId(card.getId());

        assertEquals(LocalDate.of(2024, 3, 1), result.get(0).getPurchaseDate());
        assertEquals(LocalDate.of(2024, 1, 1), result.get(2).getPurchaseDate());
    }

    @Test
    void findAllByCreditCardId_WhenNone_ShouldReturnEmpty() {
        assertTrue(purchaseRepository.findAllByCreditCardId(card.getId()).isEmpty());
    }

    @Test
    void findByIdAndCreditCardId_WhenMatch_ShouldReturnPurchase() {
        CreditCardPurchase saved = savePurchase(card, LocalDate.now());

        Optional<CreditCardPurchase> found = purchaseRepository.findByIdAndCreditCardId(saved.getId(), card.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndCreditCardId_WhenWrongCard_ShouldReturnEmpty() {
        CreditCardPurchase saved = savePurchase(card, LocalDate.now());

        assertTrue(purchaseRepository.findByIdAndCreditCardId(saved.getId(), otherCard.getId()).isEmpty());
    }

    @Test
    void findByIdAndCreditCardId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(purchaseRepository.findByIdAndCreditCardId(999L, card.getId()).isEmpty());
    }

    private CreditCardPurchase savePurchase(CreditCard card, LocalDate date) {
        return purchaseRepository.save(CreditCardPurchase.builder()
                .creditCard(card)
                .description("Compra teste")
                .totalValue(new BigDecimal("100.00"))
                .purchaseDate(date)
                .installmentsCount(1)
                .build());
    }
}