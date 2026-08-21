package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.StockCountAreaId
import com.venkoi.cuentame.core.common.ids.StockCountLineId
import com.venkoi.cuentame.core.database.entity.StockCountLineEntity
import com.venkoi.cuentame.core.model.count.StockCountLine
import java.math.BigDecimal
import java.time.Instant

fun StockCountLineEntity.toDomain(): StockCountLine = StockCountLine(
    id = StockCountLineId(id),
    stockCountAreaId = StockCountAreaId(stockCountAreaId),
    ingredientId = IngredientId(ingredientId),
    ingredientUnitOptionId = IngredientUnitOptionId(ingredientUnitOptionId),
    quantityEntered = BigDecimal(quantityEntered),
    quantityBase = BigDecimal(quantityBase),
    expectedQuantityBaseSnapshot = expectedQuantityBaseSnapshot?.let { BigDecimal(it) },
    adjustmentQuantityBase = adjustmentQuantityBase?.let { BigDecimal(it) },
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun StockCountLine.toEntity(): StockCountLineEntity = StockCountLineEntity(
    id = id.value,
    stockCountAreaId = stockCountAreaId.value,
    ingredientId = ingredientId.value,
    ingredientUnitOptionId = ingredientUnitOptionId.value,
    quantityEntered = quantityEntered.toPlainString(),
    quantityBase = quantityBase.toPlainString(),
    expectedQuantityBaseSnapshot = expectedQuantityBaseSnapshot?.toPlainString(),
    adjustmentQuantityBase = adjustmentQuantityBase?.toPlainString(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)
