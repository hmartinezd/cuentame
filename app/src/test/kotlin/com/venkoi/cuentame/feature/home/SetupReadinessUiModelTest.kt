package com.venkoi.cuentame.feature.home

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.model.inventory.InventoryArea
import java.time.Instant
import org.junit.Test

class SetupReadinessUiModelTest {
    private val area = InventoryArea(
        id = InventoryAreaId("area"), restaurantId = RestaurantId("restaurant"),
        name = "Walk-in", normalizedName = "walk-in", sortOrder = 0, isActive = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )

    @Test fun emptyRestaurantIsNotReady() {
        assertThat(SetupReadinessUiModel(emptyList(), 0, 0, emptyList(), false).coreReady).isFalse()
    }

    @Test fun partialConfigurationIsNotReady() {
        assertThat(SetupReadinessUiModel(listOf(area), 2, 0, listOf(IngredientId("missing")), false).coreReady).isFalse()
    }

    @Test fun prerequisitesWithoutInitialCountAreNotReady() {
        assertThat(SetupReadinessUiModel(listOf(area), 2, 0, emptyList(), false).coreReady).isFalse()
    }

    @Test fun canonicalCoreRequirementsProduceReadyState() {
        assertThat(SetupReadinessUiModel(listOf(area), 2, 0, emptyList(), true).coreReady).isTrue()
    }
}
