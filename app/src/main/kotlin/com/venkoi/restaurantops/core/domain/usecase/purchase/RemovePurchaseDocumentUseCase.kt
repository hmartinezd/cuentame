package com.venkoi.restaurantops.core.domain.usecase.purchase

import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.model.purchase.SourceMutationResult
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import javax.inject.Inject

class RemovePurchaseDocumentUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(receiptId: PurchaseReceiptId): SourceMutationResult {
        return repository.removeDocument(receiptId)
    }
}
