package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
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
    suspend operator fun invoke(receiptId: PurchaseReceiptId, lineId: PurchaseLineId): com.miara.cuentame.core.model.purchase.PurchaseLine? {
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
