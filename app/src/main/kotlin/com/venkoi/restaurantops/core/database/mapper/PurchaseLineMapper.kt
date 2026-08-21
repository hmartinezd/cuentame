package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.PurchaseLineId
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.database.entity.PurchaseLineEntity
import com.venkoi.restaurantops.core.model.purchase.PurchaseLine
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
