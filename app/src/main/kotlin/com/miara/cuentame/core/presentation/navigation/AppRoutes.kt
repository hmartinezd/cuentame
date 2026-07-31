package com.miara.cuentame.core.presentation.navigation

import android.net.Uri
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.StockCountAreaId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.WasteEventId

object AppRoutes {
    fun ingredientDetail(id: IngredientId): String =
        "inventory/${Uri.encode(id.value)}"

    fun ingredientEdit(id: IngredientId): String =
        "inventory/${Uri.encode(id.value)}/edit"

    fun stockCountDraft(id: StockCountId): String =
        "count/${Uri.encode(id.value)}"

    fun stockCountDetail(id: StockCountId): String =
        "count/${Uri.encode(id.value)}/detail"

    fun stockCountArea(countId: StockCountId, areaId: StockCountAreaId): String =
        "count/${Uri.encode(countId.value)}/area/${Uri.encode(areaId.value)}"

    fun purchaseDraft(id: PurchaseReceiptId): String =
        "purchases/${Uri.encode(id.value)}"

    fun purchaseDetail(id: PurchaseReceiptId): String =
        "purchases/${Uri.encode(id.value)}/detail"

    fun purchaseLineCreate(receiptId: PurchaseReceiptId): String =
        "purchases/${Uri.encode(receiptId.value)}/line"

    fun purchaseLineEdit(receiptId: PurchaseReceiptId, lineId: PurchaseLineId): String =
        "purchases/${Uri.encode(receiptId.value)}/line/${Uri.encode(lineId.value)}"

    fun wasteDraft(id: WasteEventId): String =
        "waste/draft/${Uri.encode(id.value)}"

    fun wasteEdit(id: WasteEventId): String =
        "waste/${Uri.encode(id.value)}/edit"

    fun wasteDetail(id: WasteEventId): String =
        "waste/${Uri.encode(id.value)}"

    fun supplierEdit(id: SupplierId): String =
        "suppliers/${Uri.encode(id.value)}/edit"

    fun reportPurchaseDetail(rangeName: String): String =
        "reports/purchases?range=${Uri.encode(rangeName)}"

    fun reportWasteDetail(rangeName: String): String =
        "reports/waste?range=${Uri.encode(rangeName)}"
}
