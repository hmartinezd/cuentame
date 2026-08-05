package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.parsePersistedEnum
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.dao.ProductionBatchSummaryRow
import com.miara.cuentame.core.database.entity.ProductionBatchComponentEntity
import com.miara.cuentame.core.database.entity.ProductionBatchEntity
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.ProductionBatch
import com.miara.cuentame.core.model.inventory.ProductionBatchComponent
import com.miara.cuentame.core.model.inventory.ProductionBatchSummary
import java.math.BigDecimal
import java.time.Instant

fun ProductionBatchEntity.toDomain(components: List<ProductionBatchComponentEntity>): ProductionBatch {
    return ProductionBatch(
        id = ProductionBatchId(id),
        restaurantId = RestaurantId(restaurantId),
        recipeId = PreparationRecipeId(recipeId),
        recipeNameSnapshot = recipeNameSnapshot,
        outputIngredientId = IngredientId(outputIngredientId),
        batchMultiplier = BigDecimal(batchMultiplier),
        recipeStandardYieldQuantitySnapshot = BigDecimal(recipeStandardYieldQuantitySnapshot),
        recipeStandardYieldBaseSnapshot = BigDecimal(recipeStandardYieldBaseSnapshot),
        recipeYieldUnitOptionIdSnapshot = IngredientUnitOptionId(recipeYieldUnitOptionIdSnapshot),
        expectedOutputQuantityEntered = BigDecimal(expectedOutputQuantityEntered),
        expectedOutputQuantityBase = BigDecimal(expectedOutputQuantityBase),
        actualOutputQuantityEntered = BigDecimal(actualOutputQuantityEntered),
        actualOutputQuantityBase = BigDecimal(actualOutputQuantityBase),
        outputUnitOptionId = IngredientUnitOptionId(outputUnitOptionId),
        outputAreaId = InventoryAreaId(outputAreaId),
        hasManualOutputQuantityOverride = hasManualOutputQuantityOverride,
        totalComponentCostSnapshot = totalComponentCostSnapshot?.let { BigDecimal(it) },
        outputUnitCostBaseSnapshot = outputUnitCostBaseSnapshot?.let { BigDecimal(it) },
        effectiveAt = Instant.ofEpochMilli(effectiveAt),
        status = parsePersistedEnum(status, DocumentStatus.UNKNOWN),
        notes = notes,
        components = components.map { it.toDomain() },
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        postedAt = postedAt?.let { Instant.ofEpochMilli(it) },
        voidedAt = voidedAt?.let { Instant.ofEpochMilli(it) }
    )
}

fun ProductionBatchComponentEntity.toDomain(): ProductionBatchComponent {
    return ProductionBatchComponent(
        id = ProductionBatchComponentId(id),
        productionBatchId = ProductionBatchId(productionBatchId),
        sourceRecipeComponentIdSnapshot = sourceRecipeComponentIdSnapshot,
        componentIngredientId = IngredientId(componentIngredientId),
        recipeQuantityEnteredSnapshot = BigDecimal(recipeQuantityEnteredSnapshot),
        recipeQuantityBaseSnapshot = BigDecimal(recipeQuantityBaseSnapshot),
        recipeUnitOptionIdSnapshot = IngredientUnitOptionId(recipeUnitOptionIdSnapshot),
        expectedQuantityEntered = BigDecimal(expectedQuantityEntered),
        expectedQuantityBase = BigDecimal(expectedQuantityBase),
        actualQuantityEntered = BigDecimal(actualQuantityEntered),
        actualQuantityBase = BigDecimal(actualQuantityBase),
        unitOptionId = IngredientUnitOptionId(unitOptionId),
        hasManualQuantityOverride = hasManualQuantityOverride,
        sourceAreaId = sourceAreaId?.let { InventoryAreaId(it) },
        unitCostBaseSnapshot = unitCostBaseSnapshot?.let { BigDecimal(it) },
        totalCostSnapshot = totalCostSnapshot?.let { BigDecimal(it) },
        sortOrder = sortOrder,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}

fun ProductionBatchSummaryRow.toDomain(): ProductionBatchSummary {
    return ProductionBatchSummary(
        id = ProductionBatchId(id),
        recipeName = recipeName,
        outputIngredientName = outputIngredientName,
        status = parsePersistedEnum(status, DocumentStatus.UNKNOWN),
        expectedOutputQuantityEntered = BigDecimal(expectedOutputQuantityEntered),
        actualOutputQuantityEntered = BigDecimal(actualOutputQuantityEntered),
        outputUnitLabel = outputUnitLabel,
        componentCount = componentCount,
        totalComponentCost = totalComponentCost?.let { BigDecimal(it) },
        effectiveAt = Instant.ofEpochMilli(effectiveAt)
    )
}
