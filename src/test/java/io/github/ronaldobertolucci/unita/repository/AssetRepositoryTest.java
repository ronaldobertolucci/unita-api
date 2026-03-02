package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.Asset;
import io.github.ronaldobertolucci.unita.model.investment.AssetCategory;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AssetRepositoryTest extends BaseRepositoryTest {

    @Autowired private AssetRepository assetRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired
    private FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    @Autowired private InvestmentPositionRepository investmentPositionRepository;

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

        List<Asset> result = assetRepository.findAllByUserId(user.getId());

        assertEquals(2, result.size());
    }

    @Test
    void findAllByUserId_WhenEmpty_ShouldReturnEmptyList() {
        assertTrue(assetRepository.findAllByUserId(user.getId()).isEmpty());
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
}