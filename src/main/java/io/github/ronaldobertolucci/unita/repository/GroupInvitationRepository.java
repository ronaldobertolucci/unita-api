package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.group.GroupInvitation;
import io.github.ronaldobertolucci.unita.model.group.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    boolean existsByGroupIdAndInvitedUserIdAndStatus(
            Long groupId,
            Long invitedUserId,
            InvitationStatus status
    );

    @Query("""
            SELECT gi FROM GroupInvitation gi
            LEFT JOIN FETCH gi.group g
            LEFT JOIN FETCH g.responsibleUser
            LEFT JOIN FETCH gi.invitingUser
            WHERE gi.invitedUser.id = :userId 
            AND gi.status = 'PENDING'
            ORDER BY gi.invitedAt DESC
            """)
    List<GroupInvitation> findPendingInvitationsByInvitedUserId(@Param("userId") Long userId);

    @Query("""
            SELECT gi FROM GroupInvitation gi
            LEFT JOIN FETCH gi.invitedUser
            WHERE gi.group.id = :groupId
            AND gi.invitingUser.id = :invitingUserId
            ORDER BY gi.invitedAt DESC
            """)
    List<GroupInvitation> findByGroupIdAndInvitingUserId(
            @Param("groupId") Long groupId,
            @Param("invitingUserId") Long invitingUserId
    );

    @Query("""
            SELECT gi FROM GroupInvitation gi
            LEFT JOIN FETCH gi.invitedUser
            LEFT JOIN FETCH gi.invitingUser
            WHERE gi.group.id = :groupId
            ORDER BY gi.invitedAt DESC
            """)
    List<GroupInvitation> findByGroupIdWithUsers(@Param("groupId") Long groupId);

    @Query("""
            SELECT gi FROM GroupInvitation gi
            LEFT JOIN FETCH gi.group g
            LEFT JOIN FETCH g.responsibleUser
            LEFT JOIN FETCH gi.invitedUser
            LEFT JOIN FETCH gi.invitingUser
            WHERE gi.id = :id
            """)
    Optional<GroupInvitation> findByIdWithRelations(@Param("id") Long id);

    long countByInvitedUserIdAndStatus(Long invitedUserId, InvitationStatus status);
}