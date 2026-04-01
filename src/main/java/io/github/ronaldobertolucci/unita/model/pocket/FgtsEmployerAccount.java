package io.github.ronaldobertolucci.unita.model.pocket;

import io.github.ronaldobertolucci.unita.model.employer.Employer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "fgts_employer_accounts")
@DiscriminatorValue("FGTS_EMPLOYER_ACCOUNT")
@PrimaryKeyJoinColumn(name = "pocket_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FgtsEmployerAccount extends Pocket {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "dismissal_date")
    private LocalDate dismissalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FgtsEmployerAccountStatus status;

    @Override
    public String getLabel() {
        return this.getEmployer().getName();
    }
}