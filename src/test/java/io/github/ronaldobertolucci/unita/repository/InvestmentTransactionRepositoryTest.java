package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvestmentTransactionRepositoryTest extends BaseRepositoryTest {

    @Autowired private InvestmentTransactionRepository investmentTransactionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private InvestmentPositionRepository investmentPositionRepository;
    @Autowired private TestEntityManager entityManager;

    private User user;
    private User otherUser;
    private LegalEntity legalEntity;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");
        legalEntity = saveLegalEntity(user);
    }

    // -------------------------------------------------------------------------
    // sumTaxByUserIdAndRedeemedAssets
    // -------------------------------------------------------------------------

    @Test
    void sumTaxByUserIdAndRedeemedAssets_ShouldSumOnlyTaxTransactions() {
        Asset asset = saveRedeemedAsset("CDB A", user);
        saveInvestmentTransaction(asset, InvestmentTransactionType.TAX, new BigDecimal("100.00"));
        saveInvestmentTransaction(asset, InvestmentTransactionType.TAX, new BigDecimal("50.00"));
        saveInvestmentTransaction(asset, InvestmentTransactionType.BUY, new BigDecimal("1000.00"));

        BigDecimal result = investmentTransactionRepository.sumTaxByUserIdAndRedeemedAssets(user.getId());

        assertEquals(0, new BigDecimal("150.00").compareTo(result));
    }

    @Test
    void sumTaxByUserIdAndRedeemedAssets_ShouldExcludeNonRedeemedAssets() {
        Asset redeemed = saveRedeemedAsset("CDB A", user);
        Asset active = saveAssetWithStatus("CDB B", user, AssetStatus.ACTIVE);
        Asset matured = saveAssetWithStatus("CDB C", user, AssetStatus.MATURED);

        saveInvestmentTransaction(redeemed, InvestmentTransactionType.TAX, new BigDecimal("100.00"));
        saveInvestmentTransaction(active, InvestmentTransactionType.TAX, new BigDecimal("200.00"));
        saveInvestmentTransaction(matured, InvestmentTransactionType.TAX, new BigDecimal("300.00"));

        BigDecimal result = investmentTransactionRepository.sumTaxByUserIdAndRedeemedAssets(user.getId());

        assertEquals(0, new BigDecimal("100.00").compareTo(result));
    }

    @Test
    void sumTaxByUserIdAndRedeemedAssets_ShouldNotReturnOtherUsersTransactions() {
        Asset userAsset = saveRedeemedAsset("CDB A", user);
        Asset otherAsset = saveRedeemedAsset("CDB B", otherUser);

        saveInvestmentTransaction(userAsset, InvestmentTransactionType.TAX, new BigDecimal("100.00"));
        saveInvestmentTransaction(otherAsset, InvestmentTransactionType.TAX, new BigDecimal("999.00"));

        BigDecimal result = investmentTransactionRepository.sumTaxByUserIdAndRedeemedAssets(user.getId());

        assertEquals(0, new BigDecimal("100.00").compareTo(result));
    }

    @Test
    void sumTaxByUserIdAndRedeemedAssets_WhenNoTaxTransactions_ShouldReturnZero() {
        Asset asset = saveRedeemedAsset("CDB A", user);
        saveInvestmentTransaction(asset, InvestmentTransactionType.BUY, new BigDecimal("1000.00"));

        BigDecimal result = investmentTransactionRepository.sumTaxByUserIdAndRedeemedAssets(user.getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void sumTaxByUserIdAndRedeemedAssets_WhenNoRedeemedAssets_ShouldReturnZero() {
        BigDecimal result = investmentTransactionRepository.sumTaxByUserIdAndRedeemedAssets(user.getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LegalEntity saveLegalEntity(User user) {
        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        le.setUser(user);
        return legalEntityRepository.save(le);
    }

    private Asset saveRedeemedAsset(String name, User owner) {
        return saveAssetWithStatus(name, owner, AssetStatus.REDEEMED);
    }

    private Asset saveAssetWithStatus(String name, User owner, AssetStatus status) {
        Asset asset = Asset.builder()
                .user(owner)
                .legalEntity(legalEntity)
                .name(name)
                .category(AssetCategory.RENDA_FIXA)
                .status(status)
                .build();
        return assetRepository.save(asset);
    }

    private void saveInvestmentTransaction(Asset asset, InvestmentTransactionType type, BigDecimal amount) {
        InvestmentTransaction transaction = InvestmentTransaction.builder()
                .asset(asset)
                .type(type)
                .amount(amount)
                .transactionDate(LocalDate.now())
                .build();
        investmentTransactionRepository.save(transaction);
    }
}