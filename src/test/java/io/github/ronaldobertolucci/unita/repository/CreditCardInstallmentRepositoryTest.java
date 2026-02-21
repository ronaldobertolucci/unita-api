package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.*;
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
class CreditCardInstallmentRepositoryTest extends BaseRepositoryTest {

    @Autowired private CreditCardInstallmentRepository installmentRepository;
    @Autowired private CreditCardPurchaseRepository purchaseRepository;
    @Autowired private CreditCardBillRepository billRepository;
    @Autowired private CreditCardRepository creditCardRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private CardBrandRepository cardBrandRepository;

    private CreditCard card;
    private CreditCardBill bill;
    private CreditCardBill otherBill;
    private CreditCardPurchase purchase;
    private CreditCardPurchase otherPurchase;

    @BeforeEach
    void setUp() {
        User user = saveUser("user@test.com");

        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco");
        legalEntityRepository.save(le);

        CardBrand brand = cardBrandRepository.findAll().get(0);

        card = creditCardRepository.save(CreditCard.builder()
                .user(user).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20).build());

        bill = saveBill(LocalDate.of(2024, 1, 10));
        otherBill = saveBill(LocalDate.of(2024, 2, 10));

        purchase = savePurchase();
        otherPurchase = savePurchase();
    }

    @Test
    void findAllByPurchaseId_ShouldReturnOnlyPurchaseInstallments() {
        saveInstallment(purchase, 1, new BigDecimal("50.00"), bill);
        saveInstallment(purchase, 2, new BigDecimal("50.00"), otherBill);
        saveInstallment(otherPurchase, 1, new BigDecimal("100.00"), bill);

        List<CreditCardInstallment> result = installmentRepository.findAllByPurchaseId(purchase.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(i -> i.getPurchase().getId().equals(purchase.getId())));
    }

    @Test
    void findAllByPurchaseId_ShouldReturnOrderedByInstallmentNumberAsc() {
        saveInstallment(purchase, 3, new BigDecimal("33.33"), bill);
        saveInstallment(purchase, 1, new BigDecimal("33.33"), bill);
        saveInstallment(purchase, 2, new BigDecimal("33.33"), otherBill);

        List<CreditCardInstallment> result = installmentRepository.findAllByPurchaseId(purchase.getId());

        assertEquals(1, result.get(0).getInstallmentNumber());
        assertEquals(3, result.get(2).getInstallmentNumber());
    }

    @Test
    void findAllByPurchaseId_ShouldFetchBill() {
        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill);

        List<CreditCardInstallment> result = installmentRepository.findAllByPurchaseId(purchase.getId());

        assertNotNull(result.get(0).getCreditCardBill());
    }

    @Test
    void findAllByBillId_ShouldReturnOnlyBillInstallments() {
        saveInstallment(purchase, 1, new BigDecimal("50.00"), bill);
        saveInstallment(purchase, 2, new BigDecimal("50.00"), otherBill);
        saveInstallment(otherPurchase, 1, new BigDecimal("100.00"), bill);

        List<CreditCardInstallment> result = installmentRepository.findAllByBillId(bill.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(i -> i.getCreditCardBill().getId().equals(bill.getId())));
    }

    @Test
    void findAllByBillId_ShouldFetchPurchase() {
        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill);

        List<CreditCardInstallment> result = installmentRepository.findAllByBillId(bill.getId());

        assertNotNull(result.get(0).getPurchase());
    }

    @Test
    void sumAmountByBillId_ShouldSumAllInstallmentsOfBill() {
        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill);
        saveInstallment(otherPurchase, 1, new BigDecimal("200.00"), bill);
        saveInstallment(purchase, 2, new BigDecimal("999.00"), otherBill); // não deve contar

        BigDecimal sum = installmentRepository.sumAmountByBillId(bill.getId());

        assertEquals(0, new BigDecimal("300.00").compareTo(sum));
    }

    @Test
    void sumAmountByBillId_WhenNoInstallments_ShouldReturnZero() {
        BigDecimal sum = installmentRepository.sumAmountByBillId(bill.getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(sum));
    }

    @Test
    void findByIdAndPurchaseId_WhenMatch_ShouldReturnInstallment() {
        CreditCardInstallment saved = saveInstallment(purchase, 1, new BigDecimal("100.00"), bill);

        Optional<CreditCardInstallment> found = installmentRepository.findByIdAndPurchaseId(saved.getId(), purchase.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndPurchaseId_WhenWrongPurchase_ShouldReturnEmpty() {
        CreditCardInstallment saved = saveInstallment(purchase, 1, new BigDecimal("100.00"), bill);

        assertTrue(installmentRepository.findByIdAndPurchaseId(saved.getId(), otherPurchase.getId()).isEmpty());
    }

    @Test
    void deleteAllByPurchaseId_ShouldRemoveAllInstallmentsOfPurchase() {
        saveInstallment(purchase, 1, new BigDecimal("50.00"), bill);
        saveInstallment(purchase, 2, new BigDecimal("50.00"), otherBill);
        saveInstallment(otherPurchase, 1, new BigDecimal("100.00"), bill);

        installmentRepository.deleteAllByPurchaseId(purchase.getId());

        assertTrue(installmentRepository.findAllByPurchaseId(purchase.getId()).isEmpty());
        assertEquals(1, installmentRepository.findAllByPurchaseId(otherPurchase.getId()).size());
    }

    private CreditCardBill saveBill(LocalDate closingDate) {
        return billRepository.save(CreditCardBill.builder()
                .creditCard(card).closingDate(closingDate)
                .dueDate(closingDate.plusMonths(1)).status(CreditCardBillStatus.OPEN).build());
    }

    private CreditCardPurchase savePurchase() {
        return purchaseRepository.save(CreditCardPurchase.builder()
                .creditCard(card).description("Compra").totalValue(new BigDecimal("100.00"))
                .purchaseDate(LocalDate.now()).installmentsCount(1).build());
    }

    private CreditCardInstallment saveInstallment(CreditCardPurchase purchase, int number, BigDecimal amount, CreditCardBill bill) {
        return installmentRepository.save(CreditCardInstallment.builder()
                .purchase(purchase).installmentNumber(number).amount(amount).creditCardBill(bill).build());
    }
}