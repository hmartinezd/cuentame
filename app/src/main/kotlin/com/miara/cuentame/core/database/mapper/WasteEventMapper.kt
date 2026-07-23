package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.database.entity.WasteEventEntity
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.model.waste.WasteEvent
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
    reason = WasteReason.valueOf(reason),
    effectiveAt = Instant.ofEpochMilli(effectiveAt),
    notes = notes,
    attachmentPath = attachmentPath,
    status = DocumentStatus.valueOf(status),
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
    status = status.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    postedAt = postedAt?.toEpochMilli(),
    voidedAt = voidedAt?.toEpochMilli()
)
