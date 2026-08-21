package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.WasteEventId
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.core.model.inventory.WasteReason
import com.venkoi.restaurantops.core.model.waste.WasteEvent
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class CreateWasteDraftCommand(
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val reason: WasteReason,
    val effectiveAt: Instant,
    val notes: String?,
    val attachmentUri: String?
)

data class UpdateWasteDraftCommand(
    val wasteEventId: WasteEventId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val reason: WasteReason,
    val effectiveAt: Instant,
    val notes: String?,
    val attachmentUri: String?
)

data class WasteFilter(
    val restaurantId: RestaurantId,
    val status: DocumentStatus? = null,
    val query: String? = null
)

data class WasteSummary(
    val event: WasteEvent,
    val ingredientName: String?,
    val areaName: String?,
    val unitLabel: String?,
    val estimatedValue: BigDecimal?
)

data class WasteDetails(
    val event: WasteEvent,
    val ingredientName: String?,
    val isIngredientActive: Boolean = true,
    val areaName: String?,
    val isAreaActive: Boolean = true,
    val unitLabel: String?,
    val isUnitActive: Boolean = true,
    val baseUnitSymbol: String?,
    val currentAreaQuantityBase: BigDecimal?,
    val remainingAreaQuantityBase: BigDecimal?,
    val averageCostBase: BigDecimal?,
    val estimatedValue: BigDecimal?,
    val createsNegativeBalance: Boolean = false
)

interface WasteRepository {
    fun observeWasteEvents(filter: WasteFilter): Flow<List<WasteSummary>>
    fun observeWasteEvent(id: WasteEventId): Flow<WasteDetails?>
    suspend fun getById(id: WasteEventId): WasteEvent?
    
    suspend fun createDraft(command: CreateWasteDraftCommand): WasteEventId
    suspend fun updateDraft(command: UpdateWasteDraftCommand)
    suspend fun deleteDraft(id: WasteEventId)
    
    suspend fun post(id: WasteEventId)
    suspend fun void(id: WasteEventId)
}
