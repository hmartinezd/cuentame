package com.miara.cuentame.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {
    private val TEST_DB = "migration-3-4-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RestaurantInventoryDatabase::class.java
    )

    @Before
    fun cleanDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_createsProductionTables_andPreservesExistingData() = runBlocking {
        // 1. Create version 3 database
        var db = helper.createDatabase(TEST_DB, 3)

        // Insert some version 3 data
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.execSQL("INSERT INTO units (id, name, symbol, dimension, factorToCanonical, isSystem, sortOrder) VALUES ('u1', 'Unit', 'u', 'Mass', '1.0', 1, 1)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-1', 'rest-1', 'Ing 1', 'ing 1', 'u1', 1, 100, 200)")
        db.execSQL("INSERT INTO preparation_recipes (id, restaurantId, outputIngredientId, name, normalizedName, status, createdAt, updatedAt) VALUES ('rec-1', 'rest-1', 'ing-1', 'Recipe 1', 'recipe 1', 'ACTIVE', 100, 200)")

        db.close()

        // 2. Run migration 3 to 4
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, RestaurantInventoryDatabase.MIGRATION_3_4)

        // Verify existing data preserved
        val recipeCursor = db.query("SELECT * FROM preparation_recipes")
        assertThat(recipeCursor.count).isEqualTo(1)
        recipeCursor.close()

        // Verify new tables exist and are empty
        val batchCursor = db.query("SELECT * FROM production_batches")
        assertThat(batchCursor.count).isEqualTo(0)
        batchCursor.close()

        val componentCursor = db.query("SELECT * FROM production_batch_components")
        assertThat(componentCursor.count).isEqualTo(0)
        componentCursor.close()

        db.close()
    }
}
