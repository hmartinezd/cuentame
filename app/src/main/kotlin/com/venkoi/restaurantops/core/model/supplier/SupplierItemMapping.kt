package com.venkoi.restaurantops.core.model.supplier

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import java.time.Instant

data class SupplierItemMapping(
    val id: String,
    val restaurantId: RestaurantId,
    val supplierId: SupplierId,
    val keyType: SupplierItemMappingKeyType,
    val normalizedKey: String,
    val sourceVendorCode: String?,
    val sourceDescription: String?,
    val sourcePackageText: String?,
    val ingredientId: IngredientId,
    val unitOptionId: IngredientUnitOptionId?,
    val inventoryAreaId: InventoryAreaId?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastConfirmedAt: Instant
)
