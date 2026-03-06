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

    @InjectMocks private AssetMaturityJobProcessor processor;

    @Test
    void process_WhenAssetExists_ShouldSetStatusToMatured() {
        Asset asset = Asset.builder()
                .name("CDB Banco")
                .category(AssetCategory.RENDA_FIXA)
                .status(AssetStatus.ACTIVE)
                .build();
        asset.setId(1L);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        processor.process(1L);

        assertEquals(AssetStatus.MATURED, asset.getStatus());
        verify(assetRepository).save(asset);
    }

    @Test
    void process_WhenAssetNotFound_ShouldThrowEntityNotFoundException() {
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> processor.process(99L));

        verify(assetRepository, never()).save(any());
    }
}