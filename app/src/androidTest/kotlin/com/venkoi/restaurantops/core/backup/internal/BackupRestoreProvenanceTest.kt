package com.venkoi.restaurantops.core.backup.internal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.model.*
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.*
import com.venkoi.restaurantops.core.model.backup.BackupManifest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreProvenanceTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var applier: RoomRestoreDatabaseApplier

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        applier = RoomRestoreDatabaseApplier(db, db.backupDao(), db.restoreDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun application_and_origin_records_survive_backup_restore() = runBlocking {
        // 1. Prepare a snapshot with provenance records and their dependencies
        val snapshot = createBaseSnapshot("r1").copy(
            inventoryAreas = listOf(InventoryAreaBackupDto("a1", "r1", "Kitchen", "kitchen", 0, true, 0, 0, null)),
            units = listOf(UnitBackupDto("u1", "Unit", "u", "COUNT", "1", true, 0)),
            ingredients = listOf(IngredientBackupDto("i1", "r1", "Ing", "ing", null, "u1", "a1", null, null, null, true, 0, 0, null)),
            ingredientUnitOptions = listOf(IngredientUnitOptionBackupDto("o1", "i1", "Unit", "u", null, "1", true, true, true, true, 0, 0, null)),
            purchaseReceipts = listOf(createPurchaseReceiptDto("pr1", "r1")),
            purchaseLines = listOf(createPurchaseLineDto("l1", "pr1")),
            purchaseInvoiceOcrResults = listOf(createOcrResultDto("ocr1", "pr1")),
            purchaseInvoiceParseResults = listOf(createParseResultDto("p1", "pr1", "ocr1")),
            purchaseInvoiceDraftApplications = listOf(
                PurchaseInvoiceDraftApplicationBackupDto(
                    id = "app1",
                    purchaseReceiptId = "pr1",
                    parseResultId = "p1",
                    sourceDocumentSha256 = "sha",
                    sourceStateFingerprint = "fingerprint",
                    appliedAt = 123456789L
                )
            ),
            purchaseInvoiceLineOrigins = listOf(
                PurchaseInvoiceLineOriginBackupDto(
                    purchaseLineId = "l1",
                    applicationId = "app1",
                    sourceLineIndex = 0,
                    sourceStateFingerprint = "fingerprint",
                    lastMaterializedSnapshotJson = "{\"test\":true}"
                )
            )
        )

        val manifest = BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-08-10T00:00:00Z",
            applicationId = "com.venkoi.restaurantops",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 10,
            restaurantId = "r1",
            restaurantName = "Rest",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = mapOf(
                "restaurants" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "inventory_areas" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "units" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "ingredients" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "ingredient_unit_options" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "purchase_receipts" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "purchase_lines" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "purchase_invoice_ocr_results" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "purchase_invoice_parse_results" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "purchase_invoice_draft_applications" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false),
                "purchase_invoice_line_origins" to com.venkoi.restaurantops.core.model.backup.TableMetadata(1, false)
            ),
            attachments = emptyList(),
            includedSections = listOf("data")
        )

        // 2. Restore
        applier.replaceWithBackup(snapshot, manifest)

        // 3. Verify survivors
        val apps = db.purchaseInvoiceMaterializationDao().getApplicationForReceipt("pr1")
        assertThat(apps).isNotNull()
        assertThat(apps!!.id).isEqualTo("app1")
        assertThat(apps.sourceStateFingerprint).isEqualTo("fingerprint")

        val origins = db.purchaseInvoiceMaterializationDao().getLineOrigins("app1")
        assertThat(origins).hasSize(1)
        assertThat(origins[0].purchaseLineId).isEqualTo("l1")
        assertThat(origins[0].lastMaterializedSnapshotJson).isEqualTo("{\"test\":true}")
    }

    private fun createBaseSnapshot(restaurantId: String) = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restaurantId, "Rest", "USD", "en-US", 0, 0, null)),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )

    private fun createPurchaseReceiptDto(id: String, restaurantId: String) = PurchaseReceiptBackupDto(
        id = id,
        restaurantId = restaurantId,
        supplierId = null,
        invoiceNumber = "INV1",
        purchaseDate = 0L,
        status = "DRAFT",
        notes = null,
        attachmentId = null,
        attachmentDisplayName = null,
        createdAt = 0L,
        updatedAt = 0L,
        postedAt = null,
        voidedAt = null
    )

    private fun createPurchaseLineDto(id: String, receiptId: String) = PurchaseLineBackupDto(
        id = id,
        purchaseReceiptId = receiptId,
        ingredientId = "i1",
        areaId = "a1",
        ingredientUnitOptionId = "o1",
        quantityEntered = "1",
        quantityBase = "1",
        unitCostBase = "10",
        lineTotal = "10",
        notes = null,
        createdAt = 0L,
        updatedAt = 0L
    )
    
    private fun createOcrResultDto(id: String, receiptId: String) = PurchaseInvoiceOcrResultBackupDto(
        id = id,
        purchaseReceiptId = receiptId,
        sourceDocumentSha256 = "sha",
        sourceMimeType = "pdf",
        engine = "test",
        evidenceSchemaVersion = 1,
        pageCount = 1,
        fullText = "",
        processedAt = 0L
    )
    
    private fun createParseResultDto(id: String, receiptId: String, ocrId: String) = PurchaseInvoiceParseResultBackupDto(
        id = id,
        purchaseReceiptId = receiptId,
        ocrResultId = ocrId,
        sourceDocumentSha256 = "sha",
        parserEngine = "test",
        parserSchemaVersion = 1,
        headerEvidenceJson = "{}",
        totalsEvidenceJson = "{}",
        correctionsJson = null,
        warningsJson = "[]",
        processedAt = 0L,
        reviewedAt = null
    )
}
