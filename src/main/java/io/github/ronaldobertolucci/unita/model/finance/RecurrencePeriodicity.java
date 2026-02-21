package io.github.ronaldobertolucci.unita.model.finance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recurrence_periodicities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurrencePeriodicity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private PeriodicityType type;
}