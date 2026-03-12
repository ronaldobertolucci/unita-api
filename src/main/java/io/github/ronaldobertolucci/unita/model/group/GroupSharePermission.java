package io.github.ronaldobertolucci.unita.model.group;

import io.github.ronaldobertolucci.unita.model.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "group_share_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id", "share_type"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupSharePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", nullable = false, length = 50)
    private ShareType shareType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}