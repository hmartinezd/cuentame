package com.miara.cuentame.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.model.RestaurantBackupDto
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.*
import com.miara.cuentame.core.backup.platform.*
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.RestoreDatabaseRollbackSnapshot
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
        val snapshotSource = RoomBackupSnapshotSource(backupDao, mockk(relaxed = true))
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
        }
        val storage = InternalBackupRestoreStorage(context)
        val journal = RestoreJournal(storage, codecs)
        val recoveryCoordinator = RestoreRecoveryCoordinator(journal, storage, databaseApplier, preferencesApplier, codecs)
        
        coordinator = BackupRestoreCoordinatorImpl(
            restoreRepository, databaseApplier, preferencesApplier,
            journal, storage, recoveryCoordinator, operationGate, codecs
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
        
        // 6. Verify original data restored
        verifyAllTables()
        
        val line = db.purchaseDao().getLineById("pl1")!!
        assertThat(line.lineTotal).isEqualTo("60")
        assertThat(line.unitCostBase).isEqualTo("3")
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
        
        // 3. Validate state before capture (Instruction 10)
        val backupDao = db.backupDao()
        val snapshotSource = RoomBackupSnapshotSource(backupDao, mockk(relaxed = true))
        val snapshotDto = snapshotSource.loadSnapshot("r1").dto
        val tables = BackupFormatV1Contract.expectedTablesForSchema(4)
            .associateWith { com.miara.cuentame.core.model.backup.TableMetadata(0, it in BackupFormatV1Contract.DERIVED_TABLES) }
        val manifest = com.miara.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = 1, createdAtUtc = "2026-01-01T12:00:00Z", applicationId = "com.miara.cuentame",
            appVersionName = "1.0", appVersionCode = 1, databaseSchemaVersion = 4,
            restaurantId = "r1", restaurantName = "Original", localeTag = "en-US", currencyCode = "USD",
            tableMetadata = tables, attachments = emptyList(), includedSections = listOf("data", "preferences", "attachments"),
            checksumAlgorithm = "SHA-256"
        )
        BackupSnapshotIntegrityValidator.validate(snapshotDto, manifest).getOrThrow()

        // 4. Capture and persist rollback snapshot
        val rollback = databaseApplier().captureRollbackSnapshot()
        storage.saveRollbackSnapshot(sessionId, codecs.writer.encodeToString<RestoreDatabaseRollbackSnapshot>(rollback))

        
        // 5. Write journal in ROLLING_BACK state
        val prevPrefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        journal.write(RestoreJournalDto(sessionId, RestorePhase.ROLLING_BACK, "hash", prevPrefs, 0))
        
        // 6. Mutate live database to state B
        db.restoreDao().clearAllInOrder()
        assertThat(db.restaurantDao().getById("r1")).isNull()
        
        // 7. Invoke recovery
        val result = coordinator.retryRecovery()
        
        // 8. Assert recovery success
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered(sessionId))
        
        // 9. Verify data matches original state
        verifyAllTables()

        // 10. Validate again after recovery (Instruction 10)
        val restoredSnapshot = snapshotSource.loadSnapshot("r1").dto
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
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p2", "r1", "s1", "INV2", 1300, "POSTED", "Notes", null, 100, 100, 1300, null))
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
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("sca1", "sc1", "a1", "DRAFT", null, null, 1)))
        db.stockCountDao().insertCountLine(StockCountLineEntity("scl1", "sca1", "i1", "o1", "5", "5", null, null, "Notes", 100, 100))
        db.wasteDao().insert(WasteEventEntity("w1", "r1", "i1", "a1", "o1", "2", "2", "SPOILED", 1200, "Notes", null, "POSTED", 100, 100, 1200, null))
        db.inventoryMovementDao().insertAll(listOf(
            InventoryMovementEntity("m1", "r1", "i1", "a1", "PURCHASE", "20", "3", "60", 1000, "PURCHASE_RECEIPT", "p1", "production-post:p1:line:pl1", "pl1", null, 1000),
            InventoryMovementEntity("m2", "r1", "i1", "a1", "WASTE", "-2", "3", "-6", 1200, "WASTE_EVENT", "w1", "production-post:w1:waste", "w1", null, 1200),
            InventoryMovementEntity("m5", "r1", "i2", "a1", "PURCHASE", "10", "10", "100", 1300, "PURCHASE_RECEIPT", "p2", "production-post:p2:line:pl2", "pl2", null, 1300)
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
            InventoryMovementEntity("m3", "r1", "i2", "a1", "PRODUCTION_CONSUMPTION", "-5", "10", "-50", 2000, "PRODUCTION_BATCH", "pb1", "production-post:pb1:consume:pbc1", "pbc1", null, 2500),
            InventoryMovementEntity("m4", "r1", "i1", "a1", "PRODUCTION_OUTPUT", "10", "5", "50", 2000, "PRODUCTION_BATCH", "pb1", "production-post:pb1:output", "pb1", null, 2500)
        ))

        // Use rebuilder for projections
        val rebuilder = RoomInventoryProjectionRebuilder(
            db, db.ingredientDao(), db.inventoryMovementDao(),
            db.inventoryProjectionDao(), db.ingredientCostProjectionDao(),
            HistoricalInventoryCostCalculator(), timeProvider
        )
        rebuilder.rebuildForIngredient(IngredientId("i1"))
        rebuilder.rebuildForIngredient(IngredientId("i2"))
    }

    @Test
    fun schema2_archive_restore_succeeds() = runBlocking {
        // 1. Build schema 2 archive
        val legacySnapshot = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(RestaurantBackupDto("r1", "Legacy", "USD", "en-US", 100, 100, null))
        )
        val tables = BackupFormatV1Contract.expectedTablesForSchema(2)
            .associateWith { com.miara.cuentame.core.model.backup.TableMetadata(if (it == "restaurants") 1 else 0, it in BackupFormatV1Contract.DERIVED_TABLES) }
        val manifest = com.miara.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 2,
            restaurantId = "r1",
            restaurantName = "Legacy",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = tables,
            attachments = emptyList(),
            includedSections = listOf("attachments", "data", "preferences"),
            checksumAlgorithm = "SHA-256"
        )
        
        val bytes = buildArchive(manifest, legacySnapshot)
        val backupUri = "content://backup/legacy.zip"
        documentStore.openForWrite(BackupDocumentUri(backupUri)).use { it.write(bytes) }

        // 2. Inspect
        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = inspection as BackupArchiveInspectionResult.Ready
        assertThat(ready.archive.manifest.databaseSchemaVersion).isEqualTo(2)

        // 3. Apply
        val result = coordinator.apply(BackupDocumentUri(backupUri), ready.archive.fingerprint) {}
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)

        // 4. Verify
        assertThat(db.restaurantDao().getById("r1")?.name).isEqualTo("Legacy")
        assertThat(db.preparationRecipeDao().getAllRecipesForRestaurant("r1")).isEmpty()
    }

    @Test
    fun schema3_archive_restore_succeeds() = runBlocking {
        // 1. Build schema 3 archive (Recipes, no Production)
        val schema3Snapshot = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(RestaurantBackupDto("r1", "Recipes Only", "USD", "en-US", 100, 100, null)),
            preparationRecipes = listOf(
                com.miara.cuentame.core.backup.model.PreparationRecipeBackupDto(
                    "rec1", "r1", "i1", "Recipe 1", "recipe 1", "10", "10", "o1", "ACTIVE", null, 100, 100, null
                )
            ),
            preparationRecipeComponents = listOf(
                com.miara.cuentame.core.backup.model.PreparationRecipeComponentBackupDto(
                    "rc1", "rec1", "i2", "o2", "5", "5", 0, null, 100, 100
                )
            )
        )
        val tables = BackupFormatV1Contract.expectedTablesForSchema(3)
            .associateWith { com.miara.cuentame.core.model.backup.TableMetadata(
                when (it) {
                    "restaurants" -> 1
                    "preparation_recipes" -> 1
                    "preparation_recipe_components" -> 1
                    else -> 0
                },
                it in BackupFormatV1Contract.DERIVED_TABLES
            ) }
        val manifest = com.miara.cuentame.core.model.backup.BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.miara.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 3,
            restaurantId = "r1",
            restaurantName = "Recipes Only",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = tables,
            attachments = emptyList(),
            includedSections = listOf("attachments", "data", "preferences"),
            checksumAlgorithm = "SHA-256"
        )

        val bytes = buildArchive(manifest, schema3Snapshot)
        val backupUri = "content://backup/schema3.zip"
        documentStore.openForWrite(BackupDocumentUri(backupUri)).use { it.write(bytes) }

        // 2. Inspect
        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = inspection as BackupArchiveInspectionResult.Ready
        assertThat(ready.archive.manifest.databaseSchemaVersion).isEqualTo(3)

        // 3. Apply
        val result = coordinator.apply(BackupDocumentUri(backupUri), ready.archive.fingerprint) {}
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)

        // 4. Verify
        assertThat(db.restaurantDao().getById("r1")?.name).isEqualTo("Recipes Only")
        assertThat(db.preparationRecipeDao().getAllRecipesForRestaurant("r1")).hasSize(1)
        assertThat(db.productionBatchDao().getById("pb1")).isNull()
    }

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

        add("manifest.json", manifestJson)
        add("data/database.json", snapshotJson)
        add("preferences/settings.json", prefsJson)

        val checksums = mapOf(
            "manifest.json" to sha256(manifestJson),
            "data/database.json" to sha256(snapshotJson),
            "preferences/settings.json" to sha256(prefsJson)
        )
        val checksumsJson = codecs.writer.encodeToString<Map<String, String>>(checksums).toByteArray()
        add("checksums.json", checksumsJson)

        zos.close()
        return bos.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private suspend fun verifyAllTables() {
        assertThat(db.restaurantDao().getById("r1")?.name).isEqualTo("Original")
        assertThat(db.inventoryAreaDao().getById("a1")?.name).isEqualTo("Area 1")
        assertThat(db.ingredientCategoryDao().getById("c1")?.name).isEqualTo("Cat 1")
        assertThat(db.unitDao().getById("u1")?.name).isEqualTo("Unit")
        assertThat(db.ingredientDao().getById("i1")?.name).isEqualTo("Ing 1")
        assertThat(db.ingredientUnitOptionDao().getById("o1")?.displayName).isEqualTo("Opt 1")
        assertThat(db.supplierDao().getById("s1")?.name).isEqualTo("Sup 1")
        assertThat(db.purchaseDao().getReceiptById("p1")?.invoiceNumber).isEqualTo("INV1")
        assertThat(db.purchaseDao().getLineById("pl1")?.quantityEntered).isEqualTo("20")
        assertThat(db.stockCountDao().getCountById("sc1")?.name).isEqualTo("Count 1")
        assertThat(db.stockCountDao().getAreaById("sca1")?.stockCountId).isEqualTo("sc1")
        assertThat(db.stockCountDao().getLineById("scl1")?.quantityEntered).isEqualTo("5")
        assertThat(db.wasteDao().getById("w1")?.reason).isEqualTo("SPOILED")
        
        val movements = db.inventoryMovementDao().getAll()
        assertThat(movements).hasSize(5)
        
        // m3 PRODUCTION_CONSUMPTION
        val m3 = movements.find { it.id == "m3" }!!
        assertThat(m3.movementType).isEqualTo("PRODUCTION_CONSUMPTION")
        assertThat(m3.ingredientId).isEqualTo("i2")
        assertThat(m3.areaId).isEqualTo("a1")
        assertThat(BigDecimal(m3.quantityBaseSigned).compareTo(BigDecimal("-5"))).isEqualTo(0)
        assertThat(BigDecimal(m3.unitCostBaseSnapshot!!).compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(BigDecimal(m3.totalValueSnapshot!!).compareTo(BigDecimal("-50"))).isEqualTo(0)
        assertThat(m3.sourceDocumentType).isEqualTo("PRODUCTION_BATCH")
        assertThat(m3.sourceDocumentId).isEqualTo("pb1")
        assertThat(m3.sourceLineId).isEqualTo("pbc1")
        assertThat(m3.sourceOperationId).isEqualTo("production-post:pb1:consume:pbc1")
        assertThat(m3.reversalOfMovementId).isNull()
        assertThat(m3.effectiveAt).isEqualTo(2000)
        assertThat(m3.createdAt).isEqualTo(2500)

        // m4 PRODUCTION_OUTPUT
        val m4 = movements.find { it.id == "m4" }!!
        assertThat(m4.movementType).isEqualTo("PRODUCTION_OUTPUT")
        assertThat(m4.ingredientId).isEqualTo("i1")
        assertThat(m4.areaId).isEqualTo("a1")
        assertThat(BigDecimal(m4.quantityBaseSigned).compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(BigDecimal(m4.unitCostBaseSnapshot!!).compareTo(BigDecimal("5"))).isEqualTo(0)
        assertThat(BigDecimal(m4.totalValueSnapshot!!).compareTo(BigDecimal("50"))).isEqualTo(0)
        assertThat(m4.sourceDocumentType).isEqualTo("PRODUCTION_BATCH")
        assertThat(m4.sourceDocumentId).isEqualTo("pb1")
        assertThat(m4.sourceLineId).isEqualTo("pb1")
        assertThat(m4.sourceOperationId).isEqualTo("production-post:pb1:output")
        assertThat(m4.reversalOfMovementId).isNull()
        assertThat(m4.effectiveAt).isEqualTo(2000)
        assertThat(m4.createdAt).isEqualTo(2500)

        assertThat(db.inventoryProjectionDao().getBalance("i1", "a1")?.quantityBase).isEqualTo("28")
        assertThat(db.inventoryProjectionDao().getBalance("i2", "a1")?.quantityBase).isEqualTo("5")
        
        // i2 average cost = 10
        val costI2 = db.ingredientCostProjectionDao().getCost("i2")?.averageUnitCostBase
        assertThat(BigDecimal(costI2!!).compareTo(BigDecimal("10"))).isEqualTo(0)

        // i1 average cost = (18 * 3 + 10 * 5) / 28 = 104 / 28 = 26 / 7
        // Canonical DECIMAL128 result: 3.714285714285714285714285714285714
        val costI1 = db.ingredientCostProjectionDao().getCost("i1")?.averageUnitCostBase
        assertThat(BigDecimal(costI1!!).compareTo(BigDecimal("3.714285714285714285714285714285714"))).isEqualTo(0)
        
        val recipe = db.preparationRecipeDao().getById("r1")!!
        assertThat(recipe.name).isEqualTo("Recipe 1")
        assertThat(recipe.status).isEqualTo("ACTIVE")
        val components = db.preparationRecipeDao().getComponentsForRecipe("r1")
        assertThat(components).hasSize(1)
        assertThat(components[0].id).isEqualTo("rc1")
        assertThat(components[0].notes).isEqualTo("Comp notes")

        val batch = db.productionBatchDao().getById("pb1")!!
        assertThat(batch.notes).isEqualTo("Batch 1")
        assertThat(batch.status).isEqualTo("POSTED")
        assertThat(batch.postedAt).isEqualTo(2500)
        assertThat(batch.effectiveAt).isEqualTo(2000)
        assertThat(BigDecimal(batch.batchMultiplier).compareTo(BigDecimal("1.0"))).isEqualTo(0)
        assertThat(BigDecimal(batch.totalComponentCostSnapshot!!).compareTo(BigDecimal("50.0"))).isEqualTo(0)
        assertThat(BigDecimal(batch.outputUnitCostBaseSnapshot!!).compareTo(BigDecimal("5.0"))).isEqualTo(0)

        val batchComponents = db.productionBatchDao().getComponents("pb1")
        assertThat(batchComponents).hasSize(1)
        val pbc = batchComponents[0]
        assertThat(pbc.id).isEqualTo("pbc1")
        assertThat(pbc.componentIngredientId).isEqualTo("i2")
        assertThat(BigDecimal(pbc.actualQuantityBase).compareTo(BigDecimal("5.0"))).isEqualTo(0)
        assertThat(BigDecimal(pbc.unitCostBaseSnapshot!!).compareTo(BigDecimal("10.0"))).isEqualTo(0)
        assertThat(BigDecimal(pbc.totalCostSnapshot!!).compareTo(BigDecimal("50.0"))).isEqualTo(0)
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
