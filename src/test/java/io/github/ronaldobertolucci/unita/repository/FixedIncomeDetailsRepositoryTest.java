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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedIncomeDetailsRepositoryTest extends BaseRepositoryTest {

    @Autowired private FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;

    private User user;
    private LegalEntity legalEntity;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        legalEntity = saveLegalEntity();
    }

    @Test
    void findByAssetId_WhenExists_ShouldReturn() {
        Asset asset = saveAsset("CDB A");
        saveFixedIncomeDetails(asset, LocalDate.of(2027, 1, 1));

        Optional<FixedIncomeDetails> result = fixedIncomeDetailsRepository.findByAssetId(asset.getId());

        assertTrue(result.isPresent());
        assertEquals(Indexer.CDI, result.get().getIndexer());
    }

    @Test
    void findByAssetId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(fixedIncomeDetailsRepository.findByAssetId(999L).isEmpty());
    }

    @Test
    void findAllMaturedByDate_WhenMaturityDateIsBeforeOrEqual_ShouldReturn() {
        Asset asset = saveAsset("CDB Vencido");
        saveFixedIncomeDetails(asset, LocalDate.of(2025, 1, 1));

        List<FixedIncomeDetails> result = fixedIncomeDetailsRepository
                .findAllMaturedByDate(LocalDate.of(2025, 6, 1));

        assertEquals(1, result.size());
    }

    @Test
    void findAllMaturedByDate_WhenNotYetMatured_ShouldReturnEmpty() {
        Asset asset = saveAsset("CDB Futuro");
        saveFixedIncomeDetails(asset, LocalDate.of(2030, 1, 1));

        List<FixedIncomeDetails> result = fixedIncomeDetailsRepository
                .findAllMaturedByDate(LocalDate.of(2025, 6, 1));

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllMaturedByDate_WhenAlreadyMaturedStatus_ShouldNotReturn() {
        Asset asset = saveAsset("CDB Já Vencido");
        asset.setStatus(AssetStatus.MATURED);
        assetRepository.save(asset);
        saveFixedIncomeDetails(asset, LocalDate.of(2025, 1, 1));

        List<FixedIncomeDetails> result = fixedIncomeDetailsRepository
                .findAllMaturedByDate(LocalDate.of(2025, 6, 1));

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LegalEntity saveLegalEntity() {
        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        le.setUser(user);
        return legalEntityRepository.save(le);
    }

    private Asset saveAsset(String name) {
        Asset asset = Asset.builder()
                .user(user)
                .legalEntity(legalEntity)
                .name(name)
                .category(AssetCategory.RENDA_FIXA)
                .status(AssetStatus.ACTIVE)
                .build();
        return assetRepository.save(asset);
    }

    private FixedIncomeDetails saveFixedIncomeDetails(Asset asset, LocalDate maturityDate) {
        FixedIncomeDetails details = FixedIncomeDetails.builder()
                .asset(asset)
                .indexer(Indexer.CDI)
                .annualRate(new BigDecimal("0.12000000"))
                .maturityDate(maturityDate)
                .taxFree(false)
                .build();
        return fixedIncomeDetailsRepository.save(details);
    }
}