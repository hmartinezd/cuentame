package com.miara.cuentame.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val DB_NAME = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RestaurantInventoryDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        // Create the database with version 1
        var db = helper.createDatabase(DB_NAME, 1)
        
        // Insert some data in v1
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('r1', 'Rest', 'USD', 'en', 0, 0)")
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('r1', 'i1', '10.0', 0)")
        
        db.close()

        // Open/migrate to v2
        db = helper.runMigrationsAndValidate(DB_NAME, 2, true, RestaurantInventoryDatabase.MIGRATION_1_2)
        
        // Verify data preserved and table structure changed (nullable cost)
        val cursor = db.query("SELECT * FROM ingredient_cost_projection")
        assert(cursor.moveToFirst())
        assert(cursor.getString(cursor.getColumnIndex("averageUnitCostBase")) == "10.0")
        db.close()
    }
}
