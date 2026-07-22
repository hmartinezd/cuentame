package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.model.inventory.InventoryMovement
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import java.time.Instant

fun InventoryMovementEntity.toDomain(): InventoryMovement = InventoryMovement(
    id = InventoryMovementId(id),
    restaurantId = RestaurantId(restaurantId),
    ingredientId = IngredientId(ingredientId),
    areaId = InventoryAreaId(areaId),
    movementType = InventoryMovementType.valueOf(movementType),
    quantityBaseSigned = BigDecimal(quantityBaseSigned),
    unitCostBaseSnapshot = unitCostBaseSnapshot?.let { BigDecimal(it) },
    totalValueSnapshot = totalValueSnapshot?.let { BigDecimal(it) },
    effectiveAt = Instant.ofEpochMilli(effectiveAt),
    sourceDocumentType = SourceDocumentType.valueOf(sourceDocumentType),
    sourceDocumentId = sourceDocumentId,
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
    sourceLineId = sourceLineId,
    reversalOfMovementId = reversalOfMovementId?.value,
    createdAt = createdAt.toEpochMilli()
)
