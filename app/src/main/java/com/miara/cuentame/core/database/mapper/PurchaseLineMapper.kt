package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.model.purchase.PurchaseLine
import java.math.BigDecimal
import java.time.Instant

fun PurchaseLineEntity.toDomain(): PurchaseLine = PurchaseLine(
    id = PurchaseLineId(id),
    purchaseReceiptId = PurchaseReceiptId(purchaseReceiptId),
    ingredientId = IngredientId(ingredientId),
    areaId = InventoryAreaId(areaId),
    ingredientUnitOptionId = IngredientUnitOptionId(ingredientUnitOptionId),
    quantityEntered = BigDecimal(quantityEntered),
    quantityBase = BigDecimal(quantityBase),
    lineTotal = BigDecimal(lineTotal),
    unitCostBase = BigDecimal(unitCostBase),
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun PurchaseLine.toEntity(): PurchaseLineEntity = PurchaseLineEntity(
    id = id.value,
    purchaseReceiptId = purchaseReceiptId.value,
    ingredientId = ingredientId.value,
    areaId = areaId.value,
    ingredientUnitOptionId = ingredientUnitOptionId.value,
    quantityEntered = quantityEntered.toPlainString(),
    quantityBase = quantityBase.toPlainString(),
    lineTotal = lineTotal.toPlainString(),
    unitCostBase = unitCostBase.toPlainString(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)
