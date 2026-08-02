package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchSummary
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class CreateProductionBatchDraftCommand(
    val restaurantId: RestaurantId,
    val recipeId: PreparationRecipeId,
    val batchMultiplier: BigDecimal,
    val outputAreaId: InventoryAreaId,
    val actualOutputQuantityEntered: BigDecimal?,
    val outputUnitOptionId: IngredientUnitOptionId?,
    val effectiveAt: Instant,
    val notes: String?
)

data class UpdateProductionBatchDraftCommand(
    val batchId: ProductionBatchId,
    val batchMultiplier: BigDecimal?,
    val outputAreaId: InventoryAreaId?,
    val actualOutputQuantityEntered: BigDecimal?,
    val outputUnitOptionId: IngredientUnitOptionId?,
    val effectiveAt: Instant?,
    val notes: String?
)

data class UpdateProductionBatchComponentCommand(
    val batchId: ProductionBatchId,
    val componentId: ProductionBatchComponentId,
    val sourceAreaId: InventoryAreaId?,
    val actualQuantityEntered: BigDecimal?,
    val unitOptionId: IngredientUnitOptionId?,
    val notes: String?
)

data class ProductionBatchPostingPreview(
    val batchId: ProductionBatchId,
    val effectiveAt: Instant,
    val components: List<ProductionBatchComponentPostingPreview>,
    val totalComponentCost: BigDecimal?,
    val actualOutputQuantityBase: BigDecimal,
    val outputUnitCostBase: BigDecimal?,
    val yieldVariancePercent: BigDecimal?,
    val blockers: List<PostingBlocker>
)

data class ProductionBatchComponentPostingPreview(
    val componentId: ProductionBatchComponentId,
    val ingredientId: IngredientId,
    val ingredientName: String,
    val sourceAreaId: InventoryAreaId,
    val sourceAreaName: String,
    val actualQuantityEntered: BigDecimal,
    val actualQuantityBase: BigDecimal,
    val unitOptionLabel: String,
    val currentAreaBalanceBase: BigDecimal,
    val remainingAreaBalanceBase: BigDecimal,
    val createsNegativeBalance: Boolean,
    val averageUnitCostBase: BigDecimal?,
    val totalCost: BigDecimal?,
    val costUnavailable: Boolean
)

enum class PostingBlocker {
    RECIPE_NOT_ACTIVE,
    MISSING_COMPONENT_AREA,
    COMPONENT_COST_UNAVAILABLE,
    FUTURE_EFFECTIVE_TIME,
    INVALID_RESTAURANT,
    RESTRICTED_BY_ARCHIVE
}

interface ProductionBatchRepository {

    fun observeBatches(
        restaurantId: RestaurantId,
        status: DocumentStatus? = null
    ): Flow<List<ProductionBatchSummary>>

    fun observeBatch(
        batchId: ProductionBatchId
    ): Flow<ProductionBatch?>

    suspend fun getBatch(
        batchId: ProductionBatchId
    ): ProductionBatch?

    suspend fun createDraft(
        command: CreateProductionBatchDraftCommand
    ): ProductionBatchId

    suspend fun updateDraft(
        command: UpdateProductionBatchDraftCommand
    )

    suspend fun updateComponent(
        command: UpdateProductionBatchComponentCommand
    )

    suspend fun resetComponentToExpected(
        batchId: ProductionBatchId,
        componentId: ProductionBatchComponentId
    )

    suspend fun calculatePostingPreview(
        batchId: ProductionBatchId
    ): ProductionBatchPostingPreview

    suspend fun deleteDraft(
        batchId: ProductionBatchId
    )

    suspend fun post(
        batchId: ProductionBatchId
    )

    suspend fun void(
        batchId: ProductionBatchId
    )
}
