package com.venkoi.restaurantops.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.venkoi.restaurantops.core.model.purchase.InvoiceLineMatchStatus

@Entity(
    tableName = "purchase_invoice_line_matches",
    primaryKeys = ["parseResultId", "lineIndex"],
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoiceParseResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["parseResultId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = IngredientUnitOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitOptionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InventoryAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryAreaId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SupplierItemMappingEntity::class,
            parentColumns = ["id"],
            childColumns = ["mappingId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("parseResultId"),
        Index("supplierId"),
        Index("ingredientId"),
        Index("unitOptionId"),
        Index("inventoryAreaId"),
        Index("mappingId")
    ]
)
data class PurchaseInvoiceLineMatchEntity(
    val parseResultId: String,
    val lineIndex: Int,
    val status: InvoiceLineMatchStatus,
    val supplierId: String?,
    val ingredientId: String?,
    val unitOptionId: String?,
    val inventoryAreaId: String?,
    val mappingId: String?,
    val matchMethod: String?,
    val matchConfidence: Float,
    val confirmedAt: Long?
)
