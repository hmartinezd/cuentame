package com.venkoi.cuentame.core.database.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {
    private val TEST_DB = "migration-7-8-test.db"

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
    fun migrate7To8_createsMappingAndMatchTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Create version 7 database
        var db = helper.createDatabase(TEST_DB, 7)
        
        // Insert representative data
        db.execSQL("INSERT INTO restaurants (id, name, currencyCode, localeTag, createdAt, updatedAt) VALUES ('rest-1', 'Rest 1', 'USD', 'en-US', 100, 200)")
        db.execSQL("INSERT INTO inventory_areas (id, restaurantId, name, normalizedName, sortOrder, isActive, createdAt, updatedAt) VALUES ('area-1', 'rest-1', 'Area 1', 'area 1', 1, 1, 100, 200)")
        db.execSQL("INSERT INTO units (id, name, symbol, dimension, factorToCanonical, isSystem, sortOrder) VALUES ('u1', 'Unit', 'u', 'Mass', '1.0', 1, 1)")
        db.execSQL("INSERT INTO ingredients (id, restaurantId, name, normalizedName, baseUnitId, isActive, createdAt, updatedAt) VALUES ('ing-1', 'rest-1', 'Ing 1', 'ing 1', 'u1', 1, 100, 200)")
        db.execSQL("INSERT INTO ingredient_unit_options (id, ingredientId, displayName, shortLabel, factorToBase, isBase, isDefaultCount, isDefaultPurchase, isActive, createdAt, updatedAt) VALUES ('opt-1', 'ing-1', 'Opt 1', 'O1', '1.0', 1, 1, 1, 1, 100, 200)")
        db.execSQL("INSERT INTO suppliers (id, restaurantId, name, normalizedName, isActive, createdAt, updatedAt) VALUES ('sup-1', 'rest-1', 'Sup 1', 'sup 1', 1, 100, 200)")
        
        // Purchase & OCR & Parse
        db.execSQL("INSERT INTO purchase_receipts (id, restaurantId, supplierId, purchaseDate, status, createdAt, updatedAt) VALUES ('pr-1', 'rest-1', 'sup-1', 1000, 'DRAFT', 100, 200)")
        db.execSQL("INSERT INTO purchase_invoice_ocr_results (id, purchaseReceiptId, sourceDocumentSha256, sourceMimeType, engine, evidenceSchemaVersion, pageCount, fullText, processedAt) VALUES ('ocr-1', 'pr-1', 'sha256', 'application/pdf', 'MLKIT', 1, 1, 'Full Text', 1000)")
        db.execSQL("INSERT INTO purchase_invoice_parse_results (id, purchaseReceiptId, ocrResultId, sourceDocumentSha256, parserEngine, parserSchemaVersion, headerEvidenceJson, totalsEvidenceJson, warningsJson, processedAt) VALUES ('parse-1', 'pr-1', 'ocr-1', 'sha256', 'ENGINE', 1, '{}', '{}', '[]', 1000)")
        
        db.close()

        // 2. Run migration 7 to 12
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, *RestaurantInventoryDatabase.ALL_MIGRATIONS)
        
        // Verify new tables exist and constraints work
        val mappingCursor = db.query("SELECT * FROM supplier_item_mappings")
        assertThat(mappingCursor.count).isEqualTo(0)
        mappingCursor.close()
        
        val matchCursor = db.query("SELECT * FROM purchase_invoice_line_matches")
        assertThat(matchCursor.count).isEqualTo(0)
        matchCursor.close()
        
        // Insert representative rows to verify columns and foreign keys
        db.execSQL("INSERT INTO supplier_item_mappings (id, restaurantId, supplierId, keyType, normalizedKey, ingredientId, unitOptionId, inventoryAreaId, createdAt, updatedAt, lastConfirmedAt) VALUES ('map-1', 'rest-1', 'sup-1', 'VENDOR_CODE', '001234', 'ing-1', 'opt-1', 'area-1', 100, 100, 100)")
        
        db.execSQL("INSERT INTO purchase_invoice_line_matches (parseResultId, lineIndex, status, supplierId, ingredientId, unitOptionId, inventoryAreaId, mappingId, matchMethod, matchConfidence, confirmedAt) VALUES ('parse-1', 0, 'CONFIRMED', 'sup-1', 'ing-1', 'opt-1', 'area-1', 'map-1', 'KnownSupplierItem', 1.0, 1000)")
        
        val mapCheck = db.query("SELECT * FROM supplier_item_mappings WHERE id = 'map-1'")
        assertThat(mapCheck.count).isEqualTo(1)
        mapCheck.close()
        
        val matchCheck = db.query("SELECT * FROM purchase_invoice_line_matches WHERE parseResultId = 'parse-1'")
        assertThat(matchCheck.count).isEqualTo(1)
        matchCheck.close()
        
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

        val mapping = roomDb.supplierItemMappingDao().getMapping("rest-1", "sup-1", com.venkoi.cuentame.core.model.supplier.SupplierItemMappingKeyType.VENDOR_CODE, "001234")
        assertThat(mapping).isNotNull()
        assertThat(mapping?.ingredientId).isEqualTo("ing-1")

        val matches = roomDb.purchaseInvoiceLineMatchDao().getMatchesForParseResult("parse-1")
        assertThat(matches.size).isEqualTo(1)
        assertThat(matches[0].status).isEqualTo(com.venkoi.cuentame.core.model.purchase.InvoiceLineMatchStatus.CONFIRMED)

        roomDb.close()
    }
}
