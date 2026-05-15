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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

        if (dto.custodianLegalEntityId() != null) {
            LegalEntity custodianLegalEntity = legalEntityRepository.findByIdAndUserId(dto.custodianLegalEntityId(), currentUser.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Custodian legal entity not found"));
            asset.setCustodianLegalEntity(custodianLegalEntity);
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

    public TaxSuggestionDto getTaxSuggestion(Long id, BigDecimal grossAmount, LocalDate purchaseDate,
                                             Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Asset asset = assetRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        InvestmentPosition position = investmentPositionRepository.findByAssetId(id)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));

        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(purchaseDate, today);

        BigDecimal totalInvested = position.getTotalInvested();
        BigDecimal rate;
        BigDecimal taxBase;
        String taxBasis;

        if (asset.getCategory() == AssetCategory.RENDA_FIXA) {
            FixedIncomeDetails details = fixedIncomeDetailsRepository.findByAssetId(id)
                    .orElseThrow(() -> new EntityNotFoundException("Fixed income details not found"));

            if (details.isTaxFree()) {
                BigDecimal earnings = grossAmount.subtract(totalInvested);
                return new TaxSuggestionDto(grossAmount, totalInvested, earnings,
                        BigDecimal.ZERO, BigDecimal.ZERO, grossAmount,
                        (int) days, "Isento de IR (LCI/LCA/Debênture Incentivada)");
            }

            // Renda Fixa: IR sobre rendimento
            taxBase = grossAmount.subtract(totalInvested);
            rate = taxCalculationService.calculateFixedIncomeTaxRate(purchaseDate, today);
            taxBasis = taxCalculationService.describeTaxBasis(days, "REGRESSIVO");

        } else {
            PensionDetails details = pensionDetailsRepository.findByAssetId(id)
                    .orElseThrow(() -> new EntityNotFoundException("Pension details not found"));

            if (details.getTaxRegime() == TaxRegime.PROGRESSIVO) {
                rate = taxCalculationService.getPensionProgressiveTaxRate();
                taxBasis = taxCalculationService.describeTaxBasis(days, "PROGRESSIVO");
            } else {
                rate = taxCalculationService.calculatePensionRegressiveTaxRate(purchaseDate, today);
                taxBasis = taxCalculationService.describeTaxBasis(days, "REGRESSIVO");
            }

            // PGBL e ENTIDADE_FECHADA: IR sobre total; VGBL: IR sobre rendimento
            taxBase = details.getPensionType() == PensionType.VGBL
                    ? grossAmount.subtract(totalInvested)
                    : grossAmount;
        }

        BigDecimal earnings = grossAmount.subtract(totalInvested);
        BigDecimal taxAmount = taxCalculationService.calculateTaxAmount(taxBase, rate);
        BigDecimal netAmount = grossAmount.subtract(taxAmount);

        return new TaxSuggestionDto(grossAmount, totalInvested, earnings,
                rate, taxAmount, netAmount, (int) days, taxBasis);
    }
}