package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.dto.dashboard.IndexerSummaryDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.IssuerRiskSummaryDto;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AssetRepositoryTest extends BaseRepositoryTest {

    @Autowired private AssetRepository assetRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
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

    @Test
    void findAllByUserId_ShouldReturnOnlyUserAssets() {
        saveAsset("CDB A", user);
        saveAsset("CDB B", user);
        saveAsset("CDB C", otherUser);

        List<Asset> result = assetRepository.findAllByUserIdOrderByName(user.getId());

        assertEquals(2, result.size());
    }

    @Test
    void findAllByUserId_WhenEmpty_ShouldReturnEmptyList() {
        assertTrue(assetRepository.findAllByUserIdOrderByName(user.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserId_WhenOwned_ShouldReturn() {
        Asset saved = saveAsset("CDB A", user);

        Optional<Asset> result = assetRepository.findByIdAndUserId(saved.getId(), user.getId());

        assertTrue(result.isPresent());
    }

    @Test
    void findByIdAndUserId_WhenNotOwned_ShouldReturnEmpty() {
        Asset saved = saveAsset("CDB A", user);

        Optional<Asset> result = assetRepository.findByIdAndUserId(saved.getId(), otherUser.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByIdAndUserId_WhenOwned_ShouldReturnTrue() {
        Asset saved = saveAsset("CDB A", user);

        assertTrue(assetRepository.existsByIdAndUserId(saved.getId(), user.getId()));
    }

    @Test
    void existsByIdAndUserId_WhenNotOwned_ShouldReturnFalse() {
        Asset saved = saveAsset("CDB A", user);

        assertFalse(assetRepository.existsByIdAndUserId(saved.getId(), otherUser.getId()));
    }

    @Test
    void existsByNameAndUserId_WhenExists_ShouldReturnTrue() {
        saveAsset("CDB A", user);

        assertTrue(assetRepository.existsByNameAndUserId("CDB A", user.getId()));
    }

    @Test
    void existsByNameAndUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(assetRepository.existsByNameAndUserId("Inexistente", user.getId()));
    }

    @Test
    void existsByNameAndUserId_WhenSameNameDifferentUser_ShouldReturnFalse() {
        saveAsset("CDB A", user);

        assertFalse(assetRepository.existsByNameAndUserId("CDB A", otherUser.getId()));
    }

    @Test
    void existsActiveByIdAndUserId_WhenActive_ShouldReturnTrue() {
        Asset saved = saveAsset("CDB A", user);

        assertTrue(assetRepository.existsActiveByIdAndUserId(saved.getId(), user.getId()));
    }

    @Test
    void existsActiveByIdAndUserId_WhenMatured_ShouldReturnFalse() {
        Asset saved = saveAsset("CDB A", user);
        saved.setStatus(AssetStatus.MATURED);
        assetRepository.save(saved);

        assertFalse(assetRepository.existsActiveByIdAndUserId(saved.getId(), user.getId()));
    }

    @Test
    void findAllByUserIdWithDetails_ShouldReturnAssetsWithFetchedAssociations() {
        Asset asset = saveAsset("CDB A", user);

        InvestmentPosition position = InvestmentPosition.builder()
                .asset(asset)
                .totalInvested(new BigDecimal("1000.00"))
                .currentValue(new BigDecimal("1050.00"))
                .redeemedValue(BigDecimal.ZERO)
                .build();
        investmentPositionRepository.save(position);

        FixedIncomeDetails details = FixedIncomeDetails.builder()
                .asset(asset)
                .indexer(Indexer.CDI)
                .annualRate(new BigDecimal("0.10000000"))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .taxFree(false)
                .build();
        fixedIncomeDetailsRepository.save(details);

        entityManager.flush();
        entityManager.clear();

        List<Asset> result = assetRepository.findAllByUserIdWithDetails(user.getId());

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getPosition());
        assertNotNull(result.get(0).getFixedIncomeDetails());
        assertNotNull(result.get(0).getLegalEntity());
    }

    @Test
    void findAllByUserIdWithDetails_WhenNoAssets_ShouldReturnEmpty() {
        assertTrue(assetRepository.findAllByUserIdWithDetails(user.getId()).isEmpty());
    }

    @Test
    void findAllByUserIdWithDetails_ShouldNotReturnOtherUsersAssets() {
        saveAsset("CDB A", user);
        saveAsset("CDB B", otherUser);

        List<Asset> result = assetRepository.findAllByUserIdWithDetails(user.getId());

        assertEquals(1, result.size());
        assertEquals(user.getId(), result.get(0).getUser().getId());
    }

    // -------------------------------------------------------------------------
    // sumCurrentValueByLegalEntityAndUserId
    // -------------------------------------------------------------------------

    @Test
    void sumCurrentValueByLegalEntityAndUserId_ShouldAggregateByLegalEntity() {
        LegalEntity otherLegalEntity = saveLegalEntityWithName("Corretora XP");

        saveAssetWithPosition("CDB A", user, legalEntity, new BigDecimal("1000.00"), AssetStatus.ACTIVE);
        saveAssetWithPosition("CDB B", user, legalEntity, new BigDecimal("500.00"), AssetStatus.ACTIVE);
        saveAssetWithPosition("LCI A", user, otherLegalEntity, new BigDecimal("2000.00"), AssetStatus.ACTIVE);

        List<IssuerRiskSummaryDto> result = assetRepository.sumCurrentValueByLegalEntityAndUserId(user.getId());

        assertEquals(2, result.size());
        IssuerRiskSummaryDto bancoTeste = result.stream()
                .filter(r -> "Banco Teste".equals(r.legalEntityName())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1500.00").compareTo(bancoTeste.totalCurrentValue()));
    }

    @Test
    void sumCurrentValueByLegalEntityAndUserId_ShouldExcludeRedeemedAssets() {
        saveAssetWithPosition("CDB A", user, legalEntity, new BigDecimal("1000.00"), AssetStatus.ACTIVE);
        saveAssetWithPosition("CDB B", user, legalEntity, new BigDecimal("500.00"), AssetStatus.REDEEMED);
        saveAssetWithPosition("CDB C", user, legalEntity, new BigDecimal("300.00"), AssetStatus.MATURED);

        List<IssuerRiskSummaryDto> result = assetRepository.sumCurrentValueByLegalEntityAndUserId(user.getId());

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("1300.00").compareTo(result.get(0).totalCurrentValue()));
    }

    @Test
    void sumCurrentValueByLegalEntityAndUserId_ShouldNotReturnOtherUsersAssets() {
        saveAssetWithPosition("CDB A", user, legalEntity, new BigDecimal("1000.00"), AssetStatus.ACTIVE);
        saveAssetWithPosition("CDB B", otherUser, legalEntity, new BigDecimal("999.00"), AssetStatus.ACTIVE);

        List<IssuerRiskSummaryDto> result = assetRepository.sumCurrentValueByLegalEntityAndUserId(user.getId());

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.get(0).totalCurrentValue()));
    }

    @Test
    void sumCurrentValueByLegalEntityAndUserId_WhenNoAssets_ShouldReturnEmptyList() {
        assertTrue(assetRepository.sumCurrentValueByLegalEntityAndUserId(user.getId()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // sumCurrentValueByIndexerAndUserId
    // -------------------------------------------------------------------------

    @Test
    void sumCurrentValueByIndexerAndUserId_ShouldAggregateByIndexer() {
        Asset assetA = saveAssetWithPosition("CDB A", user, legalEntity, new BigDecimal("1000.00"), AssetStatus.ACTIVE);
        Asset assetB = saveAssetWithPosition("CDB B", user, legalEntity, new BigDecimal("500.00"), AssetStatus.ACTIVE);
        Asset assetC = saveAssetWithPosition("LCI A", user, legalEntity, new BigDecimal("2000.00"), AssetStatus.ACTIVE);

        saveFixedIncomeDetails(assetA, Indexer.CDI);
        saveFixedIncomeDetails(assetB, Indexer.CDI);
        saveFixedIncomeDetails(assetC, Indexer.IPCA);

        List<IndexerSummaryDto> result = assetRepository.sumCurrentValueByIndexerAndUserId(user.getId());

        assertEquals(2, result.size());
        IndexerSummaryDto cdi = result.stream()
                .filter(r -> Indexer.CDI.equals(r.indexer())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1500.00").compareTo(cdi.totalCurrentValue()));
    }

    @Test
    void sumCurrentValueByIndexerAndUserId_ShouldExcludeRedeemedAssets() {
        Asset assetA = saveAssetWithPosition("CDB A", user, legalEntity, new BigDecimal("1000.00"), AssetStatus.ACTIVE);
        Asset assetB = saveAssetWithPosition("CDB B", user, legalEntity, new BigDecimal("500.00"), AssetStatus.REDEEMED);

        saveFixedIncomeDetails(assetA, Indexer.CDI);
        saveFixedIncomeDetails(assetB, Indexer.CDI);

        List<IndexerSummaryDto> result = assetRepository.sumCurrentValueByIndexerAndUserId(user.getId());

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.get(0).totalCurrentValue()));
    }

    @Test
    void sumCurrentValueByIndexerAndUserId_ShouldNotReturnOtherUsersAssets() {
        Asset assetA = saveAssetWithPosition("CDB A", user, legalEntity, new BigDecimal("1000.00"), AssetStatus.ACTIVE);
        Asset assetB = saveAssetWithPosition("CDB B", otherUser, legalEntity, new BigDecimal("999.00"), AssetStatus.ACTIVE);

        saveFixedIncomeDetails(assetA, Indexer.CDI);
        saveFixedIncomeDetails(assetB, Indexer.CDI);

        List<IndexerSummaryDto> result = assetRepository.sumCurrentValueByIndexerAndUserId(user.getId());

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.get(0).totalCurrentValue()));
    }

    @Test
    void sumCurrentValueByIndexerAndUserId_WhenNoFixedIncomeAssets_ShouldReturnEmptyList() {
        saveAssetWithPosition("Ação PETR4", user, legalEntity, new BigDecimal("1000.00"), AssetStatus.ACTIVE);

        assertTrue(assetRepository.sumCurrentValueByIndexerAndUserId(user.getId()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // sumGrossProfitByUserId
    // -------------------------------------------------------------------------

    @Test
    void sumGrossProfitByUserId_ShouldSumRedeemedValueMinusTotalInvested() {
        saveAssetWithPositionAndInvested("CDB A", user, legalEntity,
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), AssetStatus.REDEEMED);
        saveAssetWithPositionAndInvested("CDB B", user, legalEntity,
                new BigDecimal("600.00"), new BigDecimal("500.00"), AssetStatus.REDEEMED);

        BigDecimal result = assetRepository.sumGrossProfitByUserId(user.getId());

        assertEquals(0, new BigDecimal("300.00").compareTo(result));
    }

    @Test
    void sumGrossProfitByUserId_ShouldExcludeNonRedeemedAssets() {
        saveAssetWithPositionAndInvested("CDB A", user, legalEntity,
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), AssetStatus.REDEEMED);
        saveAssetWithPositionAndInvested("CDB B", user, legalEntity,
                new BigDecimal("600.00"), new BigDecimal("500.00"), AssetStatus.ACTIVE);
        saveAssetWithPositionAndInvested("CDB C", user, legalEntity,
                new BigDecimal("800.00"), new BigDecimal("700.00"), AssetStatus.MATURED);

        BigDecimal result = assetRepository.sumGrossProfitByUserId(user.getId());

        assertEquals(0, new BigDecimal("200.00").compareTo(result));
    }

    @Test
    void sumGrossProfitByUserId_ShouldNotReturnOtherUsersAssets() {
        saveAssetWithPositionAndInvested("CDB A", user, legalEntity,
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), AssetStatus.REDEEMED);
        saveAssetWithPositionAndInvested("CDB B", otherUser, legalEntity,
                new BigDecimal("600.00"), new BigDecimal("500.00"), AssetStatus.REDEEMED);

        BigDecimal result = assetRepository.sumGrossProfitByUserId(user.getId());

        assertEquals(0, new BigDecimal("200.00").compareTo(result));
    }

    @Test
    void sumGrossProfitByUserId_WhenNoRedeemedAssets_ShouldReturnZero() {
        BigDecimal result = assetRepository.sumGrossProfitByUserId(user.getId());

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

    private Asset saveAsset(String name, User user) {
        Asset asset = Asset.builder()
                .user(user)
                .legalEntity(legalEntity)
                .name(name)
                .category(AssetCategory.RENDA_FIXA)
                .status(AssetStatus.ACTIVE)
                .build();
        return assetRepository.save(asset);
    }

    private LegalEntity saveLegalEntityWithName(String name) {
        LegalEntity le = new LegalEntity();
        le.setCnpj(String.format("%014d", Math.abs(System.nanoTime() % 100_000_000_000_000L)));
        le.setCorporateName(name);
        le.setUser(user);
        return legalEntityRepository.save(le);
    }

    private Asset saveAssetWithPosition(String name, User owner, LegalEntity le,
                                        BigDecimal currentValue, AssetStatus status) {
        Asset asset = Asset.builder()
                .user(owner)
                .legalEntity(le)
                .name(name)
                .category(AssetCategory.RENDA_FIXA)
                .status(status)
                .build();
        assetRepository.save(asset);

        InvestmentPosition position = InvestmentPosition.builder()
                .asset(asset)
                .currentValue(currentValue)
                .totalInvested(BigDecimal.ZERO)
                .redeemedValue(BigDecimal.ZERO)
                .build();
        investmentPositionRepository.save(position);

        entityManager.flush();
        entityManager.clear();

        return assetRepository.findById(asset.getId()).orElseThrow();
    }

    private void saveFixedIncomeDetails(Asset asset, Indexer indexer) {
        FixedIncomeDetails details = FixedIncomeDetails.builder()
                .asset(asset)
                .indexer(indexer)
                .annualRate(new BigDecimal("0.10000000"))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .taxFree(false)
                .build();
        fixedIncomeDetailsRepository.save(details);
    }

    private Asset saveAssetWithPositionAndInvested(String name, User owner, LegalEntity le,
                                                   BigDecimal redeemedValue, BigDecimal totalInvested,
                                                   AssetStatus status) {
        Asset asset = Asset.builder()
                .user(owner)
                .legalEntity(le)
                .name(name)
                .category(AssetCategory.RENDA_FIXA)
                .status(status)
                .build();
        assetRepository.save(asset);

        InvestmentPosition position = InvestmentPosition.builder()
                .asset(asset)
                .currentValue(BigDecimal.ZERO)
                .totalInvested(totalInvested)
                .redeemedValue(redeemedValue)
                .build();
        investmentPositionRepository.save(position);

        entityManager.flush();
        entityManager.clear();

        return assetRepository.findById(asset.getId()).orElseThrow();
    }
}