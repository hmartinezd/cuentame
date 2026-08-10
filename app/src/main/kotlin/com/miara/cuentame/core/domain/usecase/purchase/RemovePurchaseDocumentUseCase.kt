package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.purchase.SourceMutationResult
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import javax.inject.Inject

class RemovePurchaseDocumentUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(receiptId: PurchaseReceiptId): SourceMutationResult {
        return repository.removeDocument(receiptId)
    }
}
