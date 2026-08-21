package com.venkoi.cuentame.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.venkoi.cuentame.core.model.supplier.SupplierItemMappingKeyType

@Entity(
    tableName = "supplier_item_mappings",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantEntity::class,
            parentColumns = ["id"],
            childColumns = ["restaurantId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
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
        )
    ],
    indices = [
        Index("restaurantId"),
        Index("supplierId"),
        Index("ingredientId"),
        Index("unitOptionId"),
        Index("inventoryAreaId"),
        Index(
            value = ["restaurantId", "supplierId", "keyType", "normalizedKey"],
            unique = true
        )
    ]
)
data class SupplierItemMappingEntity(
    @PrimaryKey val id: String,
    val restaurantId: String,
    val supplierId: String,
    val keyType: SupplierItemMappingKeyType,
    val normalizedKey: String,
    val sourceVendorCode: String?,
    val sourceDescription: String?,
    val sourcePackageText: String?,
    val ingredientId: String,
    val unitOptionId: String?,
    val inventoryAreaId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastConfirmedAt: Long
)
