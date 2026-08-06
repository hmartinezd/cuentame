package com.miara.cuentame.core.domain.usecase.purchase

import android.net.Uri
import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import javax.inject.Inject

class AttachPurchaseDocumentUseCase @Inject constructor(
    private val repository: PurchaseRepository,
    private val documentStore: PurchaseDocumentStore
) {
    suspend operator fun invoke(
        receiptId: PurchaseReceiptId,
        sourceUri: Uri
    ) {
        val stored = documentStore.importDocument(receiptId, sourceUri)
        try {
            repository.attachDocument(receiptId, stored.location)
        } catch (e: Exception) {
            documentStore.delete(stored.location)
            throw e
        }
    }
}
