package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.*;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    @Mock private PensionDetailsRepository pensionDetailsRepository;
    @Mock private InvestmentPositionRepository investmentPositionRepository;
    @Mock
    private InvestmentTransactionRepository investmentTransactionRepository;
    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private TaxCalculationService taxCalculationService;
    @Mock private Authentication authentication;

    @InjectMocks
    private AssetService assetService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // createFixedIncome
    // -------------------------------------------------------------------------

    @Test
    void createFixedIncome_WhenValid_ShouldPersistAndReturnDto() {
        FixedIncomeAssetCreateDto dto = new FixedIncomeAssetCreateDto(
                "CDB Banco X", 10L, Indexer.CDI,
                new BigDecimal("0.12000000"), LocalDate.of(2027, 1, 1), false);

        LegalEntity le = buildLegalEntity(10L);
        Asset savedAsset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        FixedIncomeDetails savedDetails = buildFixedIncomeDetails(savedAsset, Indexer.CDI, false);
        InvestmentPosition savedPosition = buildPosition(savedAsset);

        when(assetRepository.existsByNameAndUserId("CDB Banco X", currentUser.getId())).thenReturn(false);
        when(legalEntityRepository.findByIdAndUserId(10L, currentUser.getId())).thenReturn(Optional.of(le));
        when(assetRepository.save(any())).thenReturn(savedAsset);
        when(fixedIncomeDetailsRepository.save(any())).thenReturn(savedDetails);
        when(investmentPositionRepository.save(any())).thenReturn(savedPosition);
        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(savedAsset));

        AssetDetailDto result = assetService.createFixedIncome(dto, authentication);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(assetRepository).save(any(Asset.class));
        verify(fixedIncomeDetailsRepository).save(any(FixedIncomeDetails.class));
        verify(investmentPositionRepository).save(any(InvestmentPosition.class));
    }

    @Test
    void createFixedIncome_WhenNameAlreadyExists_ShouldThrowIllegalArgumentException() {
        FixedIncomeAssetCreateDto dto = new FixedIncomeAssetCreateDto(
                "CDB Banco X", 10L, Indexer.CDI,
                new BigDecimal("0.12000000"), LocalDate.of(2027, 1, 1), false);

        when(assetRepository.existsByNameAndUserId("CDB Banco X", currentUser.getId())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> assetService.createFixedIncome(dto, authentication));
        verify(assetRepository, never()).save(any());
    }

    @Test
    void createFixedIncome_WhenLegalEntityNotFound_ShouldThrow() {
        FixedIncomeAssetCreateDto dto = new FixedIncomeAssetCreateDto(
                "CDB Banco X", 99L, Indexer.CDI,
                new BigDecimal("0.12000000"), LocalDate.of(2027, 1, 1), false);

        when(assetRepository.existsByNameAndUserId("CDB Banco X", currentUser.getId())).thenReturn(false);
        when(legalEntityRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> assetService.createFixedIncome(dto, authentication));
        verify(assetRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // createPension
    // -------------------------------------------------------------------------

    @Test
    void createPension_WhenValid_ShouldPersistAndReturnDto() {
        PensionAssetCreateDto dto = new PensionAssetCreateDto(
                "PGBL Banco X", 10L, PensionType.PGBL, TaxRegime.REGRESSIVO);

        LegalEntity le = buildLegalEntity(10L);
        Asset savedAsset = buildAsset(1L, AssetCategory.PREVIDENCIA, le);
        PensionDetails savedDetails = buildPensionDetails(savedAsset, PensionType.PGBL, TaxRegime.REGRESSIVO);
        InvestmentPosition savedPosition = buildPosition(savedAsset);

        when(assetRepository.existsByNameAndUserId("PGBL Banco X", currentUser.getId())).thenReturn(false);
        when(legalEntityRepository.findByIdAndUserId(10L, currentUser.getId())).thenReturn(Optional.of(le));
        when(assetRepository.save(any())).thenReturn(savedAsset);
        when(pensionDetailsRepository.save(any())).thenReturn(savedDetails);
        when(investmentPositionRepository.save(any())).thenReturn(savedPosition);
        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(savedAsset));

        AssetDetailDto result = assetService.createPension(dto, authentication);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(pensionDetailsRepository).save(any(PensionDetails.class));
    }

    @Test
    void createPension_WhenNameAlreadyExists_ShouldThrowIllegalArgumentException() {
        PensionAssetCreateDto dto = new PensionAssetCreateDto(
                "PGBL Banco X", 10L, PensionType.PGBL, TaxRegime.REGRESSIVO);

        when(assetRepository.existsByNameAndUserId("PGBL Banco X", currentUser.getId())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> assetService.createPension(dto, authentication));
        verify(assetRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_ShouldReturnOnlyUserAssets() {
        LegalEntity le = buildLegalEntity(10L);
        Asset a1 = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        Asset a2 = buildAsset(2L, AssetCategory.PREVIDENCIA, le);
        when(assetRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of(a1, a2));

        List<AssetSummaryDto> result = assetService.findAll(authentication);

        assertEquals(2, result.size());
        verify(assetRepository).findAllByUserId(currentUser.getId());
    }

    @Test
    void findAll_WhenEmpty_ShouldReturnEmptyList() {
        when(assetRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());

        assertTrue(assetService.findAll(authentication).isEmpty());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_WhenOwned_ShouldReturnDetailDto() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));

        AssetDetailDto result = assetService.findById(1L, authentication);

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void findById_WhenNotOwned_ShouldThrow() {
        when(assetRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> assetService.findById(99L, authentication));
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_WhenValid_ShouldUpdateAndReturnDto() {
        LegalEntity le = buildLegalEntity(10L);
        LegalEntity newLe = buildLegalEntity(20L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        AssetUpdateDto dto = new AssetUpdateDto("CDB Atualizado", 20L);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(legalEntityRepository.findByIdAndUserId(20L, currentUser.getId())).thenReturn(Optional.of(newLe));
        when(assetRepository.save(asset)).thenReturn(asset);

        AssetDetailDto result = assetService.update(1L, dto, authentication);

        assertNotNull(result);
        assertEquals("CDB Atualizado", asset.getName());
        assertEquals(newLe, asset.getLegalEntity());
        verify(assetRepository).save(asset);
    }

    @Test
    void update_WhenNameChangedAndAlreadyExists_ShouldThrowIllegalArgumentException() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        AssetUpdateDto dto = new AssetUpdateDto("CDB Duplicado", 10L);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(assetRepository.existsByNameAndUserId("CDB Duplicado", currentUser.getId())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> assetService.update(1L, dto, authentication));
        verify(assetRepository, never()).save(any());
    }

    @Test
    void update_WhenNotFound_ShouldThrow() {
        when(assetRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> assetService.update(99L, new AssetUpdateDto("Nome", 10L), authentication));
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_WhenNoTransactions_ShouldDelete() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentTransactionRepository.existsByAssetId(1L)).thenReturn(false);

        assetService.delete(1L, authentication);

        verify(assetRepository).deleteById(1L);
    }

    @Test
    void delete_WhenHasTransactions_ShouldThrowIllegalStateException() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentTransactionRepository.existsByAssetId(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> assetService.delete(1L, authentication));
        verify(assetRepository, never()).deleteById(any());
    }

    @Test
    void delete_WhenNotFound_ShouldThrow() {
        when(assetRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> assetService.delete(99L, authentication));
        verify(assetRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // getTaxSuggestion — Renda Fixa
    // -------------------------------------------------------------------------

    @Test
    void getTaxSuggestion_WhenFixedIncomeNotTaxFree_ShouldReturnSuggestionOnEarnings() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        FixedIncomeDetails details = buildFixedIncomeDetails(asset, Indexer.CDI, false);
        InvestmentPosition position = buildPosition(asset);
        position.setTotalInvested(new BigDecimal("10000.00000000"));

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(fixedIncomeDetailsRepository.findByAssetId(1L)).thenReturn(Optional.of(details));
        when(taxCalculationService.calculateFixedIncomeTaxRate(any(), any()))
                .thenReturn(new BigDecimal("0.150"));
        when(taxCalculationService.describeTaxBasis(anyLong(), eq("REGRESSIVO")))
                .thenReturn("Tabela regressiva IR - acima de 720 dias (15,0%)");
        when(taxCalculationService.calculateTaxAmount(any(), any()))
                .thenReturn(new BigDecimal("300.00000000")); // 15% sobre rendimento de 2.000

        TaxSuggestionDto result = assetService.getTaxSuggestion(
                1L, new BigDecimal("12000.00"), LocalDate.of(2023, 1, 1), authentication);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.150").compareTo(result.suggestedTaxRate()));
        assertEquals(0, new BigDecimal("10000.00000000").compareTo(result.totalInvested()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.earnings()));
    }

    @Test
    void getTaxSuggestion_WhenFixedIncomeTaxFree_ShouldReturnZeroTax() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        FixedIncomeDetails details = buildFixedIncomeDetails(asset, Indexer.CDI, true);
        InvestmentPosition position = buildPosition(asset);
        position.setTotalInvested(new BigDecimal("10000.00000000"));

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(fixedIncomeDetailsRepository.findByAssetId(1L)).thenReturn(Optional.of(details));

        TaxSuggestionDto result = assetService.getTaxSuggestion(
                1L, new BigDecimal("12000.00"), LocalDate.of(2023, 1, 1), authentication);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.suggestedTaxRate()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.suggestedTaxAmount()));
        assertEquals(0, new BigDecimal("12000.00").compareTo(result.suggestedNetAmount()));
    }

    @Test
    void getTaxSuggestion_WhenPensionVGBLRegressivo_ShouldCalculateOnEarnings() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.PREVIDENCIA, le);
        PensionDetails details = buildPensionDetails(asset, PensionType.VGBL, TaxRegime.REGRESSIVO);
        InvestmentPosition position = buildPosition(asset);
        position.setTotalInvested(new BigDecimal("10000.00000000"));

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(pensionDetailsRepository.findByAssetId(1L)).thenReturn(Optional.of(details));
        when(taxCalculationService.calculatePensionRegressiveTaxRate(any(), any()))
                .thenReturn(new BigDecimal("0.150"));
        when(taxCalculationService.describeTaxBasis(anyLong(), eq("REGRESSIVO")))
                .thenReturn("Tabela regressiva IR - acima de 720 dias (15,0%)");
        when(taxCalculationService.calculateTaxAmount(any(), any()))
                .thenReturn(new BigDecimal("300.00000000"));

        TaxSuggestionDto result = assetService.getTaxSuggestion(
                1L, new BigDecimal("12000.00"), LocalDate.of(2023, 1, 1), authentication);

        assertNotNull(result);
        // VGBL: base = rendimento — verifica via earnings no resultado
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.earnings()));
    }

    @Test
    void getTaxSuggestion_WhenPensionPGBLRegressivo_ShouldCalculateOnTotal() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.PREVIDENCIA, le);
        PensionDetails details = buildPensionDetails(asset, PensionType.PGBL, TaxRegime.REGRESSIVO);
        InvestmentPosition position = buildPosition(asset);
        position.setTotalInvested(new BigDecimal("10000.00000000"));

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(pensionDetailsRepository.findByAssetId(1L)).thenReturn(Optional.of(details));
        when(taxCalculationService.calculatePensionRegressiveTaxRate(any(), any()))
                .thenReturn(new BigDecimal("0.150"));
        when(taxCalculationService.describeTaxBasis(anyLong(), eq("REGRESSIVO")))
                .thenReturn("Tabela regressiva IR - acima de 720 dias (15,0%)");
        when(taxCalculationService.calculateTaxAmount(any(), any()))
                .thenReturn(new BigDecimal("1800.00000000"));

        TaxSuggestionDto result = assetService.getTaxSuggestion(
                1L, new BigDecimal("12000.00"), LocalDate.of(2023, 1, 1), authentication);

        assertNotNull(result);
        // PGBL: base = total (12.000)
        verify(taxCalculationService).calculateTaxAmount(
                eq(new BigDecimal("12000.00")), any());
    }

    @Test
    void getTaxSuggestion_WhenPensionProgressivo_ShouldUse15PercentFixed() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.PREVIDENCIA, le);
        PensionDetails details = buildPensionDetails(asset, PensionType.PGBL, TaxRegime.PROGRESSIVO);
        InvestmentPosition position = buildPosition(asset);
        position.setTotalInvested(new BigDecimal("10000.00000000"));

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(pensionDetailsRepository.findByAssetId(1L)).thenReturn(Optional.of(details));
        when(taxCalculationService.getPensionProgressiveTaxRate())
                .thenReturn(new BigDecimal("0.150"));
        when(taxCalculationService.describeTaxBasis(anyLong(), eq("PROGRESSIVO")))
                .thenReturn("15% retidos na fonte (antecipação) — ajuste final na Declaração Anual");
        when(taxCalculationService.calculateTaxAmount(any(), any()))
                .thenReturn(new BigDecimal("1800.00000000"));

        TaxSuggestionDto result = assetService.getTaxSuggestion(
                1L, new BigDecimal("12000.00"), LocalDate.of(2023, 1, 1), authentication);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.150").compareTo(result.suggestedTaxRate()));
        assertTrue(result.taxBasis().contains("antecipação"));
    }

    // -------------------------------------------------------------------------
    // Position
    // -------------------------------------------------------------------------

    @Test
    void updatePosition_WhenValid_ShouldUpdateCurrentValueAndReturnDto() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        InvestmentPosition position = buildPosition(asset);
        InvestmentPositionUpdateDto dto = new InvestmentPositionUpdateDto(
                new BigDecimal("1250.00"), LocalDate.of(2025, 3, 1));

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(investmentPositionRepository.save(position)).thenReturn(position);

        AssetDetailDto result = assetService.updatePosition(1L, dto, authentication);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("1250.00").compareTo(position.getCurrentValue()));
        assertEquals(LocalDate.of(2025, 3, 1), position.getLastValuationDate());
        verify(investmentPositionRepository).save(position);
    }

    @Test
    void updatePosition_WhenAssetNotActive_ShouldThrowIllegalStateException() {
        LegalEntity le = buildLegalEntity(10L);
        Asset asset = buildAsset(1L, AssetCategory.RENDA_FIXA, le);
        asset.setStatus(AssetStatus.MATURED);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));

        InvestmentPositionUpdateDto dto = new InvestmentPositionUpdateDto(
                new BigDecimal("1250.00"), LocalDate.of(2025, 3, 1));

        assertThrows(IllegalStateException.class,
                () -> assetService.updatePosition(1L, dto, authentication));
        verify(investmentPositionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private LegalEntity buildLegalEntity(Long id) {
        LegalEntity le = new LegalEntity();
        le.setId(id);
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        le.setUser(currentUser);
        return le;
    }

    private Asset buildAsset(Long id, AssetCategory category, LegalEntity legalEntity) {
        InvestmentPosition position = InvestmentPosition.builder()
                .totalInvested(new BigDecimal("1000.00000000"))
                .currentValue(new BigDecimal("1100.00000000"))
                .redeemedValue(BigDecimal.ZERO)
                .quantity(new BigDecimal("1.00000000"))
                .averagePrice(new BigDecimal("1000.00000000"))
                .build();

        Asset asset = Asset.builder()
                .user(currentUser)
                .legalEntity(legalEntity)
                .name("CDB Banco X")
                .category(category)
                .status(AssetStatus.ACTIVE)
                .build();
        asset.setId(id);
        asset.setPosition(position);
        return asset;
    }

    private FixedIncomeDetails buildFixedIncomeDetails(Asset asset, Indexer indexer, boolean taxFree) {
        return FixedIncomeDetails.builder()
                .asset(asset)
                .indexer(indexer)
                .annualRate(new BigDecimal("0.12000000"))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .taxFree(taxFree)
                .build();
    }

    private PensionDetails buildPensionDetails(Asset asset, PensionType type, TaxRegime regime) {
        return PensionDetails.builder()
                .asset(asset)
                .pensionType(type)
                .taxRegime(regime)
                .build();
    }

    private InvestmentPosition buildPosition(Asset asset) {
        return InvestmentPosition.builder()
                .asset(asset)
                .build();
    }

    private InvestmentTransaction buildInvestmentTransaction(Long id, Asset asset,
            InvestmentTransactionType type, BigDecimal amount, LocalDate date) {
        InvestmentTransaction tx = InvestmentTransaction.builder()
                .asset(asset)
                .type(type)
                .amount(amount)
                .transactionDate(date)
                .build();
        tx.setId(id);
        return tx;
    }
}