package io.github.ronaldobertolucci.unita.model.investment;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "investment_positions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class InvestmentPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false, unique = true)
    private Asset asset;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "average_price", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal averagePrice = BigDecimal.ZERO;

    @Column(name = "total_invested", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal totalInvested = BigDecimal.ZERO;

    @Column(name = "current_value", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "redeemed_value", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal redeemedValue = BigDecimal.ZERO;

    @Column(name = "last_valuation_date")
    private LocalDate lastValuationDate;
}