package com.miara.cuentame.core.presentation.navigation

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.common.ids.PreparationRecipeComponentId
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.common.ids.ProductionBatchComponentId

object AppRoutes {
    var encoder: RouteEncoder = AndroidRouteEncoder

    fun ingredientDetail(id: IngredientId): String =
        "inventory/${encoder.encode(id.value)}"

    fun ingredientEdit(id: IngredientId): String =
        "inventory/${encoder.encode(id.value)}/edit"

    fun stockCountDraft(id: StockCountId): String =
        "count/${encoder.encode(id.value)}"

    fun stockCountDetail(id: StockCountId): String =
        "count/${encoder.encode(id.value)}/detail"

    fun stockCountArea(countId: StockCountId, areaId: StockCountAreaId): String =
        "count/${encoder.encode(countId.value)}/area/${encoder.encode(areaId.value)}"

    fun purchaseDraft(id: PurchaseReceiptId): String =
        "purchases/${encoder.encode(id.value)}"

    fun purchaseDetail(id: PurchaseReceiptId): String =
        "purchases/${encoder.encode(id.value)}/detail"

    fun purchaseLineCreate(receiptId: PurchaseReceiptId): String =
        "purchases/${encoder.encode(receiptId.value)}/line"

    fun purchaseLineEdit(receiptId: PurchaseReceiptId, lineId: PurchaseLineId): String =
        "purchases/${encoder.encode(receiptId.value)}/line/${encoder.encode(lineId.value)}"

    fun wasteDraft(id: WasteEventId): String =
        "waste/draft/${encoder.encode(id.value)}"

    fun wasteEdit(id: WasteEventId): String =
        "waste/${encoder.encode(id.value)}/edit"

    fun wasteDetail(id: WasteEventId): String =
        "waste/${encoder.encode(id.value)}"

    fun supplierEdit(id: SupplierId): String =
        "suppliers/${encoder.encode(id.value)}/edit"

    fun reportPurchaseDetail(rangeName: String): String =
        "reports/purchases?range=${encoder.encode(rangeName)}"

    fun reportWasteDetail(rangeName: String): String =
        "reports/waste?range=${encoder.encode(rangeName)}"

    fun preparationRecipeDraft(id: PreparationRecipeId): String =
        "preparations/recipes/${encoder.encode(id.value)}/edit"

    fun preparationRecipeDetail(id: PreparationRecipeId): String =
        "preparations/recipes/${encoder.encode(id.value)}"

    fun preparationRecipeComponentCreate(id: PreparationRecipeId): String =
        "preparations/recipes/${encoder.encode(id.value)}/component"

    fun preparationRecipeComponentEdit(
        recipeId: PreparationRecipeId,
        componentId: PreparationRecipeComponentId
    ): String =
        "preparations/recipes/${encoder.encode(recipeId.value)}/component/${encoder.encode(componentId.value)}"

    fun productionBatchCreate(
        recipeId: PreparationRecipeId? = null
    ): String =
        "production/batches/create" + (recipeId?.let { "?recipeId=${encoder.encode(it.value)}" } ?: "")

    fun productionBatchDraft(
        batchId: ProductionBatchId
    ): String =
        "production/batches/${encoder.encode(batchId.value)}/edit"

    fun productionBatchComponent(
        batchId: ProductionBatchId,
        componentId: ProductionBatchComponentId
    ): String =
        "production/batches/${encoder.encode(batchId.value)}/component/${encoder.encode(componentId.value)}"

    fun productionBatchPreview(
        batchId: ProductionBatchId
    ): String =
        "production/batches/${encoder.encode(batchId.value)}/preview"

    fun productionBatchDetail(
        batchId: ProductionBatchId
    ): String =
        "production/batches/${encoder.encode(batchId.value)}"
}
