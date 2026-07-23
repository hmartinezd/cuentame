package com.miara.cuentame.core.database.mapper

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import java.time.Instant

fun PurchaseReceiptEntity.toDomain(): PurchaseReceipt = PurchaseReceipt(
    id = PurchaseReceiptId(id),
    restaurantId = RestaurantId(restaurantId),
    supplierId = supplierId?.let { SupplierId(it) },
    invoiceNumber = invoiceNumber,
    purchaseDate = Instant.ofEpochMilli(purchaseDate),
    status = DocumentStatus.valueOf(status),
    notes = notes,
    attachmentPath = attachmentPath,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    postedAt = postedAt?.let { Instant.ofEpochMilli(it) },
    voidedAt = voidedAt?.let { Instant.ofEpochMilli(it) }
)

fun PurchaseReceipt.toEntity(): PurchaseReceiptEntity = PurchaseReceiptEntity(
    id = id.value,
    restaurantId = restaurantId.value,
    supplierId = supplierId?.value,
    invoiceNumber = invoiceNumber,
    purchaseDate = purchaseDate.toEpochMilli(),
    status = status.name,
    notes = notes,
    attachmentPath = attachmentPath,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    postedAt = postedAt?.toEpochMilli(),
    voidedAt = voidedAt?.toEpochMilli()
)
