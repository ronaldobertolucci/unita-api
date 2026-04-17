package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupSharePermissionRepository extends JpaRepository<GroupSharePermission, Long> {

    List<GroupSharePermission> findAllByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupSharePermission> findByGroupIdAndUserIdAndShareType(Long groupId, Long userId, ShareType shareType);

    @Query("""
            SELECT gsp.user.id FROM GroupSharePermission gsp
            WHERE gsp.group.id = :groupId
            AND gsp.shareType = :shareType
            AND gsp.enabled = true
            """)
    List<Long> findEnabledUserIdsByGroupIdAndShareType(
            @Param("groupId") Long groupId,
            @Param("shareType") ShareType shareType);
}