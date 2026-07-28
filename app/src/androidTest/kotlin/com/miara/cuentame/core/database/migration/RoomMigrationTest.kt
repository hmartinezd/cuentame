package com.miara.cuentame.core.database.migration

import androidx.room.Room
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
class RoomMigrationTest {
    private val TEST_DB = "migration-test.db"

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
    fun migrate1To2_preservesData_supportsNullCost_andOpensInRoom() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Create true version 1 database
        var db = helper.createDatabase(TEST_DB, 1)

        // Insert required parent rows
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.execSQL("INSERT INTO units (id, name, symbol, dimension, factorToCanonical, isSystem, sortOrder) VALUES ('u1', 'Unit', 'u', 'Mass', '1.0', 1, 1)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-1', 'rest-1', 'Ing 1', 'ing 1', 'u1', 1, 100, 200)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-2', 'rest-1', 'Ing 2', 'ing 2', 'u1', 1, 100, 200)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-3', 'rest-1', 'Ing 3', 'ing 3', 'u1', 1, 100, 200)")

        // Insert multiple cost projections with distinct values in version 1
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('rest-1', 'ing-1', '10.50', 1000)")
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('rest-1', 'ing-2', '25.75', 2000)")

        // Confirm version 1 rows exist before migration
        val preCursor = db.query("SELECT * FROM ingredient_cost_projection ORDER BY ingredientId")
        assertThat(preCursor.count).isEqualTo(2)
        assertThat(preCursor.moveToFirst()).isTrue()
        assertThat(preCursor.getString(preCursor.getColumnIndexOrThrow("averageUnitCostBase"))).isEqualTo("10.50")
        preCursor.close()
        db.close()

        // 2. Run migration 1 to 2 with schema validation enabled
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, RestaurantInventoryDatabase.MIGRATION_1_2)

        // Assert exact migrated row count and preserved fields
        val postCursor = db.query("SELECT * FROM ingredient_cost_projection ORDER BY ingredientId")
        assertThat(postCursor.count).isEqualTo(2)

        assertThat(postCursor.moveToFirst()).isTrue()
        assertThat(postCursor.getString(postCursor.getColumnIndexOrThrow("restaurantId"))).isEqualTo("rest-1")
        assertThat(postCursor.getString(postCursor.getColumnIndexOrThrow("ingredientId"))).isEqualTo("ing-1")
        assertThat(postCursor.getString(postCursor.getColumnIndexOrThrow("averageUnitCostBase"))).isEqualTo("10.50")
        assertThat(postCursor.getLong(postCursor.getColumnIndexOrThrow("updatedAt"))).isEqualTo(1000L)

        assertThat(postCursor.moveToNext()).isTrue()
        assertThat(postCursor.getString(postCursor.getColumnIndexOrThrow("restaurantId"))).isEqualTo("rest-1")
        assertThat(postCursor.getString(postCursor.getColumnIndexOrThrow("ingredientId"))).isEqualTo("ing-2")
        assertThat(postCursor.getString(postCursor.getColumnIndexOrThrow("averageUnitCostBase"))).isEqualTo("25.75")
        assertThat(postCursor.getLong(postCursor.getColumnIndexOrThrow("updatedAt"))).isEqualTo(2000L)
        postCursor.close()

        // Insert a null averageUnitCostBase
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('rest-1', 'ing-3', NULL, 3000)")

        // Query it back and assert it is actually null
        val nullCursor = db.query("SELECT * FROM ingredient_cost_projection WHERE ingredientId = 'ing-3'")
        assertThat(nullCursor.count).isEqualTo(1)
        assertThat(nullCursor.moveToFirst()).isTrue()
        assertThat(nullCursor.isNull(nullCursor.getColumnIndexOrThrow("averageUnitCostBase"))).isTrue()
        assertThat(nullCursor.getString(nullCursor.getColumnIndexOrThrow("averageUnitCostBase"))).isNull()
        nullCursor.close()

        db.close()

        // 3. Reopen database through Room with all production migrations
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).addMigrations(RestaurantInventoryDatabase.MIGRATION_1_2)
            .build()

        // Query through real DAO to confirm migrated database opens without schema errors
        val proj1 = roomDb.ingredientCostProjectionDao().getCost("ing-1")
        assertThat(proj1).isNotNull()
        assertThat(proj1?.averageUnitCostBase).isEqualTo("10.50")

        val proj3 = roomDb.ingredientCostProjectionDao().getCost("ing-3")
        assertThat(proj3).isNotNull()
        assertThat(proj3?.averageUnitCostBase).isNull()

        roomDb.close()
    }

    @Test
    fun createDatabaseDirectlyAtVersion2() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).build()

        // Verify clean open and DAO query on new DB at version 2
        val proj = roomDb.ingredientCostProjectionDao().getCost("non-existent")
        assertThat(proj).isNull()
        roomDb.close()
    }
}
