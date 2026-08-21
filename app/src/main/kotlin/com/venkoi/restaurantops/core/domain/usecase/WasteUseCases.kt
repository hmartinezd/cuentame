package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.WasteEventId
import com.venkoi.restaurantops.core.domain.repository.CreateWasteDraftCommand
import com.venkoi.restaurantops.core.domain.repository.UpdateWasteDraftCommand
import com.venkoi.restaurantops.core.domain.repository.WasteDetails
import com.venkoi.restaurantops.core.domain.repository.WasteFilter
import com.venkoi.restaurantops.core.domain.repository.WasteRepository
import com.venkoi.restaurantops.core.domain.repository.WasteSummary
import com.venkoi.restaurantops.core.domain.service.InventorySnapshotService
import com.venkoi.restaurantops.core.domain.validation.ValidationError
import com.venkoi.restaurantops.core.domain.repository.IngredientRepository
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import javax.inject.Inject

class ObserveWasteEventsUseCase @Inject constructor(
    private val repository: WasteRepository
) {
    operator fun invoke(filter: WasteFilter): Flow<List<WasteSummary>> =
        repository.observeWasteEvents(filter)
}

class ObserveWasteEventDetailsUseCase @Inject constructor(
    private val repository: WasteRepository
) {
    operator fun invoke(id: WasteEventId): Flow<WasteDetails?> =
        repository.observeWasteEvent(id)
}

class CreateWasteDraftUseCase @Inject constructor(
    private val repository: WasteRepository
) {
    suspend operator fun invoke(command: CreateWasteDraftCommand): WasteEventId =
        repository.createDraft(command)
}

class UpdateWasteDraftUseCase @Inject constructor(
    private val repository: WasteRepository
) {
    suspend operator fun invoke(command: UpdateWasteDraftCommand) =
        repository.updateDraft(command)
}

class DeleteWasteDraftUseCase @Inject constructor(
    private val repository: WasteRepository
) {
    suspend operator fun invoke(id: WasteEventId) =
        repository.deleteDraft(id)
}

data class WastePreview(
    val quantityBase: BigDecimal,
    val currentAreaQuantityBase: BigDecimal,
    val remainingAreaQuantityBase: BigDecimal,
    val averageCostBase: BigDecimal?,
    val estimatedWasteValue: BigDecimal?,
    val createsNegativeBalance: Boolean,
    val baseUnitSymbol: String?
)

class PreviewWasteUseCase @Inject constructor(
    private val ingredientRepository: IngredientRepository,
    private val snapshotService: InventorySnapshotService
) {
    suspend operator fun invoke(
        restaurantId: RestaurantId,
        ingredientId: IngredientId,
        areaId: InventoryAreaId,
        unitOptionId: com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId,
        quantityEntered: BigDecimal,
        effectiveAt: Instant
    ): WastePreview {
        val options = ingredientRepository.getUnitOptions(ingredientId, true)
        val option = options.find { it.id == unitOptionId } ?: throw ValidationError.WasteUnitOptionNotFound
        
        val quantityBase = quantityEntered.multiply(option.factorToBase, MathContext.DECIMAL128)
        
        val snapshot = snapshotService.calculateAt(
            restaurantId = restaurantId,
            ingredientId = ingredientId,
            areaId = areaId,
            effectiveAt = effectiveAt
        )

        val currentQty = snapshot.areaQuantityBase
        val remainingQty = currentQty.subtract(quantityBase)
        val averageCost = snapshot.ingredientAverageCostBase
        val estimatedValue = averageCost?.multiply(quantityBase, MathContext.DECIMAL128)

        val baseUnit = options.find { it.isBase }

        return WastePreview(
            quantityBase = quantityBase,
            currentAreaQuantityBase = currentQty,
            remainingAreaQuantityBase = remainingQty,
            averageCostBase = averageCost,
            estimatedWasteValue = estimatedValue,
            createsNegativeBalance = remainingQty < BigDecimal.ZERO,
            baseUnitSymbol = baseUnit?.shortLabel
        )
    }
}

class PostWasteEventUseCase @Inject constructor(
    private val repository: WasteRepository
) {
    suspend operator fun invoke(id: WasteEventId) =
        repository.post(id)
}

class VoidWasteEventUseCase @Inject constructor(
    private val repository: WasteRepository
) {
    suspend operator fun invoke(id: WasteEventId) =
        repository.void(id)
}
