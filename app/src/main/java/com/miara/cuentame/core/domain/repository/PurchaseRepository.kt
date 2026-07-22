package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class CreatePurchaseDraftCommand(
    val restaurantId: RestaurantId,
    val supplierId: SupplierId?,
    val invoiceNumber: String?,
    val purchaseDate: Instant,
    val notes: String?
)

data class UpdatePurchaseDraftCommand(
    val receiptId: PurchaseReceiptId,
    val supplierId: SupplierId?,
    val invoiceNumber: String?,
    val purchaseDate: Instant,
    val notes: String?
)

data class SavePurchaseLineCommand(
    val receiptId: PurchaseReceiptId,
    val lineId: PurchaseLineId?, // null for create
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val lineTotal: BigDecimal,
    val notes: String?
)

data class PurchaseFilter(
    val restaurantId: RestaurantId,
    val status: DocumentStatus? = null,
    val supplierId: SupplierId? = null,
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val query: String? = null
)

data class PurchaseSummary(
    val receipt: PurchaseReceipt,
    val supplierName: String?,
    val lineCount: Int,
    val totalAmount: BigDecimal
)

data class PurchaseDetails(
    val receipt: PurchaseReceipt,
    val supplierName: String?,
    val lines: List<PurchaseLineWithDetails>
)

data class PurchaseLineWithDetails(
    val line: PurchaseLine,
    val ingredientName: String,
    val areaName: String,
    val unitOptionName: String,
    val baseUnitSymbol: String
)

interface PurchaseRepository {
    fun observePurchases(
        filter: PurchaseFilter
    ): Flow<List<PurchaseSummary>>

    fun observePurchase(
        id: PurchaseReceiptId
    ): Flow<PurchaseDetails?>

    suspend fun getReceipt(id: PurchaseReceiptId): PurchaseReceipt?
    
    suspend fun createDraft(
        command: CreatePurchaseDraftCommand
    ): PurchaseReceiptId

    suspend fun updateDraft(
        command: UpdatePurchaseDraftCommand
    )

    suspend fun saveLine(
        command: SavePurchaseLineCommand
    ): PurchaseLineId

    suspend fun deleteLine(
        receiptId: PurchaseReceiptId,
        lineId: PurchaseLineId
    )

    suspend fun deleteDraft(
        id: PurchaseReceiptId
    )

    suspend fun post(
        id: PurchaseReceiptId
    )

    suspend fun void(
        id: PurchaseReceiptId
    )
}
