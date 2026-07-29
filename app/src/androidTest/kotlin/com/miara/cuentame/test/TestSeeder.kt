package com.miara.cuentame.test

import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import java.math.BigDecimal

object TestSeeder {
    const val RESTAURANT_ID = "restaurant-test-1"

    suspend fun seedBaseline(db: RestaurantInventoryDatabase) {
        db.restaurantDao().insert(
            RestaurantEntity(
                id = RESTAURANT_ID,
                name = "Test Restaurant",
                currencyCode = "USD",
                localeTag = "en-US",
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
        
        db.unitDao().insertSeedUnits(listOf(
            UnitEntity("mass_lb", "Pound", "lb", "MASS", BigDecimal.ONE, true, 0),
            UnitEntity("mass_oz", "Ounce", "oz", "MASS", BigDecimal("0.0625"), true, 1)
        ))
    }
}
