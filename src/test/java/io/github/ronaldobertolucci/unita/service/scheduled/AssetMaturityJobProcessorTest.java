package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.investment.Asset;
import io.github.ronaldobertolucci.unita.model.investment.AssetCategory;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;
import io.github.ronaldobertolucci.unita.repository.AssetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetMaturityJobProcessorTest {

    @Mock private AssetRepository assetRepository;
    @Mock private io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService locks;

    @InjectMocks private AssetMaturityJobProcessor processor;

    @Test
    void process_WhenAssetExists_ShouldSetStatusToMatured() {
        Asset asset = Asset.builder()
                .name("CDB Banco")
                .category(AssetCategory.RENDA_FIXA)
                .status(AssetStatus.ACTIVE)
                .build();
        asset.setId(1L);
        io.github.ronaldobertolucci.unita.investment.InvestmentTestDetails.complete(asset);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        processor.process(1L);

        assertEquals(AssetStatus.MATURED, asset.getStatus());
        verify(assetRepository).save(asset);
    }

    @Test
    void process_WhenAlreadyRedeemedOrMatured_ShouldPreserveStatus() {
        for (AssetStatus status : new AssetStatus[]{AssetStatus.REDEEMED, AssetStatus.MATURED}) {
            Asset asset = Asset.builder().id(1L).status(status).build();
            when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
            processor.process(1L);
            assertEquals(status, asset.getStatus());
        }
        verify(assetRepository, never()).save(any());
        var order = inOrder(locks, assetRepository);
        order.verify(locks).lockAsset(1L);
        order.verify(assetRepository).findById(1L);
    }

    @Test
    void process_WhenPension_ShouldPreserveActiveStatus() {
        Asset asset = Asset.builder().id(1L).category(AssetCategory.PREVIDENCIA).status(AssetStatus.ACTIVE).build();
        io.github.ronaldobertolucci.unita.investment.InvestmentTestDetails.complete(asset);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        processor.process(1L);

        assertEquals(AssetStatus.ACTIVE, asset.getStatus());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void process_WhenDetailsMissing_ShouldRejectWithoutChangingStatus() {
        Asset asset = Asset.builder().id(1L).category(AssetCategory.RENDA_FIXA).status(AssetStatus.ACTIVE).build();
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        assertThrows(IllegalStateException.class, () -> processor.process(1L));

        assertEquals(AssetStatus.ACTIVE, asset.getStatus());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void process_WhenAssetNotFound_ShouldThrowEntityNotFoundException() {
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> processor.process(99L));

        verify(assetRepository, never()).save(any());
    }
}
