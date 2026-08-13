package com.miara.cuentame.core.database.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

        // 2. Run migration 1 to 12
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, *RestaurantInventoryDatabase.ALL_MIGRATIONS)

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
        ).addMigrations(
            RestaurantInventoryDatabase.MIGRATION_1_2,
            RestaurantInventoryDatabase.MIGRATION_2_3,
            RestaurantInventoryDatabase.MIGRATION_3_4,
            RestaurantInventoryDatabase.MIGRATION_4_5,
            RestaurantInventoryDatabase.MIGRATION_5_6,
            RestaurantInventoryDatabase.MIGRATION_6_7,
            RestaurantInventoryDatabase.MIGRATION_7_8,
            RestaurantInventoryDatabase.MIGRATION_8_9,
            RestaurantInventoryDatabase.MIGRATION_9_10,
            RestaurantInventoryDatabase.MIGRATION_10_11,
            RestaurantInventoryDatabase.MIGRATION_11_12,
            RestaurantInventoryDatabase.MIGRATION_12_13
        ).build()

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
    @Throws(IOException::class)
    fun migrate2To3_preservesRepresentativeData_andCreatesNewTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Create version 2 database
        var db = helper.createDatabase(TEST_DB, 2)

        // Insert representative data in version 2
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
        db.execSQL("INSERT INTO ingredient_cost_projection (restaurantId, ingredientId, averageUnitCostBase, updatedAt) VALUES ('rest-1', 'ing-1', '10.0', 1000)")
        
        db.close()

        // 2. Run migration 2 to 12
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, *RestaurantInventoryDatabase.ALL_MIGRATIONS)

        // Verify representative data is preserved
        val tablesToVerify = listOf(
            "restaurants", "inventory_areas", "ingredient_categories", "ingredients", 
            "ingredient_unit_options", "suppliers", "purchase_receipts", "purchase_lines", 
            "stock_counts", "waste_events", "inventory_movements", "ingredient_cost_projection"
        )
        for (table in tablesToVerify) {
            val cursor = db.query("SELECT * FROM $table")
            assertWithMessage("Table $table count").that(cursor.count).isAtLeast(1)
            cursor.close()
        }

        // Verify new tables exist and are empty
        val recipeCursor = db.query("SELECT * FROM preparation_recipes")
        assertThat(recipeCursor.count).isEqualTo(0)
        recipeCursor.close()

        val componentCursor = db.query("SELECT * FROM preparation_recipe_components")
        assertThat(componentCursor.count).isEqualTo(0)
        componentCursor.close()

        db.close()

        // 3. Reopen through Room
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).addMigrations(
            RestaurantInventoryDatabase.MIGRATION_1_2,
            RestaurantInventoryDatabase.MIGRATION_2_3,
            RestaurantInventoryDatabase.MIGRATION_3_4,
            RestaurantInventoryDatabase.MIGRATION_4_5,
            RestaurantInventoryDatabase.MIGRATION_5_6,
            RestaurantInventoryDatabase.MIGRATION_6_7,
            RestaurantInventoryDatabase.MIGRATION_7_8,
            RestaurantInventoryDatabase.MIGRATION_8_9,
            RestaurantInventoryDatabase.MIGRATION_9_10,
            RestaurantInventoryDatabase.MIGRATION_10_11,
            RestaurantInventoryDatabase.MIGRATION_11_12,
            RestaurantInventoryDatabase.MIGRATION_12_13
        ).build()

        assertThat(roomDb.ingredientDao().getActiveIngredients("rest-1")).hasSize(1)
        roomDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_createsProductionTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Create version 3 database
        var db = helper.createDatabase(TEST_DB, 3)
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.close()

        // 2. Run migration 3 to 12
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, *RestaurantInventoryDatabase.ALL_MIGRATIONS)
        
        // Verify new tables exist
        val batchCursor = db.query("SELECT * FROM production_batches")
        assertThat(batchCursor.count).isEqualTo(0)
        batchCursor.close()
        
        db.close()

        // 3. Reopen through Room
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).addMigrations(
            RestaurantInventoryDatabase.MIGRATION_1_2,
            RestaurantInventoryDatabase.MIGRATION_2_3,
            RestaurantInventoryDatabase.MIGRATION_3_4,
            RestaurantInventoryDatabase.MIGRATION_4_5,
            RestaurantInventoryDatabase.MIGRATION_5_6,
            RestaurantInventoryDatabase.MIGRATION_6_7,
            RestaurantInventoryDatabase.MIGRATION_7_8,
            RestaurantInventoryDatabase.MIGRATION_8_9,
            RestaurantInventoryDatabase.MIGRATION_9_10,
            RestaurantInventoryDatabase.MIGRATION_10_11,
            RestaurantInventoryDatabase.MIGRATION_11_12,
            RestaurantInventoryDatabase.MIGRATION_12_13
        ).build()

        assertThat(roomDb.productionBatchDao().getById("non-existent")).isNull()
        roomDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5_addsAttachmentDisplayNameColumns() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Create version 4 database
        var db = helper.createDatabase(TEST_DB, 4)
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.execSQL("INSERT INTO suppliers (id, restaurantId, name, normalizedName, isActive, createdAt, updatedAt) VALUES ('sup-1', 'rest-1', 'Sup 1', 'sup 1', 1, 100, 200)")
        db.execSQL("INSERT INTO purchase_receipts (id, restaurantId, supplierId, purchaseDate, status, attachmentPath, createdAt, updatedAt) VALUES ('pr-1', 'rest-1', 'sup-1', 1000, 'DRAFT', 'att-1', 100, 200)")
        
        db.execSQL("INSERT INTO inventory_areas (id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt) VALUES ('area-1', 'rest-1', 'Area 1', 'area 1', 1, 1, 100, 200)")
        db.execSQL("INSERT INTO units (id, name, symbol, dimension, factorToCanonical, isSystem, sortOrder) VALUES ('u1', 'Unit', 'u', 'Mass', '1.0', 1, 1)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-1', 'rest-1', 'Ing 1', 'ing 1', 'u1', 1, 100, 200)")
        db.execSQL("INSERT INTO ingredient_unit_options (id, ingredientId, displayName, shortLabel, factorToBase, isBase, isDefaultCount, isDefaultPurchase, isActive, createdAt, updatedAt) VALUES ('opt-1', 'ing-1', 'Opt 1', 'O1', '1.0', 1, 1, 1, 1, 100, 200)")
        db.execSQL("INSERT INTO waste_events (id, restaurantId, ingredientId, areaId, ingredientUnitOptionId, quantityEntered, quantityBase, reason, effectiveAt, status, attachmentPath, createdAt, updatedAt) VALUES ('w-1', 'rest-1', 'ing-1', 'area-1', 'opt-1', '1.0', '1.0', 'EXPIRED', 1000, 'DRAFT', 'att-2', 100, 200)")
        db.close()

        // 2. Run migration 4 to 12
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, *RestaurantInventoryDatabase.ALL_MIGRATIONS)

        // Verify columns exist and are null
        val prCursor = db.query("SELECT * FROM purchase_receipts WHERE id = 'pr-1'")
        assertThat(prCursor.moveToFirst()).isTrue()
        assertThat(prCursor.isNull(prCursor.getColumnIndexOrThrow("attachmentDisplayName"))).isTrue()
        assertThat(prCursor.getString(prCursor.getColumnIndexOrThrow("attachmentPath"))).isEqualTo("att-1")
        prCursor.close()

        val wCursor = db.query("SELECT * FROM waste_events WHERE id = 'w-1'")
        assertThat(wCursor.moveToFirst()).isTrue()
        assertThat(wCursor.isNull(wCursor.getColumnIndexOrThrow("attachmentDisplayName"))).isTrue()
        assertThat(wCursor.getString(wCursor.getColumnIndexOrThrow("attachmentPath"))).isEqualTo("att-2")
        wCursor.close()

        db.close()

        // 3. Reopen through Room
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).addMigrations(
            RestaurantInventoryDatabase.MIGRATION_1_2,
            RestaurantInventoryDatabase.MIGRATION_2_3,
            RestaurantInventoryDatabase.MIGRATION_3_4,
            RestaurantInventoryDatabase.MIGRATION_4_5,
            RestaurantInventoryDatabase.MIGRATION_5_6,
            RestaurantInventoryDatabase.MIGRATION_6_7,
            RestaurantInventoryDatabase.MIGRATION_7_8,
            RestaurantInventoryDatabase.MIGRATION_8_9,
            RestaurantInventoryDatabase.MIGRATION_9_10,
            RestaurantInventoryDatabase.MIGRATION_10_11,
            RestaurantInventoryDatabase.MIGRATION_11_12,
            RestaurantInventoryDatabase.MIGRATION_12_13
        ).build()

        val pr = roomDb.purchaseDao().getReceiptById("pr-1")
        assertThat(pr?.attachmentDisplayName).isEqualTo(null)
        
        roomDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6_createsOcrTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Create version 5 database
        var db = helper.createDatabase(TEST_DB, 5)
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.close()

        // 2. Run migration 5 to 12
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, *RestaurantInventoryDatabase.ALL_MIGRATIONS)
        
        // Verify new tables exist
        val ocrResultCursor = db.query("SELECT * FROM purchase_invoice_ocr_results")
        assertThat(ocrResultCursor.count).isEqualTo(0)
        ocrResultCursor.close()
        
        val ocrPageCursor = db.query("SELECT * FROM purchase_invoice_ocr_pages")
        assertThat(ocrPageCursor.count).isEqualTo(0)
        ocrPageCursor.close()
        
        db.close()

        // 3. Reopen through Room
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).addMigrations(
            RestaurantInventoryDatabase.MIGRATION_1_2,
            RestaurantInventoryDatabase.MIGRATION_2_3,
            RestaurantInventoryDatabase.MIGRATION_3_4,
            RestaurantInventoryDatabase.MIGRATION_4_5,
            RestaurantInventoryDatabase.MIGRATION_5_6,
            RestaurantInventoryDatabase.MIGRATION_6_7,
            RestaurantInventoryDatabase.MIGRATION_7_8,
            RestaurantInventoryDatabase.MIGRATION_8_9,
            RestaurantInventoryDatabase.MIGRATION_9_10,
            RestaurantInventoryDatabase.MIGRATION_10_11,
            RestaurantInventoryDatabase.MIGRATION_11_12,
            RestaurantInventoryDatabase.MIGRATION_12_13
        ).build()

        assertThat(roomDb.purchaseOcrDao().getOcrResultForReceiptSync("non-existent")).isNull()
        roomDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_createsParseTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Create version 6 database
        var db = helper.createDatabase(TEST_DB, 6)
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.close()

        // 2. Run migration 6 to 12
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, *RestaurantInventoryDatabase.ALL_MIGRATIONS)
        
        // Verify new tables exist
        val parseResultCursor = db.query("SELECT * FROM purchase_invoice_parse_results")
        assertThat(parseResultCursor.count).isEqualTo(0)
        parseResultCursor.close()
        
        val parsedLineCursor = db.query("SELECT * FROM purchase_invoice_parsed_lines")
        assertThat(parsedLineCursor.count).isEqualTo(0)
        parsedLineCursor.close()
        
        db.close()

        // 3. Reopen through Room
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).addMigrations(
            RestaurantInventoryDatabase.MIGRATION_1_2,
            RestaurantInventoryDatabase.MIGRATION_2_3,
            RestaurantInventoryDatabase.MIGRATION_3_4,
            RestaurantInventoryDatabase.MIGRATION_4_5,
            RestaurantInventoryDatabase.MIGRATION_5_6,
            RestaurantInventoryDatabase.MIGRATION_6_7,
            RestaurantInventoryDatabase.MIGRATION_7_8,
            RestaurantInventoryDatabase.MIGRATION_8_9,
            RestaurantInventoryDatabase.MIGRATION_9_10,
            RestaurantInventoryDatabase.MIGRATION_10_11,
            RestaurantInventoryDatabase.MIGRATION_11_12,
            RestaurantInventoryDatabase.MIGRATION_12_13
        ).build()

        assertThat(roomDb.purchaseParseDao().getParseResultForReceipt("non-existent")).isNull()
        roomDb.close()
    }

    @Test
    fun createDatabaseDirectlyAtVersion7() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.databaseBuilder(
            context,
            RestaurantInventoryDatabase::class.java,
            TEST_DB
        ).build()

        assertThat(roomDb.purchaseParseDao().getParseResultForReceipt("non-existent")).isNull()
        roomDb.close()
    }
}
