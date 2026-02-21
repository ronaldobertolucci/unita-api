package io.github.ronaldobertolucci.unita.model.finance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bank_account_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
}