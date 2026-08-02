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

        // Insert comprehensive version 3 data
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.execSQL("INSERT INTO inventory_areas (id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt) VALUES ('area-1', 'rest-1', 'Area 1', 'area 1', 1, 1, 100, 200)")
        db.execSQL("INSERT INTO ingredient_categories (id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt) VALUES ('cat-1', 'rest-1', 'Cat 1', 'cat 1', 1, 1, 100, 200)")
        db.execSQL("INSERT INTO units (id, name, symbol, dimension, factorToCanonical, isSystem, sortOrder) VALUES ('u1', 'Unit', 'u', 'Mass', '1.0', 1, 1)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-1', 'rest-1', 'Ing 1', 'ing 1', 'u1', 1, 100, 200)")
        db.execSQL("INSERT INTO ingredient_unit_options (id, ingredientId, displayName, shortLabel, factorToBase, isBase, isDefaultCount, isDefaultPurchase, isActive, createdAt, updatedAt) VALUES ('opt-1', 'ing-1', 'Opt 1', 'O1', '1.0', 1, 1, 1, 1, 100, 200)")
        db.execSQL("INSERT INTO suppliers (id, restaurantId, name, normalizedName, isActive, createdAt, updatedAt) VALUES ('sup-1', 'rest-1', 'Sup 1', 'sup 1', 1, 100, 200)")
        db.execSQL("INSERT INTO purchase_receipts (id, restaurantId, supplierId, purchaseDate, status, createdAt, updatedAt) VALUES ('pr-1', 'rest-1', 'sup-1', 1000, 'DRAFT', 100, 200)")
        db.execSQL("INSERT INTO purchase_lines (id, purchaseReceiptId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, unitCostBase, lineTotal, createdAt, updatedAt) VALUES ('pl-1', 'pr-1', 'ing-1', 'area-1', 'opt-1', '1.0', '1.0', '10.0', '10.0', 100, 200)")
        db.execSQL("INSERT INTO stock_counts (id, restaurantId, name, startedAt, effectiveAt, status, createdAt, updatedAt) VALUES ('sc-1', 'rest-1', 'Count 1', 1000, 1000, 'DRAFT', 100, 200)")
        db.execSQL("INSERT INTO waste_events (id, restaurantId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, reason, effectiveAt, status, createdAt, updatedAt) VALUES ('w-1', 'rest-1', 'ing-1', 'area-1', 'opt-1', '1.0', '1.0', 'EXPIRED', 1000, 'DRAFT', 100, 200)")
        db.execSQL("INSERT INTO inventory_movements (id, restaurantId, ingredientId, areaId, movementType, quantityBaseSigned, effectiveAt, sourceDocumentType, sourceDocumentId, sourceOperationId, createdAt) VALUES ('move-1', 'rest-1', 'ing-1', 'area-1', 'PURCHASE', '1.0', 1000, 'PURCHASE_RECEIPT', 'pr-1', 'op-1', 100)")
        db.execSQL("INSERT INTO inventory_balance_projections (restaurantId, ingredientId, areaId, quantityBase, updatedAt) VALUES ('rest-1', 'ing-1', 'area-1', '1.0', 1000)")
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('rest-1', 'ing-1', '10.0', 1000)")
        db.execSQL("INSERT INTO preparation_recipes (id, restaurantId, outputIngredientId, name, normalizedName, status, createdAt, updatedAt) VALUES ('rec-1', 'rest-1', 'ing-1', 'Recipe 1', 'recipe 1', 'ACTIVE', 100, 200)")
        db.execSQL("INSERT INTO preparation_recipe_components (id, recipeId, componentIngredientId, unitOptionId, quantityEntered, quantityBase, sortOrder, createdAt, updatedAt) VALUES ('comp-1', 'rec-1', 'ing-1', 'opt-1', '1.0', '1.0', 0, 100, 200)")

        db.close()

        // 2. Run migration 3 to 4
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, RestaurantInventoryDatabase.MIGRATION_3_4)

        // Verify representative data is preserved
        val tablesToVerify = listOf(
            "restaurants", "inventory_areas", "ingredient_categories", "ingredients", 
            "ingredient_unit_options", "suppliers", "purchase_receipts", "purchase_lines", 
            "stock_counts", "waste_events", "inventory_movements", "inventory_balance_projections",
            "ingredient_cost_projection", "preparation_recipes", "preparation_recipe_components"
        )
        for (table in tablesToVerify) {
            val cursor = db.query("SELECT * FROM $table")
            assertThat(cursor.count).isAtLeast(1)
            cursor.close()
        }

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
