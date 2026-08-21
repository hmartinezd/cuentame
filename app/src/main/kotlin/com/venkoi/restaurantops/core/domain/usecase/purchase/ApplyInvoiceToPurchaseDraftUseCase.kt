package com.venkoi.restaurantops.core.domain.usecase.purchase

import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.venkoi.restaurantops.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
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
