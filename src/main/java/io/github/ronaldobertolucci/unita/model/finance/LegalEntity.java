package io.github.ronaldobertolucci.unita.model.finance;

import io.github.ronaldobertolucci.unita.model.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "legal_entities", uniqueConstraints = @UniqueConstraint(
        name = "uk_legal_entities_user_cnpj", columnNames = {"user_id", "cnpj"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cnpj", nullable = false, length = 14)
    private String cnpj;

    @Column(name = "corporate_name", nullable = false, length = 255)
    private String corporateName;

    @Column(name = "trade_name", length = 255)
    private String tradeName;

    @Column(name = "state_registration", length = 50)
    private String stateRegistration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}