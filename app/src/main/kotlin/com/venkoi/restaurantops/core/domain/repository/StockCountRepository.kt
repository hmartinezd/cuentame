package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.StockCountAreaId
import com.venkoi.restaurantops.core.common.ids.StockCountId
import com.venkoi.restaurantops.core.common.ids.StockCountLineId
import com.venkoi.restaurantops.core.model.count.StockCount
import com.venkoi.restaurantops.core.model.count.StockCountArea
import com.venkoi.restaurantops.core.model.count.StockCountLine
import com.venkoi.restaurantops.core.model.inventory.StockCountStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class StartStockCountCommand(
    val restaurantId: RestaurantId,
    val name: String,
    val effectiveAt: Instant,
    val areaIds: List<InventoryAreaId>,
    val notes: String?
)

data class UpdateStockCountDraftCommand(
    val countId: StockCountId,
    val name: String,
    val effectiveAt: Instant,
    val notes: String?
)

data class SaveStockCountLineCommand(
    val countId: StockCountId,
    val countAreaId: StockCountAreaId,
    val lineId: StockCountLineId?,
    val ingredientId: IngredientId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val notes: String?
)

data class StockCountSummary(
    val count: StockCount,
    val areaCount: Int,
    val progress: Float,
    val countedItemCount: Int = 0,
    val totalCountableItemCount: Int = 0
)

data class StockCountDriftItem(
    val countAreaId: StockCountAreaId,
    val inventoryAreaId: InventoryAreaId,
    val ingredientId: IngredientId,
    val expectedQuantityBaseSnapshot: BigDecimal?,
    val currentExpectedQuantityBase: BigDecimal?
)

data class StockCountDetails(
    val count: StockCount,
    val areas: List<StockCountAreaDetails>
)

data class StockCountAreaDetails(
    val area: StockCountArea,
    val areaName: String?,
    val restaurantId: RestaurantId,
    val countId: StockCountId,
    val countStatus: StockCountStatus,
    val effectiveAt: Instant,
    val lines: List<StockCountLine>
)

data class StockCountFilter(
    val restaurantId: RestaurantId,
    val status: StockCountStatus? = null,
    val query: String? = null
)

interface StockCountRepository {
    fun observeCounts(filter: StockCountFilter): Flow<List<StockCountSummary>>
    fun observeCount(id: StockCountId): Flow<StockCountDetails?>
    fun observeCountArea(id: StockCountAreaId): Flow<StockCountAreaDetails?>
    fun observeHasCompletedCount(restaurantId: RestaurantId): Flow<Boolean>
    
    suspend fun getCountedIngredientIds(
        countId: StockCountId,
        areaId: InventoryAreaId
    ): Set<IngredientId>

    suspend fun getDraftAreaIds(restaurantId: RestaurantId): Set<InventoryAreaId>
    suspend fun getItemOrder(areaId: InventoryAreaId): List<IngredientId>
    suspend fun saveItemOrder(areaId: InventoryAreaId, ingredientIds: List<IngredientId>)

    suspend fun start(command: StartStockCountCommand): StockCountId
    suspend fun updateDraft(command: UpdateStockCountDraftCommand)
    suspend fun saveLine(command: SaveStockCountLineCommand): StockCountLine
    suspend fun deleteLine(countId: StockCountId, countAreaId: StockCountAreaId, lineId: StockCountLineId)
    suspend fun completeArea(countId: StockCountId, countAreaId: StockCountAreaId)
    suspend fun reopenArea(countId: StockCountId, countAreaId: StockCountAreaId)
    suspend fun deleteDraft(countId: StockCountId)
    suspend fun completeCount(countId: StockCountId)
    suspend fun findDrift(countId: StockCountId): List<StockCountDriftItem>
    suspend fun reconfirmLine(countId: StockCountId, lineId: StockCountLineId)
    suspend fun voidCount(countId: StockCountId)
    suspend fun getExportRows(countId: StockCountId): List<StockCountExportRow>
}

data class StockCountExportRow(
    val areaName: String?,
    val ingredientName: String,
    val baseUnitSymbol: String,
    val expectedQuantityBase: String?,
    val countedQuantityBase: String,
    val adjustmentQuantityBase: String?,
    val notes: String?
)
