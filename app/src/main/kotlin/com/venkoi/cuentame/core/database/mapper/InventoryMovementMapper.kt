package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.parsePersistedEnum
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.InventoryMovementId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.model.inventory.InventoryMovement
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
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
