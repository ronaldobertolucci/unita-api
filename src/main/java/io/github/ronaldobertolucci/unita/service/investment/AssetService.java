package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.*;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final FinancialConcurrencyService locks;
    private final FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    private final PensionDetailsRepository pensionDetailsRepository;
    private final InvestmentPositionRepository investmentPositionRepository;
    private final InvestmentTransactionRepository investmentTransactionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final TaxCalculationService taxCalculationService;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public AssetDetailDto createFixedIncome(FixedIncomeAssetCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (assetRepository.existsByNameAndUserId(dto.name(), currentUser.getId())) {
            throw new IllegalArgumentException("An asset with this name already exists");
        }

        LegalEntity legalEntity = legalEntityRepository.findByIdAndUserId(dto.legalEntityId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        LegalEntity custodianLegalEntity = null;

        if (dto.custodianLegalEntityId() != null) {
            custodianLegalEntity = legalEntityRepository.findByIdAndUserId(dto.custodianLegalEntityId(), currentUser.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Custodian legal entity not found"));
        }

        Asset asset = assetRepository.save(Asset.builder()
                .user(currentUser)
                .legalEntity(legalEntity)
                .name(dto.name())
                .category(AssetCategory.RENDA_FIXA)
                .status(AssetStatus.ACTIVE)
                .liquidityType(dto.liquidityType())
                .custodianLegalEntity(custodianLegalEntity)
                .build());

        FixedIncomeDetails details = FixedIncomeDetails.builder()
                .asset(asset)
                .indexer(dto.indexer())
                .annualRate(dto.annualRate())
                .maturityDate(dto.maturityDate())
                .taxFree(dto.taxFree())
                .build();
        FixedIncomeDetails savedDetails = fixedIncomeDetailsRepository.save(details);

        InvestmentPosition savedPosition = investmentPositionRepository.save(InvestmentPosition.builder()
                .asset(asset)
                .build());

        asset.setFixedIncomeDetails(savedDetails);
        asset.setPosition(savedPosition);

        return new AssetDetailDto(assetRepository.findByIdAndUserId(asset.getId(), currentUser.getId())
                .orElseThrow());
    }

    @Transactional
    public AssetDetailDto createPension(PensionAssetCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (assetRepository.existsByNameAndUserId(dto.name(), currentUser.getId())) {
            throw new IllegalArgumentException("An asset with this name already exists");
        }

        LegalEntity legalEntity = legalEntityRepository.findByIdAndUserId(dto.legalEntityId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        LegalEntity custodianLegalEntity = null;

        if (dto.custodianLegalEntityId() != null) {
            custodianLegalEntity = legalEntityRepository.findByIdAndUserId(dto.custodianLegalEntityId(), currentUser.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Custodian legal entity not found"));
        }

        Asset asset = assetRepository.save(Asset.builder()
                .user(currentUser)
                .legalEntity(legalEntity)
                .name(dto.name())
                .category(AssetCategory.PREVIDENCIA)
                .status(AssetStatus.ACTIVE)
                .liquidityType(LiquidityType.PREVIDENCIARIA)
                .custodianLegalEntity(custodianLegalEntity)
                .build());

        PensionDetails details = PensionDetails.builder()
                .asset(asset)
                .pensionType(dto.pensionType())
                .taxRegime(dto.taxRegime())
                .build();
        PensionDetails savedDetails = pensionDetailsRepository.save(details);

        InvestmentPosition savedPosition = investmentPositionRepository.save(InvestmentPosition.builder()
                .asset(asset)
                .build());

        asset.setPensionDetails(savedDetails);
        asset.setPosition(savedPosition);

        return new AssetDetailDto(assetRepository.findByIdAndUserId(asset.getId(), currentUser.getId())
                .orElseThrow());
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<AssetSummaryDto> findAll(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return assetRepository.findAllByUserIdOrderByName(currentUser.getId())
                .stream()
                .map(AssetSummaryDto::new)
                .toList();
    }

    public AssetDetailDto findById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return assetRepository.findByIdAndUserId(id, currentUser.getId())
                .map(AssetDetailDto::new)
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    public AssetDetailDto update(Long id, AssetUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        locks.lockOwnedAsset(id, currentUser.getId());
        Asset asset = assetRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        if (!asset.getName().equals(dto.name()) &&
                assetRepository.existsByNameAndUserId(dto.name(), currentUser.getId())) {
            throw new IllegalArgumentException("An asset with this name already exists");
        }

        LegalEntity legalEntity = legalEntityRepository.findByIdAndUserId(dto.legalEntityId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        asset.setName(dto.name());
        asset.setLegalEntity(legalEntity);
        asset.setLiquidityType(dto.liquidityType());

        if (dto.custodianLegalEntityId() != null) {
            LegalEntity custodianLegalEntity = legalEntityRepository
                    .findByIdAndUserId(dto.custodianLegalEntityId(), currentUser.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Custodian legal entity not found"));
            asset.setCustodianLegalEntity(custodianLegalEntity);
        } else {
            asset.setCustodianLegalEntity(null);
        }

        return new AssetDetailDto(assetRepository.save(asset));
    }

    @Transactional
    public AssetDetailDto updatePosition(Long id, InvestmentPositionUpdateDto dto, Authentication authentication) {
        InvestmentDecimals.nonNegative(dto.currentValue(), "currentValue");
        User currentUser = (User) authentication.getPrincipal();

        locks.lockOwnedAsset(id, currentUser.getId());
        Asset asset = assetRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        if (asset.getStatus() == AssetStatus.REDEEMED) {
            throw new IllegalStateException("Cannot update position of a redeemed asset");
        }

        InvestmentPosition position = investmentPositionRepository.findByAssetId(id)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));

        if (position.getQuantity().signum() == 0 && dto.currentValue().signum() > 0) {
            throw new IllegalStateException("Cannot value a position without units");
        }
        position.setCurrentValue(dto.currentValue());
        position.setLastValuationDate(dto.lastValuationDate());
        investmentPositionRepository.save(position);

        asset.setPosition(position);
        return new AssetDetailDto(asset);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        locks.lockOwnedAsset(id, currentUser.getId());
        Asset asset = assetRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        if (investmentTransactionRepository.existsByAssetId(asset.getId())) {
            throw new IllegalStateException("Asset has transactions and cannot be deleted");
        }

        assetRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Tax Suggestion
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TaxSuggestionDto getTaxSuggestion(Long id, BigDecimal grossAmount, LocalDate purchaseDate, BigDecimal quantity, LocalDate saleDate,
                                             Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Asset asset = assetRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        InvestmentPosition position = investmentPositionRepository.findByAssetId(id)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));

        InvestmentDecimals.positive(grossAmount, "grossAmount");
        InvestmentDecimals.positive(quantity, "quantity");
        if (purchaseDate == null || saleDate == null || purchaseDate.isAfter(saleDate)) {
            throw new IllegalArgumentException("purchaseDate and saleDate are required; purchaseDate must not be after saleDate");
        }
        long days = ChronoUnit.DAYS.between(purchaseDate, saleDate);
        if (days > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("purchaseDate exceeds the supported holding period");
        }
        if (asset.getStatus() == AssetStatus.REDEEMED || position.getQuantity().signum() <= 0
                || quantity.compareTo(position.getQuantity()) > 0) {
            throw new IllegalStateException("Tax suggestion requires available units for the proposed sale");
        }
        BigDecimal totalInvested = position.getTotalInvested();
        BigDecimal costBasis = quantity.compareTo(position.getQuantity()) == 0 ? position.getRemainingCost()
                : position.getRemainingCost().multiply(quantity).divide(position.getQuantity(), 8, java.math.RoundingMode.HALF_UP);
        BigDecimal earnings = grossAmount.subtract(costBasis);
        BigDecimal rate;
        BigDecimal taxBase;
        String taxBasis;

        if (asset.getCategory() == AssetCategory.RENDA_FIXA) {
            FixedIncomeDetails details = fixedIncomeDetailsRepository.findByAssetId(id)
                    .orElseThrow(() -> new EntityNotFoundException("Fixed income details not found"));
            if (details.isTaxFree()) {
                rate = BigDecimal.ZERO;
                taxBase = BigDecimal.ZERO;
                taxBasis = "Isento de IR (LCI/LCA/Debênture Incentivada)";
            } else {
                taxBase = earnings.max(BigDecimal.ZERO);
                rate = taxCalculationService.calculateFixedIncomeTaxRate(purchaseDate, saleDate);
                taxBasis = taxCalculationService.describeFixedIncomeTaxBasis(days)
                        + "; base: rendimento positivo";
            }
        } else if (asset.getCategory() == AssetCategory.PREVIDENCIA) {
            PensionDetails details = pensionDetailsRepository.findByAssetId(id)
                    .orElseThrow(() -> new EntityNotFoundException("Pension details not found"));
            if (details.getTaxRegime() == null || details.getPensionType() == null) {
                throw new IllegalArgumentException("pension type and tax regime are required for a tax suggestion");
            }
            rate = details.getTaxRegime() == TaxRegime.PROGRESSIVO
                    ? taxCalculationService.getPensionProgressiveTaxRate()
                    : taxCalculationService.calculatePensionRegressiveTaxRate(purchaseDate, saleDate);
            boolean earningsBased = details.getPensionType() == PensionType.VGBL;
            taxBase = earningsBased ? earnings.max(BigDecimal.ZERO) : grossAmount;
            taxBasis = taxCalculationService.describePensionTaxBasis(days, details.getTaxRegime())
                    + (earningsBased ? "; base: rendimento positivo (VGBL)" : "; base: valor bruto total (PGBL/entidade fechada)");
        } else {
            throw new IllegalArgumentException("Tax suggestion is unavailable for this asset category");
        }

        // Quantities are recorded for new movements, but this model still has no remaining fiscal lots.
        List<LocalDate> purchaseDates = investmentTransactionRepository.findDistinctPurchaseDatesByAssetId(id);
        List<String> warnings = new ArrayList<>();
        if (purchaseDates.size() > 1) {
            warnings.add("Estimativa limitada: há aportes em datas distintas e o histórico legado não permite apuração por lote; a data informada não representa todos os aportes.");
        } else if (purchaseDates.isEmpty()) {
            warnings.add("Estimativa limitada: histórico de aportes ausente; cálculo baseado somente na data informada.");
        } else if (!purchaseDates.getFirst().equals(purchaseDate)) {
            warnings.add("Estimativa limitada: a data informada difere da data do aporte registrado.");
        }
        if (investmentTransactionRepository.existsByAssetIdAndTypeAndQuantityIsNull(id, InvestmentTransactionType.BUY)
                || investmentTransactionRepository.existsByAssetIdAndTypeAndCostBasisIsNull(id, InvestmentTransactionType.SELL)) {
            warnings.add("Estimativa limitada: histórico legado com quantidades ou custos de venda desconhecidos; não há apuração por lote fiscal.");
        }
        if (investmentTransactionRepository.existsByAssetIdAndType(id, InvestmentTransactionType.YIELD)) {
            warnings.add("Estimativa limitada: há recebimentos YIELD; o histórico não distingue distribuição de rendimento de retirada de principal, podendo afetar a base tributável.");
        }
        TaxSuggestionStatus estimationStatus = warnings.isEmpty()
                ? TaxSuggestionStatus.ESTIMATE : TaxSuggestionStatus.LIMITED_ESTIMATE;
        if (!warnings.isEmpty()) {
            taxBasis += "; " + String.join(" ", warnings);
        }
        BigDecimal taxAmount = rate.signum() == 0 ? BigDecimal.ZERO
                : taxCalculationService.calculateTaxAmount(taxBase, rate);
        BigDecimal netAmount = grossAmount.subtract(taxAmount);

        return new TaxSuggestionDto(grossAmount, totalInvested, earnings,
                rate, taxAmount, netAmount, (int) days, taxBasis, taxBase, estimationStatus, warnings, costBasis);
    }
}
