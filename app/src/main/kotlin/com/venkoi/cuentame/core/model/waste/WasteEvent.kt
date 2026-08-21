package com.venkoi.cuentame.core.model.waste

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.WasteEventId
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.WasteReason
import java.math.BigDecimal
import java.time.Instant

data class WasteEvent(
    val id: WasteEventId,
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val reason: WasteReason,
    val effectiveAt: Instant,
    val notes: String? = null,
    val attachmentPath: String? = null,
    val attachmentDisplayName: String? = null,
    val status: DocumentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val postedAt: Instant? = null,
    val voidedAt: Instant? = null
)
