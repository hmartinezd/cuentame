package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.PurchaseLineId
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import com.venkoi.restaurantops.core.domain.repository.CreatePurchaseDraftCommand
import com.venkoi.restaurantops.core.domain.repository.PurchaseDetails
import com.venkoi.restaurantops.core.domain.repository.PurchaseFilter
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.core.domain.repository.PurchaseSummary
import com.venkoi.restaurantops.core.domain.repository.SavePurchaseLineCommand
import com.venkoi.restaurantops.core.domain.repository.UpdatePurchaseDraftCommand
import com.venkoi.restaurantops.core.model.purchase.PurchaseReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObservePurchasesUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    operator fun invoke(filter: PurchaseFilter): Flow<List<PurchaseSummary>> =
        repository.observePurchases(filter)
}

class ObservePurchaseDetailsUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    operator fun invoke(id: PurchaseReceiptId): Flow<PurchaseDetails?> =
        repository.observePurchase(id)
}

class GetPurchaseLineUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(receiptId: PurchaseReceiptId, lineId: PurchaseLineId): com.venkoi.restaurantops.core.model.purchase.PurchaseLine? {
        val details = repository.observePurchase(receiptId).first()
        return details?.lines?.find { it.line.id == lineId }?.line
    }
}

class GetPurchaseReceiptUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(id: PurchaseReceiptId): PurchaseReceipt? =
        repository.getReceipt(id)
}

class CreatePurchaseDraftUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(command: CreatePurchaseDraftCommand): PurchaseReceiptId =
        repository.createDraft(command)
}

class UpdatePurchaseDraftUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(command: UpdatePurchaseDraftCommand) =
        repository.updateDraft(command)
}

class SavePurchaseLineUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(command: SavePurchaseLineCommand): PurchaseLineId =
        repository.saveLine(command)
}

class DeletePurchaseLineUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(receiptId: PurchaseReceiptId, lineId: PurchaseLineId) =
        repository.deleteLine(receiptId, lineId)
}

class DeletePurchaseDraftUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(id: PurchaseReceiptId) =
        repository.deleteDraft(id)
}

class PostPurchaseUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(id: PurchaseReceiptId) =
        repository.post(id)
}

class VoidPurchaseUseCase @Inject constructor(
    private val repository: PurchaseRepository
) {
    suspend operator fun invoke(id: PurchaseReceiptId) =
        repository.void(id)
}
