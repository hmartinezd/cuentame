package com.miara.cuentame.core.model.purchase

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.SupplierId
import java.time.Instant

data class PurchaseInvoiceLineMatch(
    val parseResultId: String,
    val lineIndex: Int,
    val status: InvoiceLineMatchStatus,
    val supplierId: SupplierId?,
    val ingredientId: IngredientId?,
    val unitOptionId: IngredientUnitOptionId?,
    val inventoryAreaId: InventoryAreaId?,
    val mappingId: String?,
    val matchMethod: String?,
    val matchConfidence: Float,
    val confirmedAt: Instant?
)
