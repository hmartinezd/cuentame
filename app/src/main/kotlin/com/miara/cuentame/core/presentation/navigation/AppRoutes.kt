package com.miara.cuentame.core.presentation.navigation

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.WasteEventId

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
}
