package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.RecurringPurchase;
import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
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
class RecurringPurchaseRepositoryTest extends BaseRepositoryTest {

    @Autowired private RecurringPurchaseRepository recurringPurchaseRepository;
    @Autowired private CreditCardRepository creditCardRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private CardBrandRepository cardBrandRepository;
    @Autowired private RecurrencePeriodicityRepository periodicityRepository;

    private CreditCard card;
    private CreditCard otherCard;
    private RecurrencePeriodicity periodicity;

    @BeforeEach
    void setUp() {
        User user = saveUser("user@test.com");
        User other = saveUser("other@test.com");

        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco");
        legalEntityRepository.save(le);

        CardBrand brand = cardBrandRepository.findAll().get(0);

        card = creditCardRepository.save(CreditCard.builder()
                .user(user).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20).build());
        otherCard = creditCardRepository.save(CreditCard.builder()
                .user(other).legalEntity(le).lastFourDigits("5678")
                .cardBrand(brand).creditLimit(new BigDecimal("3000")).closingDay(15).dueDay(25).build());

        periodicity = periodicityRepository.findAll().stream()
                .filter(p -> p.getName().equals("Mensal"))
                .findFirst().orElseThrow();
    }

    @Test
    void findAllByCreditCardId_ShouldReturnOnlyCardRecurrings() {
        saveRecurring(card, LocalDate.now().minusDays(1), null);
        saveRecurring(otherCard, LocalDate.now().minusDays(1), null);

        List<RecurringPurchase> result = recurringPurchaseRepository.findAllByCreditCardId(card.getId());

        assertEquals(1, result.size());
        assertEquals(card.getId(), result.get(0).getCreditCard().getId());
    }

    @Test
    void findAllByCreditCardId_ShouldReturnOrderedByStartDateAsc() {
        saveRecurring(card, LocalDate.of(2024, 3, 1), null);
        saveRecurring(card, LocalDate.of(2024, 1, 1), null);
        saveRecurring(card, LocalDate.of(2024, 2, 1), null);

        List<RecurringPurchase> result = recurringPurchaseRepository.findAllByCreditCardId(card.getId());

        assertEquals(LocalDate.of(2024, 1, 1), result.get(0).getStartDate());
        assertEquals(LocalDate.of(2024, 3, 1), result.get(2).getStartDate());
    }

    @Test
    void findAllByCreditCardId_ShouldFetchPeriodicity() {
        saveRecurring(card, LocalDate.now(), null);

        List<RecurringPurchase> result = recurringPurchaseRepository.findAllByCreditCardId(card.getId());

        assertNotNull(result.get(0).getPeriodicity());
    }

    @Test
    void findAllActive_ShouldReturnOnlyActiveRecurrings() {
        LocalDate today = LocalDate.now();
        saveRecurring(card, today.minusMonths(1), null);              // ativo (sem fim)
        saveRecurring(card, today.minusMonths(2), today.plusDays(5)); // ativo (fim no futuro)
        saveRecurring(card, today.minusMonths(3), today.minusDays(1)); // inativo (fim no passado)
        saveRecurring(otherCard, today.plusDays(1), null);             // inativo (não começou)

        List<RecurringPurchase> active = recurringPurchaseRepository.findAllActive(today);

        assertEquals(2, active.size());
    }

    @Test
    void findAllActive_ShouldFetchCardAndPeriodicity() {
        saveRecurring(card, LocalDate.now().minusDays(1), null);

        List<RecurringPurchase> active = recurringPurchaseRepository.findAllActive(LocalDate.now());

        assertNotNull(active.get(0).getCreditCard());
        assertNotNull(active.get(0).getPeriodicity());
    }

    @Test
    void findByIdAndCreditCardId_WhenMatch_ShouldReturnRecurring() {
        RecurringPurchase saved = saveRecurring(card, LocalDate.now(), null);

        Optional<RecurringPurchase> found = recurringPurchaseRepository.findByIdAndCreditCardId(saved.getId(), card.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndCreditCardId_WhenWrongCard_ShouldReturnEmpty() {
        RecurringPurchase saved = saveRecurring(card, LocalDate.now(), null);

        assertTrue(recurringPurchaseRepository.findByIdAndCreditCardId(saved.getId(), otherCard.getId()).isEmpty());
    }

    private RecurringPurchase saveRecurring(CreditCard card, LocalDate startDate, LocalDate endDate) {
        return recurringPurchaseRepository.save(RecurringPurchase.builder()
                .creditCard(card).description("Assinatura").amount(new BigDecimal("49.90"))
                .periodicity(periodicity).startDate(startDate).endDate(endDate).build());
    }
}