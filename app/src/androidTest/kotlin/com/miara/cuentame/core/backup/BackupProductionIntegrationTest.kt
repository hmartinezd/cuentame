package com.miara.cuentame.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.backup.platform.*
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.database.DatabaseSchema
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
import com.miara.cuentame.core.model.inventory.InventoryMovementOperationIds
import kotlinx.serialization.encodeToString
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant

import com.miara.cuentame.core.domain.service.HistoricalInventoryCostCalculator
import com.miara.cuentame.core.database.repository.RoomInventoryProjectionRebuilder
import com.miara.cuentame.core.database.repository.InventoryMovementValidator
import com.miara.cuentame.core.database.repository.InventoryMovementHistoryValidator
import com.miara.cuentame.core.common.ids.IngredientId

@RunWith(AndroidJUnit4::class)
class BackupProductionIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var coordinator: BackupRestoreCoordinator
    private lateinit var backupRepository: AndroidBackupRepository
    private lateinit var documentStore: FakeInternalDocumentStore
    
    private val codecs = BackupJsonCodecs()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val localeReconciler = mockk<AppLocaleReconciler>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        
        every { timeProvider.now() } returns Instant.parse("2026-01-01T12:00:00Z")
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns BackupFormatV1Contract.DATABASE_SCHEMA_VERSION
        
        val backupDao = db.backupDao()
        val restoreDao = db.restoreDao()
        val checksumProvider = Sha256ChecksumProvider()
        val snapshotSource = RoomBackupSnapshotSource(backupDao, checksumProvider)
        documentStore = FakeInternalDocumentStore(tempFolder.newFolder("backups"))
        
        val planner = BackupCreationPlanner(
            localeReconciler,
            mockk(relaxed = true) {
                 coEvery { loadPreferences() } returns com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
            },
            mockk(relaxed = true), // attachmentSource
            timeProvider,
            appVersionProvider,
            codecs
        )
        
        val writer = DefaultBackupArchiveWriter(mockk(relaxed = true))
        val validator = DefaultBackupArchiveValidator(codecs)
        
        val operationGate = RestoreOperationGate()
        operationGate.updateRecoveryState(RestoreStartupState.Ready)

        val restaurantRepository = mockk<com.miara.cuentame.core.domain.repository.RestaurantRepository>(relaxed = true) {
            coEvery { getRestaurant() } returns com.miara.cuentame.core.model.restaurant.Restaurant(
                com.miara.cuentame.core.common.ids.RestaurantId("r1"), "Original", "USD", "en-US", Instant.EPOCH, Instant.EPOCH
            )
        }

        backupRepository = AndroidBackupRepository(
            snapshotSource, documentStore, planner, mockk(relaxed = true),
            restaurantRepository, mockk(relaxed = true), writer, validator,
            operationGate
        )
        
        val processor = BackupArchiveProcessor(BackupReadLimits()) { input -> java.util.zip.ZipInputStream(input) }
        val fingerprinter = BackupArchiveFingerprinter(codecs)
        val reader = DefaultBackupArchiveReader(codecs, processor, fingerprinter)
        val restoreRepository = AndroidBackupRestoreRepository(documentStore, reader)
        
        val databaseApplier = RoomRestoreDatabaseApplier(db, backupDao, restoreDao)
        val preferencesApplier = mockk<RestorePreferencesApplier>(relaxed = true) {
            coEvery { validate(any()) } returns true
            coEvery { verifyMatches(any()) } returns true
            coEvery { captureRollback() } returns com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        }
        val storage = InternalBackupRestoreStorage(context)
        val journal = RestoreJournal(storage, codecs)
        val attachmentInstaller = RestoreAttachmentInstaller(storage, checksumProvider)
        val recoveryCoordinator = RestoreRecoveryCoordinator(journal, storage, databaseApplier, preferencesApplier, attachmentInstaller, codecs)
        
        val stager = BackupArchiveRestoreStager(codecs, processor, storage, fingerprinter)

        coordinator = BackupRestoreCoordinatorImpl(
            restoreRepository, databaseApplier, preferencesApplier,
            journal, storage, recoveryCoordinator, operationGate, 
            stager, attachmentInstaller, documentStore, codecs,
            com.miara.cuentame.core.backup.internal.NoOpRestoreFailureInjector()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun production_path_no_attachment_restore() = runBlocking {
        // 1. Seed data
        seedAllTables()

        val backupDao = db.backupDao()
        val snapshotSource = RoomBackupSnapshotSource(backupDao, mockk(relaxed = true))
        val originalSnapshot = snapshotSource.loadSnapshot("r1").dto

        // 2. Create backup
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        val backupUri = "content://backup/full.zip"
        val statuses = backupRepository.createBackup(backupUri).toList()
        assertThat(statuses.last()).isInstanceOf(com.miara.cuentame.core.domain.repository.BackupOperationStatus.Success::class.java)
        
        // 3. Mutate data (Delete everything)
        db.restoreDao().clearAllInOrder()
        assertThat(db.restaurantDao().getById("r1")).isNull()
        
        // 4. Inspect
        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = inspection as BackupArchiveInspectionResult.Ready
        assertThat(ready.eligibility).isEqualTo(BackupRestoreEligibility.Eligible)
        
        // 5. Apply
        val result = coordinator.apply(BackupDocumentUri(backupUri), ready.archive.fingerprint) {}
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
        
        // 6. Verify original data restored exactly (Task 10)
        val restoredSnapshot = snapshotSource.loadSnapshot("r1").dto
        assertThat(restoredSnapshot).isEqualTo(originalSnapshot)

        // Integrity validation
        BackupSnapshotIntegrityValidator.validate(restoredSnapshot, ready.archive.manifest).getOrThrow()
        
        // Manifest consistency
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(ready.archive.manifest, restoredSnapshot)).isNull()

        // Table counts match (Current Schema)
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(DatabaseSchema.VERSION)
        assertThat(ready.archive.manifest.tableMetadata.keys).containsExactlyElementsIn(expectedTables)
        
        // Mutation-only records removed
        val movements = db.inventoryMovementDao().getAll()
        assertThat(movements.any { it.sourceDocumentType == "RESTORE_JOURNAL" }).isFalse()
    }

    @Test
    fun recovery_from_rolling_back_phase_succeeds() = runBlocking {
        // 1. Seed original state
        seedAllTables()
        val originalLine = db.purchaseDao().getLineById("pl1")!!
        
        // 2. Prepare recovery environment
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = InternalBackupRestoreStorage(context)
        val journal = RestoreJournal(storage, codecs)
        val sessionId = "recovery-session"
        
        // 3. Validate state before capture (Instruction 12)
        val backupDao = db.backupDao()
        val snapshotSource = RoomBackupSnapshotSource(backupDao, mockk(relaxed = true))
        val snapshotDto = snapshotSource.loadSnapshot("r1").dto
        
        val tables = createTableMetadata(snapshotDto, DatabaseSchema.VERSION)
        val manifest = com.miara.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = com.miara.cuentame.core.backup.api.BackupFormatV1Contract.BACKUP_FORMAT_VERSION, createdAtUtc = "2026-01-01T12:00:00Z", applicationId = "com.miara.cuentame",
            appVersionName = "1.0", appVersionCode = 1, databaseSchemaVersion = DatabaseSchema.VERSION,
            restaurantId = "r1", restaurantName = "Original", localeTag = "en-US", currencyCode = "USD",
            tableMetadata = tables, attachments = emptyList(), includedSections = listOf("data", "preferences", "attachments"),
            checksumAlgorithm = "SHA-256"
        )
        
        // Assert exact manifest sets
        assertThat(manifest.tableMetadata.keys).containsExactlyElementsIn(BackupFormatV1Contract.expectedTablesForSchema(DatabaseSchema.VERSION))
        
        // Validate manifest/snapshot contract
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshotDto)).isNull()
        
        // Validate snapshot integrity
        BackupSnapshotIntegrityValidator.validate(snapshotDto, manifest).getOrThrow()

        // 4. Capture and persist rollback snapshot
        val rollback = databaseApplier().captureRollbackSnapshot()
        storage.saveRollbackSnapshot(sessionId, codecs.writer.encodeToString<RestoreDatabaseRollbackSnapshot>(rollback))
        
        // Instruction 15: Always create attachments dir in rollback path even if empty
        val rollbackAttachmentsDir = File(storage.getRollbackDir(sessionId), "attachments")
        rollbackAttachmentsDir.mkdirs()

        
        // 5. Write journal in ROLLING_BACK state
        val prevPrefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        journal.write(RestoreJournalDto(
            sessionId = sessionId,
            phase = RestorePhase.ROLLING_BACK,
            expectedArchiveFingerprint = "hash",
            previousPreferences = prevPrefs,
            attachmentInventory = emptyList(),
            startedAt = 0
        ))
        
        // 6. Mutate live database to state B
        db.restoreDao().clearAllInOrder()
        assertThat(db.restaurantDao().getById("r1")).isNull()
        
        // 7. Invoke recovery
        val result = coordinator.retryRecovery()
        
        // 8. Assert recovery success
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered(sessionId))
        
        // 9. Verify data matches original state exactly (Instruction 11)
        val restoredSnapshot = snapshotSource.loadSnapshot("r1").dto
        assertThat(restoredSnapshot).isEqualTo(snapshotDto)

        // 10. Validate restored snapshot against ORIGINAL manifest (Instruction 11)
        assertThat(BackupManifestContractValidator.validateSnapshotConsistency(manifest, restoredSnapshot)).isNull()
        
        // Validate snapshot integrity
        BackupSnapshotIntegrityValidator.validate(restoredSnapshot, manifest).getOrThrow()
        
        val restoredLine = db.purchaseDao().getLineById("pl1")!!
        assertThat(restoredLine.lineTotal).isEqualTo(originalLine.lineTotal)
        assertThat(restoredLine.unitCostBase).isEqualTo(originalLine.unitCostBase)
        
        // 11. Verify cleanup
        assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
        assertThat(storage.getRollbackSnapshotFile(sessionId).exists()).isFalse()
    }

    private fun databaseApplier() = RoomRestoreDatabaseApplier(db, db.backupDao(), db.restoreDao())

    private suspend fun seedAllTables() {
        db.restaurantDao().insert(RestaurantEntity("r1", "Original", "USD", "en-US", 100, 100, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a1", "r1", "Area 1", "area 1", 1, true, 100, 100, null))
        db.ingredientCategoryDao().upsert(IngredientCategoryEntity("c1", "r1", "Cat 1", "cat 1", 1, true, 100, 100, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "Unit", "u", "COUNT", BigDecimal.ONE, true, 1)))
        
        db.ingredientDao().insert(IngredientEntity("i1", "r1", "Ing 1", "ing 1", "c1", "u1", "a1", "SKU1", "Notes", BigDecimal("10"), true, 100, 100, null))
        db.ingredientUnitOptionDao().upsert(IngredientUnitOptionEntity("o1", "i1", "Opt 1", "o1", "u1", BigDecimal.ONE, true, true, true, true, 100, 100, null))
        
        db.ingredientDao().insert(IngredientEntity("i2", "r1", "Ing 2", "ing 2", "c1", "u1", "a1", "SKU2", "Notes", BigDecimal("10"), true, 100, 100, null))
        db.ingredientUnitOptionDao().upsert(IngredientUnitOptionEntity("o2", "i2", "Opt 2", "o2", "u1", BigDecimal.ONE, true, true, true, true, 100, 100, null))

        db.supplierDao().insert(SupplierEntity("s1", "r1", "Sup 1", "sup 1", "123", "sup@test.com", "Notes", true, 100, 100, null))
        
        // Purchase 1
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", "r1", "s1", "INV1", 1000, "POSTED", "Notes", null, null, 100, 100, 1000, null))
        db.purchaseDao().insertLine(PurchaseLineEntity(
            id = "pl1",
            purchaseReceiptId = "p1",
            ingredientId = "i1",
            areaId = "a1",
            ingredientUnitOptionId = "o1",
            quantityEntered = "20",
            quantityBase = "20",
            lineTotal = "60",
            unitCostBase = "3",
            notes = "Notes",
            createdAt = 100,
            updatedAt = 100
        ))

        // Purchase 2
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p2", "r1", "s1", "INV2", 1300, "POSTED", "Notes", null, null, 100, 100, 1300, null))
        db.purchaseDao().insertLine(PurchaseLineEntity(
            id = "pl2",
            purchaseReceiptId = "p2",
            ingredientId = "i2",
            areaId = "a1",
            ingredientUnitOptionId = "o2",
            quantityEntered = "10",
            quantityBase = "10",
            lineTotal = "100",
            unitCostBase = "10",
            notes = "Notes",
            createdAt = 100,
            updatedAt = 100
        ))

        db.stockCountDao().insertCount(StockCountEntity("sc1", "r1", "Count 1", 1000, 1000, null, "DRAFT", "Notes", 100, 100, null))
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("sca1", "sc1", "a1", "NOT_STARTED", null, null, 1)))
        db.stockCountDao().insertCountLine(StockCountLineEntity("scl1", "sca1", "i1", "o1", "5", "5", null, null, "Notes", 100, 100))
        
        db.wasteDao().insert(WasteEventEntity("w1", "r1", "i1", "a1", "o1", "2", "2", "SPOILED", 1200, "Notes", null, null, "POSTED", 100, 100, 1200, null))
        
        db.inventoryMovementDao().insertAll(listOf(
            InventoryMovementEntity("m1", "r1", "i1", "a1", "PURCHASE", "20", "3", "60", 1000, "PURCHASE_RECEIPT", "p1", InventoryMovementOperationIds.purchasePost("p1", "pl1"), "pl1", null, 1000),
            InventoryMovementEntity("m2", "r1", "i1", "a1", "WASTE", "-2", "3", "-6", 1200, "WASTE_EVENT", "w1", InventoryMovementOperationIds.wastePost("w1"), "w1", null, 1200),
            InventoryMovementEntity("m5", "r1", "i2", "a1", "PURCHASE", "10", "10", "100", 1300, "PURCHASE_RECEIPT", "p2", InventoryMovementOperationIds.purchasePost("p2", "pl2"), "pl2", null, 1300)
        ))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity("r1", "i1", "a1", "18", 1200))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity("r1", "i1", "3", 1200))
        
        // Preparation recipes
        db.preparationRecipeDao().insert(PreparationRecipeEntity(
            "r1", "r1", "i1", "Recipe 1", "recipe 1", BigDecimal("10"), BigDecimal("10"), "o1", "ACTIVE", "Notes", 100, 100, null
        ))
        db.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity(
            "rc1", "r1", "i2", "o2", BigDecimal("5"), BigDecimal("5"), 0, "Comp notes", 100, 100
        ))

        // Production batches
        db.productionBatchDao().insert(ProductionBatchEntity(
            id = "pb1", restaurantId = "r1", recipeId = "r1", recipeNameSnapshot = "Recipe 1",
            outputIngredientId = "i1", batchMultiplier = "1.0",
            recipeStandardYieldQuantitySnapshot = "10.0", recipeStandardYieldBaseSnapshot = "10.0",
            recipeYieldUnitOptionIdSnapshot = "o1", expectedOutputQuantityEntered = "10.0",
            expectedOutputQuantityBase = "10.0", actualOutputQuantityEntered = "10.0",
            actualOutputQuantityBase = "10.0", outputUnitOptionId = "o1",
            outputAreaId = "a1", hasManualOutputQuantityOverride = false,
            totalComponentCostSnapshot = "50.0", outputUnitCostBaseSnapshot = "5.0",
            effectiveAt = 2000, status = "POSTED", notes = "Batch 1",
            createdAt = 1500, updatedAt = 2500, postedAt = 2500, voidedAt = null
        ))
        db.productionBatchDao().insertComponents(listOf(
            ProductionBatchComponentEntity(
                id = "pbc1", productionBatchId = "pb1", sourceRecipeComponentIdSnapshot = "rc1",
                componentIngredientId = "i2", recipeQuantityEnteredSnapshot = "5.0",
                recipeQuantityBaseSnapshot = "5.0", recipeUnitOptionIdSnapshot = "o2",
                expectedQuantityEntered = "5.0", expectedQuantityBase = "5.0",
                actualQuantityEntered = "5.0", actualQuantityBase = "5.0",
                unitOptionId = "o2", hasManualQuantityOverride = false,
                sourceAreaId = "a1", unitCostBaseSnapshot = "10.0",
                totalCostSnapshot = "50.0", sortOrder = 0, notes = null,
                createdAt = 1500, updatedAt = 2500
            )
        ))
        db.inventoryMovementDao().insertAll(listOf(
            InventoryMovementEntity("m3", "r1", "i2", "a1", "PRODUCTION_CONSUMPTION", "-5", "10", "-50", 2000, "PRODUCTION_BATCH", "pb1", InventoryMovementOperationIds.productionConsumption("pb1", "pbc1"), "pbc1", null, 2500),
            InventoryMovementEntity("m4", "r1", "i1", "a1", "PRODUCTION_OUTPUT", "10", "5", "50", 2000, "PRODUCTION_BATCH", "pb1", InventoryMovementOperationIds.productionOutput("pb1"), "pb1", null, 2500)
        ))

        // Use rebuilder for projections
        val inventoryValidator = InventoryMovementValidator()
        val historyValidator = InventoryMovementHistoryValidator(inventoryValidator)
        val rebuilder = RoomInventoryProjectionRebuilder(
            db, db.ingredientDao(), db.inventoryMovementDao(),
            db.inventoryProjectionDao(), db.ingredientCostProjectionDao(),
            HistoricalInventoryCostCalculator(), historyValidator, timeProvider
        )
        rebuilder.rebuildForIngredient(IngredientId("i1"))
        rebuilder.rebuildForIngredient(IngredientId("i2"))
    }


    private fun createTableMetadata(dto: BackupSnapshotDto, schemaVersion: Int): Map<String, com.miara.cuentame.core.model.backup.TableMetadata> {
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
            "production_batches" to dto.productionBatches.size,
            "production_batch_components" to dto.productionBatchComponents.size,
            "stock_count_item_order" to dto.stockCountItemOrder.size,
            "purchase_invoice_ocr_results" to dto.purchaseInvoiceOcrResults.size,
            "purchase_invoice_ocr_pages" to dto.purchaseInvoiceOcrPages.size,
            "purchase_invoice_parse_results" to dto.purchaseInvoiceParseResults.size,
            "purchase_invoice_parsed_lines" to dto.purchaseInvoiceParsedLines.size,
            "supplier_item_mappings" to dto.supplierItemMappings.size,
            "purchase_invoice_line_matches" to dto.purchaseInvoiceLineMatches.size,
            "purchase_invoice_draft_applications" to dto.purchaseInvoiceDraftApplications.size,
            "purchase_invoice_line_origins" to dto.purchaseInvoiceLineOrigins.size,
            "menu_recipes" to dto.menuRecipes.size,
            "menu_recipe_components" to dto.menuRecipeComponents.size
        )
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(schemaVersion)
        return expectedTables.associateWith { table ->
             com.miara.cuentame.core.model.backup.TableMetadata(
                 counts.getOrDefault(table, 0),
                 table in BackupFormatV1Contract.DERIVED_TABLES
             )
        }
    }

    @Test
    fun v1_no_attachment_backup_is_accepted() = runBlocking {
        val fixture = BackupTestFixtures.createValidV1NoAttachmentArchiveFixture(codecs)
        val backupUri = "content://backup/valid-v1.zip"
        documentStore.openForWrite(BackupDocumentUri(backupUri)).use { it.write(fixture.archiveBytes) }

        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        
        val ready = inspection as BackupArchiveInspectionResult.Ready
        assertThat(ready.eligibility).isEqualTo(BackupRestoreEligibility.Eligible)
        
        val result = coordinator.apply(BackupDocumentUri(backupUri), ready.archive.fingerprint) {}
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
    }

    @Test
    fun v1_with_manifest_attachment_is_rejected() = runBlocking {
        val fixture = BackupTestFixtures.createInvalidV1WithAttachmentArchiveFixture(codecs)
        val backupUri = "content://backup/invalid-v1.zip"
        documentStore.openForWrite(BackupDocumentUri(backupUri)).use { it.write(fixture.archiveBytes) }

        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((inspection as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun v1_with_database_attachment_reference_is_rejected() = runBlocking {
        // Build an archive with V1 manifest (no attachments) but Snapshot HAS an attachment reference
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val snapshotWithRef = snapshot.copy(
            purchaseReceipts = snapshot.purchaseReceipts.map { 
                if (it.id == "p1") it.copy(attachmentId = "some-id") else it 
            }
        )
        
        val fixture = BackupTestFixtures.createValidV1NoAttachmentArchiveFixture(codecs)
        // Re-build with the "dirty" snapshot and valid checksums
        val manifest = fixture.manifest
        val snapshotJson = codecs.writer.encodeToString(snapshotWithRef).toByteArray()
        val manifestJson = codecs.writer.encodeToString(manifest).toByteArray()
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        val prefsJson = codecs.writer.encodeToString(prefs).toByteArray()

        val checksums = mutableMapOf(
            "manifest.json" to sha256(manifestJson),
            "data/database.json" to sha256(snapshotJson),
            "preferences/settings.json" to sha256(prefsJson)
        )
        val checksumsJson = codecs.writer.encodeToString<Map<String, String>>(checksums).toByteArray()

        val bos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(bos)
        fun add(name: String, content: ByteArray) {
            val entry = java.util.zip.ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(content)
            zos.closeEntry()
        }
        add("manifest.json", manifestJson)
        add("data/database.json", snapshotJson)
        add("preferences/settings.json", prefsJson)
        add("checksums.json", checksumsJson)
        zos.close()
        
        val backupUri = "content://backup/invalid-v1-ref.zip"
        documentStore.openForWrite(BackupDocumentUri(backupUri)).use { it.write(bos.toByteArray()) }

        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        // validateSnapshotConsistency should catch this
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((inspection as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun v1_with_physical_attachment_zip_entry_is_rejected() = runBlocking {
        // Build an archive with V1 manifest (no attachments) but ZIP contains a stray entry
        // AND checksums.json IS UPDATED to include it (so it passes checksum validation)
        val fixture = BackupTestFixtures.createValidV1NoAttachmentArchiveFixture(codecs)
        
        val bos = java.io.ByteArrayOutputStream()
        val zis = java.util.zip.ZipInputStream(fixture.archiveBytes.inputStream())
        val zos = java.util.zip.ZipOutputStream(bos)
        
        val entryContents = mutableMapOf<String, ByteArray>()
        var entry = zis.nextEntry
        while (entry != null) {
            val content = zis.readBytes()
            entryContents[entry.name] = content
            entry = zis.nextEntry
        }
        
        // Add stray entry
        val strayPath = "attachments/stray.jpg"
        val strayContent = "stray".toByteArray()
        entryContents[strayPath] = strayContent
        
        // Recalculate checksums
        val newChecksums = entryContents.filter { it.key != "checksums.json" }
            .mapValues { sha256(it.value) }
        
        val newChecksumsJson = codecs.writer.encodeToString<Map<String, String>>(newChecksums).toByteArray()
        entryContents["checksums.json"] = newChecksumsJson
        
        // Write new ZIP
        entryContents.forEach { (name, bytes) ->
            zos.putNextEntry(java.util.zip.ZipEntry(name))
            zos.write(bytes)
            zos.closeEntry()
        }
        zos.close()
        
        val backupUri = "content://backup/invalid-v1-stray.zip"
        documentStore.openForWrite(BackupDocumentUri(backupUri)).use { it.write(bos.toByteArray()) }

        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        // validateManifestStructure bijection should catch this now (after passing checksums)
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        assertThat((inspection as BackupArchiveInspectionResult.Failure).reason).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }

    @Test
    fun v2_attachment_backup_is_accepted() = runBlocking {
        val fixture = BackupTestFixtures.createValidV2AttachmentArchiveFixture(codecs)
        val backupUri = "content://backup/valid-v2.zip"
        documentStore.openForWrite(BackupDocumentUri(backupUri)).use { it.write(fixture.archiveBytes) }

        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        
        val ready = inspection as BackupArchiveInspectionResult.Ready
        assertThat(ready.eligibility).isEqualTo(BackupRestoreEligibility.Eligible)
        
        val result = coordinator.apply(BackupDocumentUri(backupUri), ready.archive.fingerprint) {}
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
    }

    @Test
    fun application_with_wrong_fingerprint_does_not_mutate_database() = runBlocking {
        seedAllTables()
        
        // 1. Create valid backup
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        val backupUri = "content://backup/fingerprint-test.zip"
        backupRepository.createBackup(backupUri).toList()
        
        val wrongFingerprint = BackupArchiveFingerprint("completely-wrong")
        
        // 2. Mutate live DB slightly
        db.restaurantDao().update(db.restaurantDao().getById("r1")!!.copy(name = "Mutated"))
        
        // 3. Apply with wrong fingerprint
        val result = coordinator.apply(BackupDocumentUri(backupUri), wrongFingerprint) {}
        assertThat(result).isInstanceOf(BackupRestoreApplyResult.Failure::class.java)
        
        // 4. Verify no restoration (Mutated name remains, it was NOT replaced by "Original")
        assertThat(db.restaurantDao().getById("r1")?.name).isEqualTo("Mutated")
        
        // 5. Verify no artifacts
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = InternalBackupRestoreStorage(context)
        assertThat(storage.getJournalFile().exists()).isFalse()
    }

    private fun database() = db

    private fun buildArchive(
        manifest: com.miara.cuentame.core.model.backup.BackupManifest,
        snapshot: BackupSnapshotDto
    ): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(bos)

        fun add(name: String, content: ByteArray) {
            val entry = java.util.zip.ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(content)
            zos.closeEntry()
        }

        val manifestJson = codecs.writer.encodeToString<com.miara.cuentame.core.model.backup.BackupManifest>(manifest).toByteArray()
        val snapshotJson = codecs.writer.encodeToString<BackupSnapshotDto>(snapshot).toByteArray()
        val prefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        val prefsJson = codecs.writer.encodeToString<com.miara.cuentame.core.model.backup.BackupPreferencesDto>(prefs).toByteArray()

        val checksums = mutableMapOf(
            "manifest.json" to sha256(manifestJson),
            "data/database.json" to sha256(snapshotJson),
            "preferences/settings.json" to sha256(prefsJson)
        )

        add("manifest.json", manifestJson)
        add("data/database.json", snapshotJson)
        add("preferences/settings.json", prefsJson)

        val checksumsJson = codecs.writer.encodeToString<Map<String, String>>(checksums).toByteArray()
        add("checksums.json", checksumsJson)

        zos.close()
        return bos.toByteArray()
    }


    private fun sha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private class FakeInternalDocumentStore(private val root: File) : BackupDocumentStore {
        override suspend fun openForWrite(destination: BackupDocumentUri) = File(root, destination.value.substringAfterLast("/")).outputStream()
        override suspend fun openForRead(source: BackupDocumentUri) = File(root, source.value.substringAfterLast("/")).inputStream()
        override suspend fun delete(document: BackupDocumentUri): Boolean = File(root, document.value.substringAfterLast("/")).delete()
        override suspend fun truncate(document: BackupDocumentUri): Boolean {
            File(root, document.value.substringAfterLast("/")).writeBytes(byteArrayOf())
            return true
        }
    }
}
