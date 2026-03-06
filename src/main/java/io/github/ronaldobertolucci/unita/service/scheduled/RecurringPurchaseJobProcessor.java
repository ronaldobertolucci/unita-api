package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardInstallment;
import io.github.ronaldobertolucci.unita.model.card.CreditCardPurchase;
import io.github.ronaldobertolucci.unita.model.card.RecurringPurchase;
import io.github.ronaldobertolucci.unita.repository.CreditCardInstallmentRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardPurchaseRepository;
import io.github.ronaldobertolucci.unita.repository.RecurringPurchaseRepository;
import io.github.ronaldobertolucci.unita.service.card.CreditCardBillResolverService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringPurchaseJobProcessor {

    private final RecurringPurchaseRepository recurringPurchaseRepository;
    private final CreditCardPurchaseRepository creditCardPurchaseRepository;
    private final CreditCardInstallmentRepository creditCardInstallmentRepository;
    private final CreditCardBillResolverService billResolverService;

    // REQUIRES_NEW garante que cada registro é commitado de forma independente.
    // A entidade é re-buscada por ID dentro desta transação, permitindo que
    // associações lazy (creditCard, category) sejam carregadas corretamente.
    // billResolverService.findOrCreateForDate usa propagação REQUIRED e,
    // portanto, participa desta mesma transação.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long recurringPurchaseId, LocalDate today) {
        RecurringPurchase rp = recurringPurchaseRepository.findById(recurringPurchaseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "RecurringPurchase not found: " + recurringPurchaseId));

        CreditCard creditCard = rp.getCreditCard();

        CreditCardPurchase purchase = CreditCardPurchase.builder()
                .creditCard(creditCard)
                .description(rp.getDescription())
                .totalValue(rp.getAmount())
                .purchaseDate(today)
                .installmentsCount(1)
                .build();

        creditCardPurchaseRepository.save(purchase);

        CreditCardBill bill = billResolverService.findOrCreateForDate(creditCard, today);

        CreditCardInstallment installment = CreditCardInstallment.builder()
                .purchase(purchase)
                .installmentNumber(1)
                .amount(rp.getAmount())
                .creditCardBill(bill)
                .category(rp.getCategory())
                .build();

        creditCardInstallmentRepository.save(installment);

        rp.setLastGeneratedDate(today);
        recurringPurchaseRepository.save(rp);
    }
}