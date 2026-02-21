package io.github.ronaldobertolucci.unita.model.pocket;

import io.github.ronaldobertolucci.unita.model.finance.BankAccountType;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import jakarta.persistence.*;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "bank_accounts")
@DiscriminatorValue("BANK_ACCOUNT")
@PrimaryKeyJoinColumn(name = "pocket_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BankAccount extends Pocket {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @Column(name = "number", nullable = false, length = 20)
    private String number;

    @Column(name = "agency", nullable = false, length = 10)
    private String agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_type_id", nullable = false)
    private BankAccountType bankAccountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private BankAccountStatus status;
}