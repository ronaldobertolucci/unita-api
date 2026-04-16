package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
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
    @Autowired private CategoryRepository categoryRepository;

    private CreditCard card;
    private CreditCardBill bill;
    private CreditCardBill otherBill;
    private CreditCardPurchase purchase;
    private CreditCardPurchase otherPurchase;
    private Category category;

    @BeforeEach
    void setUp() {
        User user = saveUser("user@test.com");

        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco");
        le.setUser(user);
        legalEntityRepository.save(le);

        CardBrand brand = cardBrandRepository.findAll().get(0);

        card = creditCardRepository.save(CreditCard.builder()
                .user(user).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20).build());

        bill = saveBill(LocalDate.of(2024, 1, 10));
        otherBill = saveBill(LocalDate.of(2024, 2, 10));

        purchase = savePurchase();
        otherPurchase = savePurchase();
        category = categoryRepository.save(Category.builder()
                .user(user)
                .name("Categoria Teste")
                .type(CategoryType.EXPENSE)
                .system(false)
                .build());
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

    @Test
    void sumAmountByCategoryTypeAndUserIdAndPeriod_WhenExpense_ShouldReturnAggregated() {
        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill);
        saveInstallment(purchase, 2, new BigDecimal("200.00"), otherBill);

        List<Object[]> result = installmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                card.getUser().getId(), "EXPENSE", null, null);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("300.00").compareTo((BigDecimal) result.get(0)[1]));
    }

    @Test
    void sumAmountByCategoryTypeAndUserIdAndPeriod_WithDateFilter_ShouldReturnOnlyInPeriod() {
        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill, LocalDate.of(2025, 1, 10));
        saveInstallment(purchase, 2, new BigDecimal("200.00"), otherBill, LocalDate.of(2025, 3, 10));

        List<Object[]> result = installmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                card.getUser().getId(), "EXPENSE",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) result.get(0)[1]));
    }

    @Test
    void sumAmountByCategoryTypeAndUserIdAndPeriod_WhenNoInstallments_ShouldReturnEmpty() {
        List<Object[]> result = installmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                card.getUser().getId(), "EXPENSE", null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void sumInstallmentsByUserIdAndOpenBills_ShouldSumOnlyOpenBills() {
        CreditCardBill paidBill = billRepository.save(CreditCardBill.builder()
                .creditCard(card)
                .periodStart(LocalDate.of(2023, 11, 10))
                .closingDate(LocalDate.of(2023, 12, 10))
                .closingDay(10)
                .dueDate(LocalDate.of(2024, 1, 10))
                .dueDay(10)
                .status(CreditCardBillStatus.PAID)
                .build());

        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill);
        saveInstallment(purchase, 2, new BigDecimal("200.00"), paidBill);

        BigDecimal result = installmentRepository.sumInstallmentsByUserIdAndOpenBills(card.getUser().getId());

        assertEquals(0, new BigDecimal("100.00").compareTo(result));
    }

    @Test
    void sumInstallmentsByUserIdAndOpenBills_WhenNoOpenBills_ShouldReturnZero() {
        BigDecimal result = installmentRepository.sumInstallmentsByUserIdAndOpenBills(card.getUser().getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void sumExpenseAmountByMonthAndUserIdAndPeriod_ShouldGroupByMonth() {
        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill, LocalDate.of(2025, 1, 10));
        saveInstallment(purchase, 2, new BigDecimal("200.00"), otherBill, LocalDate.of(2025, 2, 10));

        List<Object[]> result = installmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(
                card.getUser().getId(), null, null);

        assertEquals(2, result.size());
        assertEquals("2025-01", result.get(0)[0]);
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) result.get(0)[1]));
        assertEquals("2025-02", result.get(1)[0]);
    }

    @Test
    void sumExpenseAmountByMonthAndUserIdAndPeriod_WithDateFilter_ShouldReturnOnlyInPeriod() {
        saveInstallment(purchase, 1, new BigDecimal("100.00"), bill, LocalDate.of(2025, 1, 10));
        saveInstallment(purchase, 2, new BigDecimal("200.00"), otherBill, LocalDate.of(2025, 3, 10));

        List<Object[]> result = installmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(
                card.getUser().getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.size());
        assertEquals("2025-01", result.get(0)[0]);
    }

    @Test
    void sumExpenseAmountByMonthAndUserIdAndPeriod_WhenNoInstallments_ShouldReturnEmpty() {
        List<Object[]> result = installmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(
                card.getUser().getId(), null, null);

        assertTrue(result.isEmpty());
    }

    private CreditCardBill saveBill(LocalDate closingDate) {
        return billRepository.save(CreditCardBill.builder()
                .creditCard(card)
                .periodStart(closingDate.plusMonths(-1))
                .closingDate(closingDate)
                .closingDay(closingDate.getDayOfMonth())
                .dueDate(closingDate.plusMonths(1))
                .dueDay(closingDate.plusMonths(1).getDayOfMonth())
                .status(CreditCardBillStatus.OPEN)
                .build());
    }

    private CreditCardPurchase savePurchase() {
        return purchaseRepository.save(CreditCardPurchase.builder()
                .creditCard(card).description("Compra").totalValue(new BigDecimal("100.00"))
                .purchaseDate(LocalDate.now()).installmentsCount(1).build());
    }

    private CreditCardInstallment saveInstallment(CreditCardPurchase purchase, int number, BigDecimal amount, CreditCardBill bill) {
        return installmentRepository.save(CreditCardInstallment.builder()
                .purchase(purchase).installmentNumber(number).installmentDate(LocalDate.now())
                .amount(amount).creditCardBill(bill).category(category).build());
    }

    private CreditCardInstallment saveInstallment(CreditCardPurchase purchase, int number,
                                                  BigDecimal amount, CreditCardBill bill,
                                                  LocalDate installmentDate) {
        return installmentRepository.save(CreditCardInstallment.builder()
                .purchase(purchase).installmentNumber(number).installmentDate(installmentDate)
                .amount(amount).creditCardBill(bill).category(category).build());
    }
}