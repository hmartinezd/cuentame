package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.fakes.*
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.backup.platform.DefaultBackupArchiveValidator
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.common.database.DatabaseSchema
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class BackupMetadataParityTest {

    private val localeReconciler = mockk<AppLocaleReconciler>()
    private val preferencesSource = FakeBackupPreferencesSource()
    private val attachmentSource = FakeBackupAttachmentSource()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val jsonCodecs = BackupJsonCodecs()

    private lateinit var planner: BackupCreationPlanner
    private lateinit var validator: DefaultBackupArchiveValidator

    @Before
    fun setup() {
        planner = BackupCreationPlanner(
            localeReconciler = localeReconciler,
            preferencesSource = preferencesSource,
            attachmentSource = attachmentSource,
            timeProvider = timeProvider,
            appVersionProvider = appVersionProvider,
            jsonCodecs = jsonCodecs
        )
        validator = DefaultBackupArchiveValidator(jsonCodecs)

        every { timeProvider.now() } returns Instant.parse("2026-01-01T12:00:00Z")
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns DatabaseSchema.VERSION
    }

    @Test
    fun `planner and validator agree on current metadata`() = runTest {
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        preferencesSource.result = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        
        val snapshotDto = BackupTestFixtures.createPopulatedCurrentSnapshot()
        
        // 0. Validate snapshot integrity before planning
        val manifestBefore = BackupManifest(
            backupFormatVersion = 2,
            createdAtUtc = "2026-08-02T12:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = DatabaseSchema.VERSION,
            restaurantId = "r1",
            restaurantName = "Test Rest",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = createExpectedMetadata(snapshotDto),
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments")
        )
        assertThat(BackupSnapshotIntegrityValidator.validate(snapshotDto, manifestBefore).isSuccess).isTrue()

        val snapshotResult = BackupSnapshotResult(snapshotDto, emptyList())

        val restaurant = Restaurant(RestaurantId("r1"), "Test Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val planResult = planner.createPlan(restaurant, snapshotResult)
        
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        // 1. Validate Planner's metadata
        val plannerMetadata = plan.manifest.tableMetadata
        assertThat(plannerMetadata.keys).containsExactlyElementsIn(BackupFormatV1Contract.expectedTablesForSchema(DatabaseSchema.VERSION))
        
        // 2. Build archive from plan
        val archiveBytes = buildArchive(plan)
        
        // 3. Validate through Validator
        val validationResult = validator.validate(ByteArrayInputStream(archiveBytes))
        assertThat(validationResult).isInstanceOf(BackupValidationResult.Valid::class.java)
        val validatedManifest = (validationResult as BackupValidationResult.Valid).manifest
        
        // 4. Compare all three manifestations
        assertThat(validatedManifest.tableMetadata).isEqualTo(plannerMetadata)
        assertThat(validatedManifest.entryCounts()).isEqualTo(plannerMetadata.mapValues { it.value.entryCount })
        assertThat(validatedManifest.databaseSchemaVersion).isEqualTo(plan.manifest.databaseSchemaVersion)
        assertThat(validatedManifest.restaurantId).isEqualTo(plan.manifest.restaurantId)
        
        // Assert specific derived flags
        assertThat(plannerMetadata["inventory_balance_projections"]?.isDerived).isTrue()
        assertThat(plannerMetadata["ingredient_cost_projections"]?.isDerived).isTrue()
        assertThat(plannerMetadata["ingredients"]?.isDerived).isFalse()
    }

    private fun BackupManifest.entryCounts() = tableMetadata.mapValues { it.value.entryCount }

    private fun createExpectedMetadata(dto: BackupSnapshotDto): Map<String, TableMetadata> {
        val schemaVersion = DatabaseSchema.VERSION
        val counts = mapOf(
            "restaurants" to dto.restaurants.size,
            "inventory_areas" to dto.inventoryAreas.size,
            "ingredient_categories" to dto.ingredientCategories.size,
            "units" to dto.units.size,
            "ingredients" to dto.ingredients.size,
            "ingredient_unit_options" to dto.ingredientUnitOptions.size,
            "suppliers" to dto.suppliers.size,
            "purchase_receipts" to dto.purchaseReceipts.size,
            "purchase_lines" to dto.purchaseLines.size,
            "stock_counts" to dto.stockCounts.size,
            "stock_count_areas" to dto.stockCountAreas.size,
            "stock_count_lines" to dto.stockCountLines.size,
            "waste_events" to dto.wasteEvents.size,
            "inventory_movements" to dto.inventoryMovements.size,
            "inventory_balance_projections" to dto.inventoryBalanceProjections.size,
            "ingredient_cost_projections" to dto.ingredientCostProjections.size,
            "preparation_recipes" to dto.preparationRecipes.size,
            "preparation_recipe_components" to dto.preparationRecipeComponents.size,
            "menu_recipes" to dto.menuRecipes.size,
            "menu_recipe_components" to dto.menuRecipeComponents.size,
            "production_batches" to dto.productionBatches.size,
            "production_batch_components" to dto.productionBatchComponents.size,
            "purchase_invoice_ocr_results" to dto.purchaseInvoiceOcrResults.size,
            "purchase_invoice_ocr_pages" to dto.purchaseInvoiceOcrPages.size,
            "purchase_invoice_parse_results" to dto.purchaseInvoiceParseResults.size,
            "purchase_invoice_parsed_lines" to dto.purchaseInvoiceParsedLines.size,
            "supplier_item_mappings" to dto.supplierItemMappings.size,
            "purchase_invoice_line_matches" to dto.purchaseInvoiceLineMatches.size,
            "purchase_invoice_draft_applications" to dto.purchaseInvoiceDraftApplications.size,
            "purchase_invoice_line_origins" to dto.purchaseInvoiceLineOrigins.size
        )
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(schemaVersion)
        return expectedTables.associateWith { table ->
             TableMetadata(
                 counts.getOrDefault(table, 0),
                 table in BackupFormatV1Contract.DERIVED_TABLES
             )
        }
    }

    private fun buildArchive(plan: BackupPlan): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(bos)

        fun add(name: String, content: ByteArray) {
            val entry = java.util.zip.ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(content)
            zos.closeEntry()
        }

        add(BackupFormatV1Contract.MANIFEST_ENTRY, plan.manifestJson.copyForTest())
        add(BackupFormatV1Contract.DATABASE_ENTRY, plan.snapshotJson.copyForTest())
        add(BackupFormatV1Contract.PREFERENCES_ENTRY, plan.preferencesJson.copyForTest())
        add(BackupFormatV1Contract.CHECKSUMS_ENTRY, plan.checksumsJson.copyForTest())

        zos.close()
        return bos.toByteArray()
    }
}
