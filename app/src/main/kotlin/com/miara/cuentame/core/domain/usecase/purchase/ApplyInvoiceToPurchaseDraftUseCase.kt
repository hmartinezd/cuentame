package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import javax.inject.Inject

class ApplyInvoiceToPurchaseDraftUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) {
    suspend fun execute(
        proposal: PurchaseInvoiceDraftProposal
    ): PurchaseInvoiceMaterializationResult {
        return purchaseRepository.applyInvoiceToDraft(proposal)
    }
}
