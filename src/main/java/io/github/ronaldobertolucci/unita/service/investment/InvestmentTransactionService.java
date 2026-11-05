package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.InvestmentBuyDto;
import io.github.ronaldobertolucci.unita.dto.investment.InvestmentSellDto;
import io.github.ronaldobertolucci.unita.dto.investment.InvestmentTransactionDto;
import io.github.ronaldobertolucci.unita.dto.investment.InvestmentYieldDto;
import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvestmentTransactionService {

    private final AssetRepository assetRepository;
    private final FinancialConcurrencyService locks;
    private final InvestmentTransactionRepository investmentTransactionRepository;
    private final InvestmentPositionRepository investmentPositionRepository;
    private final PocketRepository pocketRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;

    // -------------------------------------------------------------------------
    // BUY — Aporte
    // -------------------------------------------------------------------------

    @Transactional
    public InvestmentTransactionDto buy(Long assetId, InvestmentBuyDto dto, Authentication authentication) {
        InvestmentDecimals.positive(dto.amount(), "amount");
        InvestmentDecimals.positive(dto.quantity(), "quantity");
        User currentUser = (User) authentication.getPrincipal();

        locks.lockOwnedAsset(assetId, currentUser.getId());
        Asset asset = assetRepository.findByIdAndUserId(assetId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        if (asset.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalStateException("Cannot buy into a non-active asset");
        }

        Pocket pocket = pocketRepository.findByIdAndUserId(dto.pocketId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pocket not found"));

        Category category = categoryService.resolveCategory(dto.categoryId(), currentUser,
                EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL));

        InvestmentPosition position = investmentPositionRepository.findByAssetId(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));
        BigDecimal multiplied = InvestmentDecimals.calculated(dto.amount().multiply(dto.quantity()), "contribution");
        if (multiplied.signum() == 0) throw new IllegalArgumentException("contribution must not round to zero");
        BigDecimal newQuantity = InvestmentDecimals.calculated(position.getQuantity().add(dto.quantity()), "quantity");
        BigDecimal newTotalInvested = InvestmentDecimals.calculated(position.getTotalInvested().add(multiplied), "totalInvested");
        BigDecimal newRemainingCost = InvestmentDecimals.calculated(position.getRemainingCost().add(multiplied), "remainingCost");
        BigDecimal newCurrentValue = InvestmentDecimals.calculated(position.getCurrentValue().add(multiplied), "currentValue");
        BigDecimal newAveragePrice = InvestmentDecimals.calculated(
                newTotalInvested.divide(newQuantity, 8, RoundingMode.HALF_UP), "averagePrice");

        // Cria Transaction EXPENSE no pocket
        Transaction pocketTransaction = transactionRepository.save(Transaction.builder()
                .pocket(pocket)
                .amount(multiplied)
                .direction(Direction.EXPENSE)
                .transactionDate(dto.transactionDate())
                .description("Aporte em " + asset.getName())
                .category(category)
                .build());

        // Cria InvestmentTransaction BUY
        InvestmentTransaction investmentTransaction = investmentTransactionRepository.save(
                InvestmentTransaction.builder()
                        .asset(asset)
                        .transaction(pocketTransaction)
                        .type(InvestmentTransactionType.BUY)
                        .amount(multiplied)
                        .transactionDate(dto.transactionDate())
                        .notes(dto.notes())
                        .build());

        // Atualiza posição
        position.setTotalInvested(newTotalInvested);
        position.setRemainingCost(newRemainingCost);
        position.setQuantity(newQuantity);
        position.setAveragePrice(newAveragePrice);
        position.setCurrentValue(newCurrentValue);
        investmentPositionRepository.save(position);

        return new InvestmentTransactionDto(investmentTransaction);
    }

    // -------------------------------------------------------------------------
    // YIELD — Rendimento creditado em conta
    // -------------------------------------------------------------------------

    @Transactional
    public InvestmentTransactionDto yield(Long assetId, InvestmentYieldDto dto, Authentication authentication) {
        InvestmentDecimals.positive(dto.amount(), "amount");
        User currentUser = (User) authentication.getPrincipal();

        locks.lockOwnedAsset(assetId, currentUser.getId());
        Asset asset = assetRepository.findByIdAndUserId(assetId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        if (asset.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalStateException("Cannot register yield for a non-active asset");
        }

        Pocket pocket = pocketRepository.findByIdAndUserId(dto.pocketId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pocket not found"));

        Category category = categoryService.resolveCategory(dto.categoryId(), currentUser,
                EnumSet.of(CategoryType.INCOME, CategoryType.NEUTRAL));

        InvestmentPosition position = investmentPositionRepository.findByAssetId(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));

        if (dto.amount().compareTo(position.getCurrentValue()) > 0) {
            throw new IllegalStateException("Yield value cannot be greater than current value.");
        }

        BigDecimal newRedeemedValue = InvestmentDecimals.calculated(position.getRedeemedValue().add(dto.amount()), "redeemedValue");

        // Cria Transaction INCOME no pocket
        Transaction pocketTransaction = transactionRepository.save(Transaction.builder()
                .pocket(pocket)
                .amount(dto.amount())
                .direction(Direction.INCOME)
                .transactionDate(dto.transactionDate())
                .description("Rendimento de " + asset.getName())
                .category(category)
                .build());

        // Cria InvestmentTransaction YIELD
        InvestmentTransaction investmentTransaction = investmentTransactionRepository.save(
                InvestmentTransaction.builder()
                        .asset(asset)
                        .transaction(pocketTransaction)
                        .type(InvestmentTransactionType.YIELD)
                        .amount(dto.amount())
                        .transactionDate(dto.transactionDate())
                        .notes(dto.notes())
                        .build());

        // Atualiza posição — acumula em redeemedValue para evitar duplicidade no resgate final
        position.setRedeemedValue(newRedeemedValue);
        position.setCurrentValue(position.getCurrentValue().subtract(dto.amount()));

        investmentPositionRepository.save(position);

        return new InvestmentTransactionDto(investmentTransaction);
    }

    // -------------------------------------------------------------------------
    // SELL — Resgate
    // -------------------------------------------------------------------------

    @Transactional
    public List<InvestmentTransactionDto> sell(Long assetId, InvestmentSellDto dto, Authentication authentication) {
        InvestmentDecimals.positive(dto.grossAmount(), "grossAmount");
        InvestmentDecimals.nonNegative(dto.taxAmount(), "taxAmount");
        if (dto.taxAmount().compareTo(dto.grossAmount()) > 0) {
            throw new IllegalArgumentException("taxAmount must not exceed grossAmount");
        }

        User currentUser = (User) authentication.getPrincipal();

        locks.lockOwnedAsset(assetId, currentUser.getId());
        Asset asset = assetRepository.findByIdAndUserId(assetId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Asset not found"));

        if (asset.getStatus() == AssetStatus.REDEEMED) {
            throw new IllegalStateException("Asset has already been fully redeemed");
        }

        Pocket pocket = pocketRepository.findByIdAndUserId(dto.pocketId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pocket not found"));

        Category category = categoryService.resolveCategory(dto.categoryId(), currentUser,
                EnumSet.of(CategoryType.INCOME, CategoryType.NEUTRAL));

        InvestmentPosition position = investmentPositionRepository.findByAssetId(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Position not found"));
        if (position.getQuantity().signum() <= 0) {
            throw new IllegalStateException("Cannot sell a position without units");
        }
        BigDecimal netAmount = dto.grossAmount().subtract(dto.taxAmount());
        BigDecimal newRedeemedValue = InvestmentDecimals.calculated(position.getRedeemedValue().add(netAmount), "redeemedValue");

        // Cria Transaction INCOME no pocket com valor líquido
        Transaction pocketTransaction = transactionRepository.save(Transaction.builder()
                .pocket(pocket)
                .amount(netAmount)
                .direction(Direction.INCOME)
                .transactionDate(dto.transactionDate())
                .description("Resgate de " + asset.getName())
                .category(category)
                .build());

        // Cria InvestmentTransaction SELL
        InvestmentTransaction sellTransaction = investmentTransactionRepository.save(
                InvestmentTransaction.builder()
                        .asset(asset)
                        .transaction(pocketTransaction)
                        .type(InvestmentTransactionType.SELL)
                        .amount(dto.grossAmount())
                        .transactionDate(dto.transactionDate())
                        .notes(dto.notes())
                        .build());

        // Cria InvestmentTransaction TAX
        InvestmentTransaction taxTransaction = investmentTransactionRepository.save(
                InvestmentTransaction.builder()
                        .asset(asset)
                        .type(InvestmentTransactionType.TAX)
                        .amount(dto.taxAmount())
                        .transactionDate(dto.transactionDate())
                        .notes("IR retido no resgate de " + asset.getName())
                        .build());

        // Atualiza posição
        position.setRedeemedValue(newRedeemedValue);
        position.setQuantity(BigDecimal.ZERO);
        position.setRemainingCost(BigDecimal.ZERO);
        position.setAveragePrice(BigDecimal.ZERO);
        position.setCurrentValue(BigDecimal.ZERO);
        investmentPositionRepository.save(position);

        asset.setStatus(AssetStatus.REDEEMED);
        assetRepository.save(asset);

        return List.of(
                new InvestmentTransactionDto(sellTransaction),
                new InvestmentTransactionDto(taxTransaction));
    }

    // -------------------------------------------------------------------------
    // Find
    // -------------------------------------------------------------------------

    public List<InvestmentTransactionDto> findAllByAsset(Long assetId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (!assetRepository.existsByIdAndUserId(assetId, currentUser.getId())) {
            throw new EntityNotFoundException("Asset not found");
        }

        return investmentTransactionRepository.findAllByAssetIdOrderByTransactionDateDesc(assetId)
                .stream()
                .map(InvestmentTransactionDto::new)
                .toList();
    }
}
