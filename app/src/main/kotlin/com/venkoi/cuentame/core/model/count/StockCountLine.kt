package com.venkoi.cuentame.core.model.count

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.StockCountAreaId
import com.venkoi.cuentame.core.common.ids.StockCountLineId
import java.math.BigDecimal
import java.time.Instant

data class StockCountLine(
    val id: StockCountLineId,
    val stockCountAreaId: StockCountAreaId,
    val ingredientId: IngredientId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val expectedQuantityBaseSnapshot: BigDecimal? = null,
    val adjustmentQuantityBase: BigDecimal? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
