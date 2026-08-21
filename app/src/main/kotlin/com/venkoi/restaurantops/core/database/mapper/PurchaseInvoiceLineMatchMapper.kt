package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import com.venkoi.restaurantops.core.database.entity.PurchaseInvoiceLineMatchEntity
import com.venkoi.restaurantops.core.model.purchase.PurchaseInvoiceLineMatch
import java.time.Instant

fun PurchaseInvoiceLineMatchEntity.toDomain(): PurchaseInvoiceLineMatch = PurchaseInvoiceLineMatch(
    parseResultId = parseResultId,
    lineIndex = lineIndex,
    status = status,
    supplierId = supplierId?.let { SupplierId(it) },
    ingredientId = ingredientId?.let { IngredientId(it) },
    unitOptionId = unitOptionId?.let { IngredientUnitOptionId(it) },
    inventoryAreaId = inventoryAreaId?.let { InventoryAreaId(it) },
    mappingId = mappingId,
    matchMethod = matchMethod,
    matchConfidence = matchConfidence,
    confirmedAt = confirmedAt?.let { Instant.ofEpochMilli(it) }
)

fun PurchaseInvoiceLineMatch.toEntity(): PurchaseInvoiceLineMatchEntity = PurchaseInvoiceLineMatchEntity(
    parseResultId = parseResultId,
    lineIndex = lineIndex,
    status = status,
    supplierId = supplierId?.value,
    ingredientId = ingredientId?.value,
    unitOptionId = unitOptionId?.value,
    inventoryAreaId = inventoryAreaId?.value,
    mappingId = mappingId,
    matchMethod = matchMethod,
    matchConfidence = matchConfidence,
    confirmedAt = confirmedAt?.toEpochMilli()
)
