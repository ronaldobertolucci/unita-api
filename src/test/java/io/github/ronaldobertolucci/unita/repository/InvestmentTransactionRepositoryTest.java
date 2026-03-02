package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InvestmentTransactionRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private InvestmentTransactionRepository investmentTransactionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;

    private User user;
    private Asset asset;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        le.setUser(user);
        legalEntityRepository.save(le);

        asset = Asset.builder()
                .user(user)
                .legalEntity(le)
                .name("CDB A")
                .category(AssetCategory.RENDA_FIXA)
                .status(AssetStatus.ACTIVE)
                .build();
        assetRepository.save(asset);
    }

    @Test
    void findAllByAssetIdOrderByTransactionDateDesc_ShouldReturnInOrder() {
        saveInvestmentTransaction(InvestmentTransactionType.BUY,
                new BigDecimal("1000.00"), LocalDate.of(2025, 1, 1));
        saveInvestmentTransaction(InvestmentTransactionType.YIELD,
                new BigDecimal("50.00"), LocalDate.of(2025, 3, 1));

        List<InvestmentTransaction> result = investmentTransactionRepository
                .findAllByAssetIdOrderByTransactionDateDesc(asset.getId());

        assertEquals(2, result.size());
        assertTrue(result.get(0).getTransactionDate()
                .isAfter(result.get(1).getTransactionDate()));
    }

    @Test
    void findAllByAssetIdOrderByTransactionDateDesc_WhenEmpty_ShouldReturnEmptyList() {
        assertTrue(investmentTransactionRepository
                .findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).isEmpty());
    }

    @Test
    void findByIdAndAssetId_WhenExists_ShouldReturn() {
        InvestmentTransaction saved = saveInvestmentTransaction(
                InvestmentTransactionType.BUY, new BigDecimal("1000.00"), LocalDate.now());

        Optional<InvestmentTransaction> result = investmentTransactionRepository
                .findByIdAndAssetId(saved.getId(), asset.getId());

        assertTrue(result.isPresent());
    }

    @Test
    void findByIdAndAssetId_WhenWrongAsset_ShouldReturnEmpty() {
        InvestmentTransaction saved = saveInvestmentTransaction(
                InvestmentTransactionType.BUY, new BigDecimal("1000.00"), LocalDate.now());

        Optional<InvestmentTransaction> result = investmentTransactionRepository
                .findByIdAndAssetId(saved.getId(), 999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByAssetId_WhenExists_ShouldReturnTrue() {
        saveInvestmentTransaction(InvestmentTransactionType.BUY,
                new BigDecimal("1000.00"), LocalDate.now());

        assertTrue(investmentTransactionRepository.existsByAssetId(asset.getId()));
    }

    @Test
    void existsByAssetId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(investmentTransactionRepository.existsByAssetId(asset.getId()));
    }

    @Test
    void findFirstByAssetIdAndTypeOrderByTransactionDateAsc_WhenMultipleBuys_ShouldReturnEarliest() {
        saveInvestmentTransaction(InvestmentTransactionType.BUY,
                new BigDecimal("500.00"), LocalDate.of(2025, 3, 1));
        saveInvestmentTransaction(InvestmentTransactionType.BUY,
                new BigDecimal("1000.00"), LocalDate.of(2025, 1, 1));

        Optional<InvestmentTransaction> result = investmentTransactionRepository
                .findFirstByAssetIdAndTypeOrderByTransactionDateAsc(
                        asset.getId(), InvestmentTransactionType.BUY);

        assertTrue(result.isPresent());
        assertEquals(LocalDate.of(2025, 1, 1), result.get().getTransactionDate());
    }

    @Test
    void findFirstByAssetIdAndTypeOrderByTransactionDateAsc_WhenNoBuy_ShouldReturnEmpty() {
        Optional<InvestmentTransaction> result = investmentTransactionRepository
                .findFirstByAssetIdAndTypeOrderByTransactionDateAsc(
                        asset.getId(), InvestmentTransactionType.BUY);

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InvestmentTransaction saveInvestmentTransaction(
            InvestmentTransactionType type, BigDecimal amount, LocalDate date) {
        InvestmentTransaction tx = InvestmentTransaction.builder()
                .asset(asset)
                .type(type)
                .amount(amount)
                .transactionDate(date)
                .build();
        return investmentTransactionRepository.save(tx);
    }
}