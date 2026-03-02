package io.github.ronaldobertolucci.unita.model.investment;

import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "investment_transactions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class InvestmentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 5)
    private InvestmentTransactionType type;

    @Column(name = "amount", nullable = false, precision = 18, scale = 8)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "notes", length = 500)
    private String notes;
}