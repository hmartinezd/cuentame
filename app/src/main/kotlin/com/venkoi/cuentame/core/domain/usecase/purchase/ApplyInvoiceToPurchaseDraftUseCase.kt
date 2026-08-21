package com.venkoi.cuentame.core.domain.usecase.purchase

import com.venkoi.cuentame.core.domain.repository.PurchaseRepository
import com.venkoi.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.venkoi.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
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
