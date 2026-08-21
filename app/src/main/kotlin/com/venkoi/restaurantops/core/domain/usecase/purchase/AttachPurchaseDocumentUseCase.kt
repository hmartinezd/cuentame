package com.venkoi.restaurantops.core.domain.usecase.purchase

import android.net.Uri
import com.venkoi.restaurantops.core.backup.api.PurchaseDocumentStore
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.model.purchase.SourceMutationResult
import javax.inject.Inject

class AttachPurchaseDocumentUseCase @Inject constructor(
    private val repository: PurchaseRepository,
    private val documentStore: PurchaseDocumentStore
) {
    suspend operator fun invoke(
        receiptId: PurchaseReceiptId,
        sourceUri: Uri,
        displayNameOverride: String? = null
    ): SourceMutationResult {
        val stored = documentStore.importDocument(receiptId, sourceUri, displayNameOverride)
        return try {
            val status = repository.attachDocument(receiptId, stored.location, stored.displayName)
            if (status != SourceMutationResult.Success) {
                documentStore.delete(stored.location)
            }
            status
        } catch (e: Exception) {
            documentStore.delete(stored.location)
            throw e
        }
    }
}
