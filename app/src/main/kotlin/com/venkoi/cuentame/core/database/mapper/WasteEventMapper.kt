package com.venkoi.cuentame.core.database.mapper

import com.venkoi.cuentame.core.common.parsePersistedEnum
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.WasteEventId
import com.venkoi.cuentame.core.database.entity.WasteEventEntity
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.WasteReason
import com.venkoi.cuentame.core.model.waste.WasteEvent
import java.math.BigDecimal
import java.time.Instant

fun WasteEventEntity.toDomain(): WasteEvent = WasteEvent(
    id = WasteEventId(id),
    restaurantId = RestaurantId(restaurantId),
    ingredientId = IngredientId(ingredientId),
    areaId = InventoryAreaId(areaId),
    ingredientUnitOptionId = IngredientUnitOptionId(ingredientUnitOptionId),
    quantityEntered = BigDecimal(quantityEntered),
    quantityBase = BigDecimal(quantityBase),
    reason = parsePersistedEnum(reason, WasteReason.UNKNOWN),
    effectiveAt = Instant.ofEpochMilli(effectiveAt),
    notes = notes,
    attachmentPath = attachmentPath,
    attachmentDisplayName = attachmentDisplayName,
    status = parsePersistedEnum(status, DocumentStatus.UNKNOWN),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    postedAt = postedAt?.let { Instant.ofEpochMilli(it) },
    voidedAt = voidedAt?.let { Instant.ofEpochMilli(it) }
)

fun WasteEvent.toEntity(): WasteEventEntity = WasteEventEntity(
    id = id.value,
    restaurantId = restaurantId.value,
    ingredientId = ingredientId.value,
    areaId = areaId.value,
    ingredientUnitOptionId = ingredientUnitOptionId.value,
    quantityEntered = quantityEntered.toPlainString(),
    quantityBase = quantityBase.toPlainString(),
    reason = reason.name,
    effectiveAt = effectiveAt.toEpochMilli(),
    notes = notes,
    attachmentPath = attachmentPath,
    attachmentDisplayName = attachmentDisplayName,
    status = status.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    postedAt = postedAt?.toEpochMilli(),
    voidedAt = voidedAt?.toEpochMilli()
)
