package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.*
import com.venkoi.cuentame.core.backup.model.BackupSnapshotDto
import com.venkoi.cuentame.core.backup.platform.DefaultBackupArchiveValidator
import com.venkoi.cuentame.core.common.AppVersionProvider
import com.venkoi.cuentame.core.common.time.TimeProvider
import com.venkoi.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.venkoi.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.venkoi.cuentame.core.model.backup.BackupManifest
import com.venkoi.cuentame.core.model.backup.BackupPreferencesDto
import com.venkoi.cuentame.core.model.backup.BackupResult
import com.venkoi.cuentame.core.model.backup.BackupValidationResult
import com.venkoi.cuentame.core.model.restaurant.Restaurant
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.database.DatabaseSchema
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.security.MessageDigest

class BackupMetadataConsistencyTest {

    private val codecs = BackupJsonCodecs()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val localeReconciler = mockk<AppLocaleReconciler>()
    private val preferencesSource = mockk<BackupPreferencesSource>()
    private val attachmentSource = mockk<BackupAttachmentSource>()

    private lateinit var planner: BackupCreationPlanner
    private lateinit var validator: DefaultBackupArchiveValidator

    @Before
    fun setup() {
        every { timeProvider.now() } returns Instant.parse("2026-01-01T12:00:00Z")
        every { appVersionProvider.applicationId } returns "com.venkoi.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns DatabaseSchema.VERSION

        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        coEvery { preferencesSource.loadPreferences() } returns BackupPreferencesDto("SYSTEM", true, "en-US")

        planner = BackupCreationPlanner(
            localeReconciler,
            preferencesSource,
            attachmentSource,
            timeProvider,
            appVersionProvider,
            codecs
        )
        validator = DefaultBackupArchiveValidator(codecs)
    }

    @Test
    fun planner_validator_and_helper_produce_consistent_metadata() = runBlocking<Unit> {
        // 1. Create a representative current snapshot
        val snapshot = createPopulatedCurrentSnapshot()
        val restaurant = Restaurant(RestaurantId("r1"), "Test", "USD", "en-US", Instant.now(), Instant.now())
        
        // 2. Generate plan through Planner
        val snapshotResult = BackupSnapshotResult(snapshot, emptyList())
        val planResult = planner.createPlan(restaurant, snapshotResult)
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        val plannerMetadata = plan.manifest.tableMetadata

        // 3. Generate manifest through Test Helper (Local copy for JVM test)
        val helperManifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = DatabaseSchema.VERSION,
            restaurantName = "Test",
            localeTag = "en-US",
            currencyCode = "USD"
        )
        val helperMetadata = helperManifest.tableMetadata

        // 4. Compare Planner vs Helper
        assertThat(plannerMetadata).isEqualTo(helperMetadata)

        // 5. Build archive and validate with Validator
        val archiveBytes = buildArchive(plan.manifest, snapshot, plan.preferencesDto)
        val validationResult = validator.validate(archiveBytes.inputStream())
        
        assertThat(validationResult).isInstanceOf(BackupValidationResult.Valid::class.java)
        val validatedManifest = (validationResult as BackupValidationResult.Valid).manifest
        
        // 6. Compare Validator-accepted manifest against original
        assertThat(validatedManifest.tableMetadata).isEqualTo(plannerMetadata)
        
        // 7. Verify literal expected schema keys
        val expectedKeys = BackupFormatV1Contract.expectedTablesForSchema(DatabaseSchema.VERSION)
        assertThat(plannerMetadata.keys).containsExactlyElementsIn(expectedKeys)
    }

    private fun createPopulatedCurrentSnapshot() = BackupSnapshotDto(
        restaurants = listOf(com.venkoi.cuentame.core.backup.model.RestaurantBackupDto("r1", "Test", "USD", "en-US", 100, 100, null)),
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
        ingredientCostProjections = emptyList(),
        preparationRecipes = emptyList(),
        preparationRecipeComponents = emptyList(),
        menuRecipes = emptyList(),
        menuRecipeComponents = emptyList(),
        productionBatches = emptyList(),
        productionBatchComponents = emptyList(),
        purchaseInvoiceOcrResults = emptyList(),
        purchaseInvoiceOcrPages = emptyList(),
        purchaseInvoiceParseResults = emptyList(),
        purchaseInvoiceParsedLines = emptyList(),
        supplierItemMappings = emptyList(),
        purchaseInvoiceLineMatches = emptyList(),
        purchaseInvoiceDraftApplications = emptyList(),
        purchaseInvoiceLineOrigins = emptyList()
    )

    private fun createManifestForSnapshot(
        snapshot: BackupSnapshotDto,
        schemaVersion: Int,
        restaurantName: String,
        localeTag: String,
        currencyCode: String
    ): BackupManifest {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        
        val tableMetadata = mutableMapOf<String, com.venkoi.cuentame.core.model.backup.TableMetadata>()
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(schemaVersion)
        val derivedTables = BackupFormatV1Contract.DERIVED_TABLES
        
        val counts = mapOf(
            "restaurants" to snapshot.restaurants.size,
            "inventory_areas" to snapshot.inventoryAreas.size,
            "ingredient_categories" to snapshot.ingredientCategories.size,
            "units" to snapshot.units.size,
            "ingredients" to snapshot.ingredients.size,
            "ingredient_unit_options" to snapshot.ingredientUnitOptions.size,
            "suppliers" to snapshot.suppliers.size,
            "purchase_receipts" to snapshot.purchaseReceipts.size,
            "purchase_lines" to snapshot.purchaseLines.size,
            "stock_counts" to snapshot.stockCounts.size,
            "stock_count_areas" to snapshot.stockCountAreas.size,
            "stock_count_lines" to snapshot.stockCountLines.size,
            "waste_events" to snapshot.wasteEvents.size,
            "inventory_movements" to snapshot.inventoryMovements.size,
            "inventory_balance_projections" to snapshot.inventoryBalanceProjections.size,
            "ingredient_cost_projections" to snapshot.ingredientCostProjections.size,
            "preparation_recipes" to snapshot.preparationRecipes.size,
            "preparation_recipe_components" to snapshot.preparationRecipeComponents.size,
            "menu_recipes" to snapshot.menuRecipes.size,
            "menu_recipe_components" to snapshot.menuRecipeComponents.size,
            "menus" to snapshot.menus.size,
            "menu_categories" to snapshot.menuCategories.size,
            "menu_placements" to snapshot.menuPlacements.size,
            "menu_publications" to snapshot.menuPublications.size,
            "menu_publication_categories" to snapshot.menuPublicationCategories.size,
            "menu_publication_items" to snapshot.menuPublicationItems.size,
            "menu_publication_item_components" to snapshot.menuPublicationItemComponents.size,
            "production_batches" to snapshot.productionBatches.size,
            "production_batch_components" to snapshot.productionBatchComponents.size,
            "purchase_invoice_ocr_results" to snapshot.purchaseInvoiceOcrResults.size,
            "purchase_invoice_ocr_pages" to snapshot.purchaseInvoiceOcrPages.size,
            "purchase_invoice_parse_results" to snapshot.purchaseInvoiceParseResults.size,
            "purchase_invoice_parsed_lines" to snapshot.purchaseInvoiceParsedLines.size,
            "supplier_item_mappings" to snapshot.supplierItemMappings.size,
            "purchase_invoice_line_matches" to snapshot.purchaseInvoiceLineMatches.size,
            "purchase_invoice_draft_applications" to snapshot.purchaseInvoiceDraftApplications.size,
            "purchase_invoice_line_origins" to snapshot.purchaseInvoiceLineOrigins.size
        )

        for (table in expectedTables) {
            val count = counts[table] ?: 0
            tableMetadata[table] = com.venkoi.cuentame.core.model.backup.TableMetadata(
                entryCount = count,
                isDerived = table in derivedTables
            )
        }

        return BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.venkoi.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1L,
            databaseSchemaVersion = schemaVersion,
            restaurantId = restaurantId,
            restaurantName = restaurantName,
            localeTag = localeTag,
            currencyCode = currencyCode,
            tableMetadata = tableMetadata,
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments"),
            checksumAlgorithm = "SHA-256"
        )
    }

    private fun buildArchive(
        manifest: BackupManifest,
        snapshot: BackupSnapshotDto,
        prefs: BackupPreferencesDto
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val zos = ZipOutputStream(bos)

        fun add(name: String, content: ByteArray) {
            val entry = ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(content)
            zos.closeEntry()
        }

        val manifestJson = codecs.writer.encodeToString(manifest).toByteArray()
        val snapshotJson = codecs.writer.encodeToString(snapshot).toByteArray()
        val prefsJson = codecs.writer.encodeToString(prefs).toByteArray()

        add("manifest.json", manifestJson)
        add("data/database.json", snapshotJson)
        add("preferences/settings.json", prefsJson)

        val checksums = mapOf(
            "manifest.json" to sha256(manifestJson),
            "data/database.json" to sha256(snapshotJson),
            "preferences/settings.json" to sha256(prefsJson)
        )
        val checksumsJson = codecs.writer.encodeToString(checksums).toByteArray()
        add("checksums.json", checksumsJson)

        zos.close()
        return bos.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
