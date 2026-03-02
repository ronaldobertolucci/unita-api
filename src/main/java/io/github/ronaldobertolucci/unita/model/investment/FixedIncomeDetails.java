package io.github.ronaldobertolucci.unita.model.investment;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fixed_income_details")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FixedIncomeDetails {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "indexer", nullable = false, length = 10)
    private Indexer indexer;

    @Column(name = "annual_rate", nullable = false, precision = 10, scale = 8)
    private BigDecimal annualRate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "is_tax_free", nullable = false)
    private boolean taxFree;
}