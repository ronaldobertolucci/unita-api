package io.github.ronaldobertolucci.unita.model.card;

import io.github.ronaldobertolucci.unita.model.finance.Category;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "credit_card_installments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCardInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private CreditCardPurchase purchase;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "installment_date", nullable = false)
    private LocalDate installmentDate;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_bill_id", nullable = false)
    private CreditCardBill creditCardBill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}