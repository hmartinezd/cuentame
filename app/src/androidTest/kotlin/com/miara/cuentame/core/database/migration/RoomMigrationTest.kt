package com.miara.cuentame.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RestaurantInventoryDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1)

        // Insert data in version 1
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 0, 0)")
        db.execSQL("INSERT INTO units (id, name, symbol, dimension, factorToCanonical, isSystem, sortOrder) VALUES ('u1', 'Unit', 'u', 'Mass', '1.0', 1, 1)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-1', 'rest-1', 'Ing 1', 'ing 1', 'u1', 1, 0, 0)")
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('rest-1', 'ing-1', '10.0', 0)")

        db.close()

        // Re-open with migration to 2
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, RestaurantInventoryDatabase.MIGRATION_1_2)

        // Verify data
        val cursor = db.query("SELECT * FROM ingredient_cost_projection")
        if (cursor.moveToFirst()) {
            val restId = cursor.getString(cursor.getColumnIndex("restaurantId"))
            val cost = cursor.getString(cursor.getColumnIndex("averageUnitCostBase"))
            assert(restId == "rest-1")
            assert(cost == "10.0")
        }
        cursor.close()
        
        // Verify nullability - insert null in migrated db
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('rest-1', 'ing-2', NULL, 1)")
        
        db.close()
    }
}
