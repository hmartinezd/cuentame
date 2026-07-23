package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject

data class StockCountLinePreview(
    val countedQuantityBase: BigDecimal,
    val expectedQuantityBase: BigDecimal?,
    val provisionalAdjustmentBase: BigDecimal,
    val willCreateOpeningBalance: Boolean,
    val averageCostBase: BigDecimal?,
    val estimatedValueChange: BigDecimal?
)

class PreviewStockCountLineUseCase @Inject constructor(
    private val snapshotService: InventorySnapshotService
) {
    suspend operator fun invoke(
        restaurantId: RestaurantId,
        ingredientId: IngredientId,
        areaId: InventoryAreaId,
        effectiveAt: Instant,
        quantityBase: BigDecimal
    ): StockCountLinePreview {
        val snapshot = snapshotService.calculateAt(
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            effectiveAt = effectiveAt
        )

        val expected = if (snapshot.hasEffectiveHistory) snapshot.areaQuantityBase else null
        val adjustment = if (expected == null) quantityBase else quantityBase.subtract(expected)
        
        val valueChange = if (snapshot.ingredientAverageCostBase != null) {
            adjustment.multiply(snapshot.ingredientAverageCostBase, MathContext.DECIMAL128)
        } else null

        return StockCountLinePreview(
            countedQuantityBase = quantityBase,
            expectedQuantityBase = expected,
            provisionalAdjustmentBase = adjustment,
            willCreateOpeningBalance = expected == null,
            averageCostBase = snapshot.ingredientAverageCostBase,
            estimatedValueChange = valueChange
        )
    }
}
