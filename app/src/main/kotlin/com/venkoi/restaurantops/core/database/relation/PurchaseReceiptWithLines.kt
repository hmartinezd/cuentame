package com.venkoi.restaurantops.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.venkoi.restaurantops.core.database.entity.PurchaseLineEntity
import com.venkoi.restaurantops.core.database.entity.PurchaseReceiptEntity

data class PurchaseReceiptWithLines(
    @Embedded val receipt: PurchaseReceiptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "purchaseReceiptId"
    )
    val lines: List<PurchaseLineEntity>
)
