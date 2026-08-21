package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.SupplierId
import com.venkoi.cuentame.core.database.entity.SupplierItemMappingEntity
import com.venkoi.cuentame.core.model.supplier.SupplierItemMapping
import java.time.Instant

fun SupplierItemMappingEntity.toDomain(): SupplierItemMapping = SupplierItemMapping(
    id = id,
    restaurantId = RestaurantId(restaurantId),
    supplierId = SupplierId(supplierId),
    keyType = keyType,
    normalizedKey = normalizedKey,
    sourceVendorCode = sourceVendorCode,
    sourceDescription = sourceDescription,
    sourcePackageText = sourcePackageText,
    ingredientId = IngredientId(ingredientId),
    unitOptionId = unitOptionId?.let { IngredientUnitOptionId(it) },
    inventoryAreaId = inventoryAreaId?.let { InventoryAreaId(it) },
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    lastConfirmedAt = Instant.ofEpochMilli(lastConfirmedAt)
)

fun SupplierItemMapping.toEntity(): SupplierItemMappingEntity = SupplierItemMappingEntity(
    id = id,
    restaurantId = restaurantId.value,
    supplierId = supplierId.value,
    keyType = keyType,
    normalizedKey = normalizedKey,
    sourceVendorCode = sourceVendorCode,
    sourceDescription = sourceDescription,
    sourcePackageText = sourcePackageText,
    ingredientId = ingredientId.value,
    unitOptionId = unitOptionId?.value,
    inventoryAreaId = inventoryAreaId?.value,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    lastConfirmedAt = lastConfirmedAt.toEpochMilli()
)
