package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.investment.Asset;
import io.github.ronaldobertolucci.unita.model.investment.AssetCategory;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;
import io.github.ronaldobertolucci.unita.model.investment.FixedIncomeDetails;
import io.github.ronaldobertolucci.unita.model.investment.Indexer;
import io.github.ronaldobertolucci.unita.repository.FixedIncomeDetailsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetMaturityJobTest {

    @Mock private FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    @Mock private AssetMaturityJobProcessor processor;

    @InjectMocks private AssetMaturityJob job;

    @Test
    void markMaturedAssets_WhenNoMaturedAssets_ShouldNotCallProcessor() {
        when(fixedIncomeDetailsRepository.findAllMaturedByDate(any())).thenReturn(List.of());

        job.markMaturedAssets();

        verify(processor, never()).process(any());
    }

    @Test
    void markMaturedAssets_WhenOneMaturedAsset_ShouldCallProcessorOnce() {
        FixedIncomeDetails details = buildDetails(1L, LocalDate.now().minusDays(1));

        when(fixedIncomeDetailsRepository.findAllMaturedByDate(any())).thenReturn(List.of(details));

        job.markMaturedAssets();

        verify(processor).process(eq(1L));
    }

    @Test
    void markMaturedAssets_WhenMultipleMaturedAssets_ShouldCallProcessorForEach() {
        FixedIncomeDetails d1 = buildDetails(1L, LocalDate.now().minusDays(1));
        FixedIncomeDetails d2 = buildDetails(2L, LocalDate.now().minusDays(5));
        FixedIncomeDetails d3 = buildDetails(3L, LocalDate.now());

        when(fixedIncomeDetailsRepository.findAllMaturedByDate(any())).thenReturn(List.of(d1, d2, d3));

        job.markMaturedAssets();

        verify(processor).process(eq(1L));
        verify(processor).process(eq(2L));
        verify(processor).process(eq(3L));
    }

    @Test
    void markMaturedAssets_WhenProcessorThrowsForOne_ShouldContinueAndProcessOthers() {
        FixedIncomeDetails d1 = buildDetails(1L, LocalDate.now().minusDays(1));
        FixedIncomeDetails d2 = buildDetails(2L, LocalDate.now().minusDays(5));

        when(fixedIncomeDetailsRepository.findAllMaturedByDate(any())).thenReturn(List.of(d1, d2));
        doThrow(new RuntimeException("DB error")).when(processor).process(eq(1L));

        job.markMaturedAssets();

        verify(processor).process(eq(1L));
        verify(processor).process(eq(2L));
    }

    @Test
    void markMaturedAssets_ShouldQueryWithTodaysDate() {
        LocalDate today = LocalDate.now();
        when(fixedIncomeDetailsRepository.findAllMaturedByDate(today)).thenReturn(List.of());

        job.markMaturedAssets();

        verify(fixedIncomeDetailsRepository).findAllMaturedByDate(eq(today));
    }

    private FixedIncomeDetails buildDetails(Long assetId, LocalDate maturityDate) {
        Asset asset = Asset.builder()
                .name("CDB Banco")
                .category(AssetCategory.RENDA_FIXA)
                .status(AssetStatus.ACTIVE)
                .build();
        asset.setId(assetId);

        FixedIncomeDetails details = FixedIncomeDetails.builder()
                .asset(asset)
                .indexer(Indexer.CDI)
                .annualRate(new BigDecimal("0.12000000"))
                .maturityDate(maturityDate)
                .taxFree(false)
                .build();
        details.setId(assetId);
        return details;
    }
}