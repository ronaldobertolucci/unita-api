package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.*;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    private final PensionDetailsRepository pensionDetailsRepository;
    private final InvestmentPositionRepository investmentPositionRepository;
    private final InvestmentTransactionRepository investmentTransactionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final TaxCalculationService taxCalculationService;
    private final Clock investmentClock;

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
        User currentUser = (User) authentication.getPrincipal();

        Asset asset = assetRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        if (asset.getStatus() == AssetStatus.REDEEMED) {
            throw new IllegalStateException("Cannot update position of a redeemed asset");
        }

        InvestmentPosition position = investmentPositionRepository.findByAssetId(id)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));

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
    public TaxSuggestionDto getTaxSuggestion(Long id, BigDecimal grossAmount, LocalDate purchaseDate,
                                             Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Asset asset = assetRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        InvestmentPosition position = investmentPositionRepository.findByAssetId(id)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));

        LocalDate today = LocalDate.now(investmentClock.withZone(ZoneId.of("America/Sao_Paulo")));
        if (purchaseDate == null || purchaseDate.isAfter(today)) {
            throw new IllegalArgumentException("purchaseDate is required and must not be in the future");
        }
        if (grossAmount == null || grossAmount.signum() <= 0) {
            throw new IllegalArgumentException("grossAmount must be greater than zero");
        }
        long days = ChronoUnit.DAYS.between(purchaseDate, today);
        if (days > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("purchaseDate exceeds the supported holding period");
        }

        BigDecimal totalInvested = position.getTotalInvested();
        if (totalInvested == null || totalInvested.signum() <= 0) {
            throw new IllegalArgumentException("totalInvested must be greater than zero for a tax suggestion");
        }
        BigDecimal earnings = grossAmount.subtract(totalInvested);
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
                rate = taxCalculationService.calculateFixedIncomeTaxRate(purchaseDate, today);
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
                    : taxCalculationService.calculatePensionRegressiveTaxRate(purchaseDate, today);
            boolean earningsBased = details.getPensionType() == PensionType.VGBL;
            taxBase = earningsBased ? earnings.max(BigDecimal.ZERO) : grossAmount;
            taxBasis = taxCalculationService.describePensionTaxBasis(days, details.getTaxRegime())
                    + (earningsBased ? "; base: rendimento positivo (VGBL)" : "; base: valor bruto total (PGBL/entidade fechada)");
        } else {
            throw new IllegalArgumentException("Tax suggestion is unavailable for this asset category");
        }

        // The legacy ledger has no per-contribution units or remaining tax lots.
        List<LocalDate> purchaseDates = investmentTransactionRepository.findDistinctPurchaseDatesByAssetId(id);
        List<String> warnings = new ArrayList<>();
        if (purchaseDates.size() > 1) {
            warnings.add("Estimativa limitada: há aportes em datas distintas e o histórico legado não permite apuração por lote; a data informada não representa todos os aportes.");
        } else if (purchaseDates.isEmpty()) {
            warnings.add("Estimativa limitada: histórico de aportes ausente; cálculo baseado somente na data informada.");
        } else if (!purchaseDates.getFirst().equals(purchaseDate)) {
            warnings.add("Estimativa limitada: a data informada difere da data do aporte registrado.");
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
                rate, taxAmount, netAmount, (int) days, taxBasis, taxBase, estimationStatus, warnings);
    }
}
