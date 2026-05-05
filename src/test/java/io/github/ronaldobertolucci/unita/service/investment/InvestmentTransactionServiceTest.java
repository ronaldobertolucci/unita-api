package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.InvestmentBuyDto;
import io.github.ronaldobertolucci.unita.dto.investment.InvestmentSellDto;
import io.github.ronaldobertolucci.unita.dto.investment.InvestmentTransactionDto;
import io.github.ronaldobertolucci.unita.dto.investment.InvestmentYieldDto;
import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
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
class InvestmentTransactionServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private InvestmentTransactionRepository investmentTransactionRepository;
    @Mock
    private InvestmentPositionRepository investmentPositionRepository;
    @Mock private PocketRepository pocketRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryService categoryService;
    @Mock private Authentication authentication;

    @InjectMocks
    private InvestmentTransactionService investmentTransactionService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // buy
    // -------------------------------------------------------------------------

    @Test
    void buy_WhenValid_ShouldCreateTransactionsAndUpdatePosition() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        Pocket pocket = buildCash(5L);
        Category category = buildCategory(1L, CategoryType.NEUTRAL);
        InvestmentPosition position = buildPosition(asset);
        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 5L, 1L, null);

        Transaction savedPocketTx = buildPocketTransaction(10L, pocket, Direction.EXPENSE,
                new BigDecimal("1000.00"), category);
        InvestmentTransaction savedInvTx = buildInvestmentTransaction(
                1L, asset, InvestmentTransactionType.BUY, new BigDecimal("1000.00"), LocalDate.now());

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any())).thenReturn(category);
        when(transactionRepository.save(any())).thenReturn(savedPocketTx);
        when(investmentTransactionRepository.save(any())).thenReturn(savedInvTx);
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(investmentPositionRepository.save(any())).thenReturn(position);

        InvestmentTransactionDto result = investmentTransactionService.buy(1L, dto, authentication);

        assertNotNull(result);
        assertEquals(InvestmentTransactionType.BUY, result.type());
        verify(transactionRepository).save(any(Transaction.class));
        verify(investmentTransactionRepository).save(any(InvestmentTransaction.class));
        verify(investmentPositionRepository).save(any(InvestmentPosition.class));
    }

    @Test
    void buy_WhenAssetNotActive_ShouldThrowIllegalStateException() {
        Asset asset = buildAsset(1L, AssetStatus.MATURED);
        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));

        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 5L, 1L, null);

        assertThrows(IllegalStateException.class,
                () -> investmentTransactionService.buy(1L, dto, authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void buy_WhenAssetNotFound_ShouldThrow() {
        when(assetRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 5L, 1L, null);

        assertThrows(EntityNotFoundException.class,
                () -> investmentTransactionService.buy(99L, dto, authentication));
    }

    @Test
    void buy_WhenPocketNotFound_ShouldThrow() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 99L, 1L, null);

        assertThrows(EntityNotFoundException.class,
                () -> investmentTransactionService.buy(1L, dto, authentication));
    }

    @Test
    void buy_WhenCategoryTypeIncompatible_ShouldThrow() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        Pocket pocket = buildCash(5L);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Category type INCOME is not allowed in this context"));

        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 5L, 1L, null);

        assertThrows(IllegalArgumentException.class,
                () -> investmentTransactionService.buy(1L, dto, authentication));
    }

    @Test
    void buy_ShouldUpdatePositionCorrectly() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        Pocket pocket = buildCash(5L);
        Category category = buildCategory(1L, CategoryType.NEUTRAL);
        InvestmentPosition position = buildPosition(asset);
        position.setTotalInvested(new BigDecimal("500.00000000"));
        position.setQuantity(new BigDecimal("0.50000000"));

        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 5L, 1L, null);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any())).thenReturn(category);
        when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));
        when(investmentTransactionRepository.save(any()))
                .thenReturn(buildInvestmentTransaction(1L, asset, InvestmentTransactionType.BUY,
                        new BigDecimal("1000.00"), LocalDate.now()));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(investmentPositionRepository.save(any())).thenReturn(position);

        investmentTransactionService.buy(1L, dto, authentication);

        assertEquals(0, new BigDecimal("1500.00000000").compareTo(position.getTotalInvested()));
        assertEquals(0, new BigDecimal("1.50000000").compareTo(position.getQuantity()));
    }

    // -------------------------------------------------------------------------
    // yield
    // -------------------------------------------------------------------------

    @Test
    void yield_WhenValid_ShouldCreateTransactionsAndUpdateRedeemedValue() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        Pocket pocket = buildCash(5L);
        Category category = buildCategory(1L, CategoryType.NEUTRAL);
        InvestmentPosition position = buildPosition(asset);
        position.setCurrentValue(new BigDecimal("1500.00"));
        InvestmentYieldDto dto = new InvestmentYieldDto(
                new BigDecimal("50.00"), LocalDate.now(), 5L, 1L, "Rendimento mensal");

        Transaction savedPocketTx = buildPocketTransaction(10L, pocket, Direction.INCOME,
                new BigDecimal("50.00"), category);
        InvestmentTransaction savedInvTx = buildInvestmentTransaction(
                2L, asset, InvestmentTransactionType.YIELD, new BigDecimal("50.00"), LocalDate.now());

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any())).thenReturn(category);
        when(transactionRepository.save(any())).thenReturn(savedPocketTx);
        when(investmentTransactionRepository.save(any())).thenReturn(savedInvTx);
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(investmentPositionRepository.save(any())).thenReturn(position);

        InvestmentTransactionDto result = investmentTransactionService.yield(1L, dto, authentication);

        assertNotNull(result);
        assertEquals(InvestmentTransactionType.YIELD, result.type());
        assertEquals(0, new BigDecimal("50.00000000").compareTo(position.getRedeemedValue()));
        verify(investmentPositionRepository).save(position);
    }

    @Test
    void yield_WhenInvalid_ShouldNotCreateTransactions() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        Pocket pocket = buildCash(5L);
        Category category = buildCategory(1L, CategoryType.NEUTRAL);
        InvestmentPosition position = buildPosition(asset);
        InvestmentYieldDto dto = new InvestmentYieldDto(
                new BigDecimal("50.00"), LocalDate.now(), 5L, 1L, "Rendimento mensal");

        Transaction savedPocketTx = buildPocketTransaction(10L, pocket, Direction.INCOME,
                new BigDecimal("50.00"), category);
        InvestmentTransaction savedInvTx = buildInvestmentTransaction(
                2L, asset, InvestmentTransactionType.YIELD, new BigDecimal("50.00"), LocalDate.now());

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any())).thenReturn(category);
        when(transactionRepository.save(any())).thenReturn(savedPocketTx);
        when(investmentTransactionRepository.save(any())).thenReturn(savedInvTx);
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));

        assertThrows(java.lang.IllegalStateException.class,
                () -> investmentTransactionService.yield(1L, dto, authentication));
    }

    @Test
    void yield_WhenAssetNotActive_ShouldThrowIllegalStateException() {
        Asset asset = buildAsset(1L, AssetStatus.REDEEMED);
        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));

        InvestmentYieldDto dto = new InvestmentYieldDto(
                new BigDecimal("50.00"), LocalDate.now(), 5L, 1L, null);

        assertThrows(IllegalStateException.class,
                () -> investmentTransactionService.yield(1L, dto, authentication));
    }

    // -------------------------------------------------------------------------
    // sell
    // -------------------------------------------------------------------------

    @Test
    void sell_WhenValid_ShouldCreateSellAndTaxTransactionsAndUpdatePosition() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        Pocket pocket = buildCash(5L);
        Category category = buildCategory(1L, CategoryType.NEUTRAL);
        InvestmentPosition position = buildPosition(asset);
        position.setCurrentValue(new BigDecimal("1100.00000000"));

        InvestmentSellDto dto = new InvestmentSellDto(
                new BigDecimal("1100.00"), new BigDecimal("165.00"),
                LocalDate.now(), 5L, 1L, "Resgate total");

        InvestmentTransaction sellTx = buildInvestmentTransaction(
                3L, asset, InvestmentTransactionType.SELL, new BigDecimal("1100.00"), LocalDate.now());
        InvestmentTransaction taxTx = buildInvestmentTransaction(
                4L, asset, InvestmentTransactionType.TAX, new BigDecimal("165.00"), LocalDate.now());

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any())).thenReturn(category);
        when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));
        when(investmentTransactionRepository.save(any()))
                .thenReturn(sellTx)
                .thenReturn(taxTx);
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(investmentPositionRepository.save(any())).thenReturn(position);
        when(assetRepository.save(any())).thenReturn(asset);

        List<InvestmentTransactionDto> result = investmentTransactionService.sell(1L, dto, authentication);

        assertEquals(2, result.size());
        assertEquals(InvestmentTransactionType.SELL, result.get(0).type());
        assertEquals(InvestmentTransactionType.TAX, result.get(1).type());
        verify(transactionRepository).save(any(Transaction.class));
        verify(investmentTransactionRepository, times(2)).save(any(InvestmentTransaction.class));
    }

    @Test
    void sell_WhenFullRedemption_ShouldMarkAssetAsRedeemed() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        Pocket pocket = buildCash(5L);
        Category category = buildCategory(1L, CategoryType.NEUTRAL);
        InvestmentPosition position = buildPosition(asset);
        position.setCurrentValue(new BigDecimal("1100.00000000"));

        InvestmentSellDto dto = new InvestmentSellDto(
                new BigDecimal("1100.00"), new BigDecimal("165.00"),
                LocalDate.now(), 5L, 1L, null);

        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any())).thenReturn(category);
        when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));
        when(investmentTransactionRepository.save(any()))
                .thenReturn(buildInvestmentTransaction(3L, asset, InvestmentTransactionType.SELL,
                        new BigDecimal("1100.00"), LocalDate.now()))
                .thenReturn(buildInvestmentTransaction(4L, asset, InvestmentTransactionType.TAX,
                        new BigDecimal("165.00"), LocalDate.now()));
        when(investmentPositionRepository.findByAssetId(1L)).thenReturn(Optional.of(position));
        when(investmentPositionRepository.save(any())).thenReturn(position);
        when(assetRepository.save(asset)).thenReturn(asset);

        investmentTransactionService.sell(1L, dto, authentication);

        assertEquals(AssetStatus.REDEEMED, asset.getStatus());
        verify(assetRepository).save(asset);
    }

    @Test
    void sell_WhenAlreadyRedeemed_ShouldThrowIllegalStateException() {
        Asset asset = buildAsset(1L, AssetStatus.REDEEMED);
        when(assetRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(asset));

        InvestmentSellDto dto = new InvestmentSellDto(
                new BigDecimal("1100.00"), new BigDecimal("165.00"),
                LocalDate.now(), 5L, 1L, null);

        assertThrows(IllegalStateException.class,
                () -> investmentTransactionService.sell(1L, dto, authentication));
        verify(transactionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findAllByAsset
    // -------------------------------------------------------------------------

    @Test
    void findAllByAsset_WhenOwned_ShouldReturnList() {
        Asset asset = buildAsset(1L, AssetStatus.ACTIVE);
        InvestmentTransaction tx = buildInvestmentTransaction(
                1L, asset, InvestmentTransactionType.BUY,
                new BigDecimal("1000.00"), LocalDate.now());

        when(assetRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(investmentTransactionRepository.findAllByAssetIdOrderByTransactionDateDesc(1L))
                .thenReturn(List.of(tx));

        List<InvestmentTransactionDto> result =
                investmentTransactionService.findAllByAsset(1L, authentication);

        assertEquals(1, result.size());
        assertEquals(InvestmentTransactionType.BUY, result.get(0).type());
    }

    @Test
    void findAllByAsset_WhenAssetNotOwned_ShouldThrow() {
        when(assetRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> investmentTransactionService.findAllByAsset(99L, authentication));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Asset buildAsset(Long id, AssetStatus status) {
        LegalEntity le = new LegalEntity();
        le.setId(10L);
        le.setCorporateName("Banco Teste");

        Asset asset = Asset.builder()
                .user(currentUser)
                .legalEntity(le)
                .name("CDB Banco X")
                .category(AssetCategory.RENDA_FIXA)
                .status(status)
                .build();
        asset.setId(id);
        return asset;
    }

    private InvestmentPosition buildPosition(Asset asset) {
        InvestmentPosition position = InvestmentPosition.builder()
                .asset(asset)
                .build();
        position.setId(1L);
        return position;
    }

    private Cash buildCash(Long id) {
        Cash cash = new Cash();
        cash.setId(id);
        cash.setUser(currentUser);
        return cash;
    }

    private Category buildCategory(Long id, CategoryType type) {
        Category c = Category.builder()
                .user(null).name("Aporte em Investimento").type(type).system(true).build();
        c.setId(id);
        return c;
    }

    private Transaction buildPocketTransaction(Long id, Pocket pocket, Direction direction,
            BigDecimal amount, Category category) {
        Transaction tx = Transaction.builder()
                .pocket(pocket).amount(amount).direction(direction)
                .transactionDate(LocalDate.now()).description("Desc").category(category).build();
        tx.setId(id);
        return tx;
    }

    private InvestmentTransaction buildInvestmentTransaction(Long id, Asset asset,
            InvestmentTransactionType type, BigDecimal amount, LocalDate date) {
        InvestmentTransaction tx = InvestmentTransaction.builder()
                .asset(asset).type(type).amount(amount).transactionDate(date).build();
        tx.setId(id);
        return tx;
    }
}