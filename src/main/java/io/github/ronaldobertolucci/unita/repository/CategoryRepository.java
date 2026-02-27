package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
            SELECT c FROM Category c
            WHERE c.user.id = :userId OR c.user IS NULL
            ORDER BY c.type ASC, c.name ASC
            """)
    List<Category> findAllAvailableForUser(@Param("userId") Long userId);

    @Query("""
            SELECT c FROM Category c
            WHERE c.name = :name
            AND c.type = :type
            AND c.user IS NULL
            """)
    Optional<Category> findGlobalByNameAndType(@Param("name") String name,
            @Param("type") CategoryType type);

    @Query("""
            SELECT c FROM Category c
            WHERE c.name = :name
            AND c.type = :type
            AND c.user.id = :userId
            """)
    Optional<Category> findPersonalByNameAndTypeAndUserId(@Param("name") String name,
                                                          @Param("type") CategoryType type,
                                                          @Param("userId") Long userId);

    @Query("""
            SELECT c FROM Category c
            WHERE c.system = true
            AND c.name = :name
            """)
    Optional<Category> findSystemByName(@Param("name") String name);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT COUNT(t) > 0 FROM Transaction t
            WHERE t.category.id = :categoryId
            """)
    boolean existsTransactionByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
            SELECT COUNT(i) > 0 FROM CreditCardInstallment i
            WHERE i.category.id = :categoryId
            """)
    boolean existsInstallmentByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
            SELECT COUNT(r) > 0 FROM CreditCardRefund r
            WHERE r.category.id = :categoryId
            """)
    boolean existsRefundByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
            SELECT COUNT(rt) > 0 FROM RecurringTransaction rt
            WHERE rt.category.id = :categoryId
            """)
    boolean existsRecurringTransactionByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
            SELECT COUNT(rp) > 0 FROM RecurringPurchase rp
            WHERE rp.category.id = :categoryId
            """)
    boolean existsRecurringPurchaseByCategoryId(@Param("categoryId") Long categoryId);
}