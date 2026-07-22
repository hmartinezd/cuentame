package com.miara.cuentame.core.model.purchase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import java.math.BigDecimal
import java.time.Instant

data class PurchaseLine(
    val id: PurchaseLineId,
    val purchaseReceiptId: PurchaseReceiptId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val lineTotal: BigDecimal,
    val unitCostBase: BigDecimal,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
