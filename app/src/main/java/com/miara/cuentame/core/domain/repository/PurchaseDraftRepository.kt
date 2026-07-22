package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import kotlinx.coroutines.flow.Flow

interface PurchaseDraftRepository {
    fun observeDraftReceipts(): Flow<List<PurchaseReceipt>>
    suspend fun getDraftReceipt(id: PurchaseReceiptId): PurchaseReceipt?
    suspend fun getDraftLines(id: PurchaseReceiptId): List<PurchaseLine>
    suspend fun saveDraft(receipt: PurchaseReceipt, lines: List<PurchaseLine>)
    suspend fun deleteDraft(id: PurchaseReceiptId)
}
