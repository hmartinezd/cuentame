package com.miara.cuentame.test

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import java.math.BigDecimal

object TestSeeder {
    const val RESTAURANT_ID = "restaurant-test-1"
    const val AREA_ID = "area-test-1"
    const val UNIT_ID = "unit-test-1"
    const val ING_ID = "ing-test-1"
    const val OPTION_ID = "opt-test-1"

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
            UnitEntity(UNIT_ID, "Pound", "lb", "MASS", BigDecimal.ONE, true, 0)
        ))
        
        db.inventoryAreaDao().upsert(
            InventoryAreaEntity(AREA_ID, RESTAURANT_ID, "Storage", "storage", 0, true, 0L, 0L, null)
        )
        
        db.ingredientDao().insert(
            IngredientEntity(ING_ID, RESTAURANT_ID, "Chicken", "chicken", null, UNIT_ID, AREA_ID, null, null, null, true, 0L, 0L, null)
        )
        
        db.ingredientUnitOptionDao().insert(
            IngredientUnitOptionEntity(OPTION_ID, ING_ID, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null)
        )
    }

    suspend fun seedPostedPurchase(db: RestaurantInventoryDatabase, receiptId: String, amount: String) {
        val now = System.currentTimeMillis()
        db.purchaseDao().insertReceipt(
            PurchaseReceiptEntity(receiptId, RESTAURANT_ID, null, "INV-1", now, DocumentStatus.POSTED.name, null, null, now, now, now, null)
        )
        db.purchaseDao().insertLine(
            PurchaseLineEntity("line-$receiptId", receiptId, ING_ID, AREA_ID, OPTION_ID, "1", "1", amount, amount, null, now, now)
        )
        // Note: For real repository tests, we call repo.post(). This seeder is for UI tests that need data.
    }
}
