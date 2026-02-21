package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PocketRepository extends JpaRepository<Pocket, Long> {

    @Query("""
            SELECT p FROM Pocket p
            WHERE p.user.id = :userId
            """)
    List<Pocket> findAllByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT p FROM Pocket p
            WHERE p.id = :id
            AND p.user.id = :userId
            """)
    Optional<Pocket> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}