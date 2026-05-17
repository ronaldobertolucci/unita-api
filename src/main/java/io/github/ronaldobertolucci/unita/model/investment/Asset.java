package io.github.ronaldobertolucci.unita.model.investment;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "assets")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 15)
    private AssetCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private AssetStatus status = AssetStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "liquidity_type", length = 20)
    private LiquidityType liquidityType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custodian_legal_entity_id")
    private LegalEntity custodianLegalEntity;

    @OneToOne(mappedBy = "asset", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private FixedIncomeDetails fixedIncomeDetails;

    @OneToOne(mappedBy = "asset", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private PensionDetails pensionDetails;

    @OneToOne(mappedBy = "asset", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private InvestmentPosition position;
}