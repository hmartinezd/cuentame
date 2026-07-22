package com.miara.cuentame.core.model.inventory

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class InventoryMovement(
    val id: InventoryMovementId,
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val movementType: InventoryMovementType,
    val quantityBaseSigned: BigDecimal,
    val unitCostBaseSnapshot: BigDecimal? = null,
    val totalValueSnapshot: BigDecimal? = null,
    val effectiveAt: Instant,
    val sourceDocumentType: SourceDocumentType,
    val sourceDocumentId: String,
    val sourceLineId: String? = null,
    val reversalOfMovementId: InventoryMovementId? = null,
    val createdAt: Instant
)
