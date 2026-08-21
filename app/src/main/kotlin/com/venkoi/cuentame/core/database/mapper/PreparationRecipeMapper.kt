package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.parsePersistedEnum
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeComponentId
import com.venkoi.cuentame.core.common.ids.PreparationRecipeId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.database.dao.RecipeSummaryRow
import com.venkoi.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.venkoi.cuentame.core.database.entity.PreparationRecipeEntity
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipe
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeComponent
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.venkoi.cuentame.core.model.ingredient.PreparationRecipeSummary
import java.time.Instant

fun PreparationRecipeEntity.toDomain(components: List<PreparationRecipeComponentEntity>): PreparationRecipe {
    return PreparationRecipe(
        id = PreparationRecipeId(id),
        restaurantId = RestaurantId(restaurantId),
        outputIngredientId = IngredientId(outputIngredientId),
        name = name,
        standardYieldQuantity = standardYieldQuantity,
        standardYieldQuantityBase = standardYieldQuantityBase,
        yieldUnitOptionId = yieldUnitOptionId?.let { IngredientUnitOptionId(it) },
        status = parsePersistedEnum(status, PreparationRecipeStatus.UNKNOWN),
        notes = notes,
        components = components.map { it.toDomain() },
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        archivedAt = archivedAt?.let { Instant.ofEpochMilli(it) }
    )
}

fun PreparationRecipeComponentEntity.toDomain(): PreparationRecipeComponent {
    return PreparationRecipeComponent(
        id = PreparationRecipeComponentId(id),
        recipeId = PreparationRecipeId(recipeId),
        componentIngredientId = IngredientId(componentIngredientId),
        unitOptionId = IngredientUnitOptionId(unitOptionId),
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        sortOrder = sortOrder,
        notes = notes
    )
}

fun RecipeSummaryRow.toDomain(): PreparationRecipeSummary {
    return PreparationRecipeSummary(
        id = PreparationRecipeId(id),
        outputIngredientId = IngredientId(outputIngredientId),
        outputIngredientName = outputIngredientName,
        recipeName = recipeName,
        status = parsePersistedEnum(status, PreparationRecipeStatus.UNKNOWN),
        standardYieldQuantity = standardYieldQuantity,
        yieldUnitLabel = yieldUnitLabel,
        componentCount = componentCount,
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}

fun PreparationRecipe.toEntity(): PreparationRecipeEntity {
    return PreparationRecipeEntity(
        id = id.value,
        restaurantId = restaurantId.value,
        outputIngredientId = outputIngredientId.value,
        name = name,
        normalizedName = "", // Should be set by repository using normalization logic
        standardYieldQuantity = standardYieldQuantity,
        standardYieldQuantityBase = standardYieldQuantityBase,
        yieldUnitOptionId = yieldUnitOptionId?.value,
        status = status.name,
        notes = notes,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        archivedAt = archivedAt?.toEpochMilli()
    )
}

fun PreparationRecipeComponent.toEntity(createdAt: Long, updatedAt: Long): PreparationRecipeComponentEntity {
    return PreparationRecipeComponentEntity(
        id = id.value,
        recipeId = recipeId.value,
        componentIngredientId = componentIngredientId.value,
        unitOptionId = unitOptionId.value,
        quantityEntered = quantityEntered,
        quantityBase = quantityBase,
        sortOrder = sortOrder,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
