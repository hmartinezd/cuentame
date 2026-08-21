package com.venkoi.cuentame.core.domain.usecase.purchase

import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.model.purchase.SourceMutationResult
import com.venkoi.cuentame.core.domain.repository.PurchaseRepository
import javax.inject.Inject

class RemovePurchaseDocumentUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(receiptId: PurchaseReceiptId): SourceMutationResult {
        return repository.removeDocument(receiptId)
    }
}
