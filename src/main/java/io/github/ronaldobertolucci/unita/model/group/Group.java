package io.github.ronaldobertolucci.unita.model.group;

import io.github.ronaldobertolucci.unita.model.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "groups",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_groups_name_responsible",
                columnNames = {"name", "responsible_user_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;
}