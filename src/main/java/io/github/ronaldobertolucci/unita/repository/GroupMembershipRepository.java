package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

    boolean existsByUserIdAndGroupId(Long userId, Long groupId);

    Optional<GroupMembership> findByUserIdAndGroupId(Long userId, Long groupId);

    @Query("""
            SELECT gm FROM GroupMembership gm
            LEFT JOIN FETCH gm.user
            WHERE gm.group.id = :groupId
            ORDER BY gm.joinedAt DESC
            """)
    List<GroupMembership> findByGroupIdWithUsers(@Param("groupId") Long groupId);

    @Query("""
            SELECT gm FROM GroupMembership gm
            LEFT JOIN FETCH gm.group g
            LEFT JOIN FETCH g.responsibleUser
            WHERE gm.user.id = :userId
            ORDER BY gm.joinedAt DESC
            """)
    List<GroupMembership> findByUserIdWithGroups(@Param("userId") Long userId);

    long countByGroupId(Long groupId);

    void deleteByUserIdAndGroupId(Long userId, Long groupId);

    @Query("""
        SELECT COUNT(gm1) > 0 FROM GroupMembership gm1
        WHERE gm1.user.id = :userId1
        AND EXISTS (
            SELECT gm2 FROM GroupMembership gm2
            WHERE gm2.user.id = :userId2
            AND gm2.group.id = gm1.group.id
        )
        """)
    boolean existsSharedGroup(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}