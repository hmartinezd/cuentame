package com.miara.cuentame.core.model.inventory

import com.miara.cuentame.core.common.ids.*
import java.math.BigDecimal
import java.time.Instant

data class ProductionBatch(
    val id: ProductionBatchId,
    val restaurantId: RestaurantId,
    val recipeId: PreparationRecipeId,
    val recipeNameSnapshot: String,
    val outputIngredientId: IngredientId,

    val batchMultiplier: BigDecimal,

    val recipeStandardYieldQuantitySnapshot: BigDecimal,
    val recipeStandardYieldBaseSnapshot: BigDecimal,
    val recipeYieldUnitOptionIdSnapshot: IngredientUnitOptionId,

    val expectedOutputQuantityEntered: BigDecimal,
    val expectedOutputQuantityBase: BigDecimal,

    val actualOutputQuantityEntered: BigDecimal,
    val actualOutputQuantityBase: BigDecimal,
    val outputUnitOptionId: IngredientUnitOptionId,
    val outputAreaId: InventoryAreaId,
    val hasManualOutputQuantityOverride: Boolean,

    val totalComponentCostSnapshot: BigDecimal?,
    val outputUnitCostBaseSnapshot: BigDecimal?,

    val effectiveAt: Instant,
    val status: DocumentStatus,
    val notes: String?,

    val components: List<ProductionBatchComponent>,

    val createdAt: Instant,
    val updatedAt: Instant,
    val postedAt: Instant?,
    val voidedAt: Instant?
)

data class ProductionBatchSummary(
    val id: ProductionBatchId,
    val recipeName: String,
    val outputIngredientName: String,
    val status: DocumentStatus,
    val expectedOutputQuantityEntered: BigDecimal,
    val actualOutputQuantityEntered: BigDecimal,
    val outputUnitLabel: String,
    val componentCount: Int,
    val totalComponentCost: BigDecimal?,
    val effectiveAt: Instant
)

data class ProductionBatchComponent(
    val id: ProductionBatchComponentId,
    val productionBatchId: ProductionBatchId,

    val sourceRecipeComponentIdSnapshot: String,
    val componentIngredientId: IngredientId,

    val recipeQuantityEnteredSnapshot: BigDecimal,
    val recipeQuantityBaseSnapshot: BigDecimal,
    val recipeUnitOptionIdSnapshot: IngredientUnitOptionId,

    val expectedQuantityEntered: BigDecimal,
    val expectedQuantityBase: BigDecimal,

    val actualQuantityEntered: BigDecimal,
    val actualQuantityBase: BigDecimal,
    val unitOptionId: IngredientUnitOptionId,
    val hasManualQuantityOverride: Boolean,

    val sourceAreaId: InventoryAreaId?,

    val unitCostBaseSnapshot: BigDecimal?,
    val totalCostSnapshot: BigDecimal?,

    val sortOrder: Int,
    val notes: String?,

    val createdAt: Instant,
    val updatedAt: Instant
)
