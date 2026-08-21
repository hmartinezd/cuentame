package com.venkoi.restaurantops.core.database.mapper

import com.venkoi.restaurantops.core.common.parsePersistedEnum
import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.InventoryMovementId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.entity.InventoryMovementEntity
import com.venkoi.restaurantops.core.model.inventory.InventoryMovement
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementType
import com.venkoi.restaurantops.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import java.time.Instant

fun InventoryMovementEntity.toDomain(): InventoryMovement = InventoryMovement(
    id = InventoryMovementId(id),
    restaurantId = RestaurantId(restaurantId),
    ingredientId = IngredientId(ingredientId),
    areaId = InventoryAreaId(areaId),
    movementType = parsePersistedEnum(movementType, InventoryMovementType.UNKNOWN),
    quantityBaseSigned = BigDecimal(quantityBaseSigned),
    unitCostBaseSnapshot = unitCostBaseSnapshot?.let { BigDecimal(it) },
    totalValueSnapshot = totalValueSnapshot?.let { BigDecimal(it) },
    effectiveAt = Instant.ofEpochMilli(effectiveAt),
    sourceDocumentType = parsePersistedEnum(sourceDocumentType, SourceDocumentType.UNKNOWN),
    sourceDocumentId = sourceDocumentId,
    sourceOperationId = sourceOperationId,
    sourceLineId = sourceLineId,
    reversalOfMovementId = reversalOfMovementId?.let { InventoryMovementId(it) },
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun InventoryMovement.toEntity(): InventoryMovementEntity = InventoryMovementEntity(
    id = id.value,
    restaurantId = restaurantId.value,
    ingredientId = ingredientId.value,
    areaId = areaId.value,
    movementType = movementType.name,
    quantityBaseSigned = quantityBaseSigned.toPlainString(),
    unitCostBaseSnapshot = unitCostBaseSnapshot?.toPlainString(),
    totalValueSnapshot = totalValueSnapshot?.toPlainString(),
    effectiveAt = effectiveAt.toEpochMilli(),
    sourceDocumentType = sourceDocumentType.name,
    sourceDocumentId = sourceDocumentId,
    sourceOperationId = sourceOperationId,
    sourceLineId = sourceLineId,
    reversalOfMovementId = reversalOfMovementId?.value,
    createdAt = createdAt.toEpochMilli()
)
