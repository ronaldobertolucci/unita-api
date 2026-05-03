package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    boolean existsByNameAndResponsibleUserId(String name, Long responsibleUserId);

    @Query("SELECT g FROM Group g WHERE g.responsibleUser.id = :userId")
    List<Group> findByResponsibleUserId(@Param("userId") Long userId);

    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.responsibleUser WHERE g.id = :id")
    Optional<Group> findByIdWithResponsible(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT g FROM Group g
            INNER JOIN GroupMembership gm ON gm.group.id = g.id
            WHERE gm.user.id = :userId
            ORDER BY g.name
            """)
    List<Group> findGroupsByMemberUserId(@Param("userId") Long userId);
}