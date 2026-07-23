package com.miara.cuentame.core.database.factory

import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import java.time.Instant

object TestFactories {
    fun createRestaurant(id: String = "rest_1", name: String = "Test Restaurant") = RestaurantEntity(
        id = id,
        name = name,
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.now().toEpochMilli(),
        updatedAt = Instant.now().toEpochMilli(),
        deletedAt = null
    )

    fun createArea(id: String = "area_1", restaurantId: String = "rest_1", name: String = "Kitchen") = InventoryAreaEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = name.lowercase(),
        sortOrder = 0,
        isActive = true,
        createdAt = Instant.now().toEpochMilli(),
        updatedAt = Instant.now().toEpochMilli(),
        deletedAt = null
    )

    fun createIngredient(
        id: String = "ing_1",
        restaurantId: String = "rest_1",
        name: String = "Chicken",
        baseUnitId: String = "mass_lb"
    ) = IngredientEntity(
        id = id,
        restaurantId = restaurantId,
        name = name,
        normalizedName = name.lowercase(),
        categoryId = null,
        baseUnitId = baseUnitId,
        defaultAreaId = null,
        sku = null,
        notes = null,
        reorderPointBase = null,
        isActive = true,
        createdAt = Instant.now().toEpochMilli(),
        updatedAt = Instant.now().toEpochMilli(),
        deletedAt = null
    )
}
