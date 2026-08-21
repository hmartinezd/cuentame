package com.venkoi.restaurantops.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.repository.IngredientRepository
import com.venkoi.restaurantops.core.domain.service.InventorySnapshot
import com.venkoi.restaurantops.core.domain.service.InventorySnapshotService
import com.venkoi.restaurantops.core.model.ingredient.IngredientUnitOption
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PreviewWasteUseCaseTest {

    private val ingredientRepository = mockk<IngredientRepository>()
    private val snapshotService = mockk<InventorySnapshotService>()
    private val useCase = PreviewWasteUseCase(ingredientRepository, snapshotService)

    @Test
    fun `calculates preview correctly`() = runTest {
        val restId = RestaurantId("rest-1")
        val ingId = IngredientId("ing-1")
        val areaId = InventoryAreaId("area-1")
        val optId = IngredientUnitOptionId("opt-1")
        val now = Instant.now()

        val option = IngredientUnitOption(
            id = optId,
            ingredientId = ingId,
            displayName = "Case",
            shortLabel = "cs",
            standardUnitId = null,
            factorToBase = BigDecimal("10.0"),
            isBase = false,
            isDefaultCount = false,
            isDefaultPurchase = false,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        coEvery { ingredientRepository.getUnitOptions(ingId, true) } returns listOf(option)
        coEvery { snapshotService.calculateAt(restId, ingId, areaId, now) } returns InventorySnapshot(
            hasEffectiveHistory = true,
            areaQuantityBase = BigDecimal("50.0"),
            ingredientAverageCostBase = BigDecimal("2.0")
        )

        val result = useCase(restId, ingId, areaId, optId, BigDecimal("2.0"), now)

        // 2.0 cases * 10 = 20.0 base
        assertThat(result.quantityBase.compareTo(BigDecimal("20.0"))).isEqualTo(0)
        assertThat(result.currentAreaQuantityBase.compareTo(BigDecimal("50.0"))).isEqualTo(0)
        assertThat(result.remainingAreaQuantityBase.compareTo(BigDecimal("30.0"))).isEqualTo(0)
        assertThat(result.averageCostBase!!.compareTo(BigDecimal("2.0"))).isEqualTo(0)
        assertThat(result.estimatedWasteValue!!.compareTo(BigDecimal("40.0"))).isEqualTo(0) // 20 * 2
        assertThat(result.createsNegativeBalance).isFalse()
    }
}
