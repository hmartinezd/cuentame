package com.miara.cuentame.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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

        backupRepository = AndroidBackupRepository(
            snapshotSource, documentStore, planner, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), writer, validator,
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
        // 1. Seed data across all 16 tables
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
        
        // Specifically verify PurchaseLine values to ensure no field swapping
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
        
        // 3. Capture and persist rollback snapshot
        val rollback = databaseApplier().captureRollbackSnapshot()
        storage.saveRollbackSnapshot(sessionId, codecs.writer.encodeToString<RestoreDatabaseRollbackSnapshot>(rollback))

        
        // 4. Write journal in ROLLING_BACK state
        val prevPrefs = com.miara.cuentame.core.model.backup.BackupPreferencesDto("SYSTEM", true, "en-US")
        journal.write(RestoreJournalDto(sessionId, RestorePhase.ROLLING_BACK, "hash", prevPrefs, 0))
        
        // 5. Mutate live database to state B
        db.restoreDao().clearAllInOrder()
        assertThat(db.restaurantDao().getById("r1")).isNull()
        
        // 6. Invoke recovery
        val result = coordinator.retryRecovery()
        
        // 7. Assert recovery success
        assertThat(result).isEqualTo(RestoreRecoveryResult.Recovered(sessionId))
        
        // 8. Verify data matches original state
        verifyAllTables()
        val restoredLine = db.purchaseDao().getLineById("pl1")!!
        assertThat(restoredLine.lineTotal).isEqualTo(originalLine.lineTotal)
        assertThat(restoredLine.unitCostBase).isEqualTo(originalLine.unitCostBase)
        
        // 9. Verify cleanup
        assertThat(journal.read()).isEqualTo(RestoreJournalReadResult.Absent)
        assertThat(storage.getRollbackSnapshotFile(sessionId).exists()).isFalse()
    }

    private fun databaseApplier() = RoomRestoreDatabaseApplier(db, db.backupDao(), db.restoreDao())

    private suspend fun seedAllTables() {
        db.restaurantDao().insert(RestaurantEntity("r1", "Original", "USD", "en-US", 100, 100, null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("a1", "r1", "Area 1", "area1", 1, true, 100, 100, null))
        db.ingredientCategoryDao().upsert(IngredientCategoryEntity("c1", "r1", "Cat 1", "cat1", 1, true, 100, 100, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "Unit", "u", "COUNT", BigDecimal.ONE, true, 1)))
        db.ingredientDao().insert(IngredientEntity("i1", "r1", "Ing 1", "ing1", "c1", "u1", "a1", "SKU1", "Notes", BigDecimal.TEN, true, 100, 100, null))
        db.ingredientUnitOptionDao().upsert(IngredientUnitOptionEntity("o1", "i1", "Opt 1", "o1", "u1", BigDecimal.ONE, true, true, true, true, 100, 100, null))
        db.supplierDao().insert(SupplierEntity("s1", "r1", "Sup 1", "sup1", "123", "sup@test.com", "Notes", true, 100, 100, null))
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", "r1", "s1", "INV1", 1000, "POSTED", "Notes", null, 100, 100, 1000, null))
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
        db.stockCountDao().insertCount(StockCountEntity("sc1", "r1", "Count 1", 1000, 1000, 1100, "COMPLETED", "Notes", 100, 100, null))
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("sca1", "sc1", "a1", "COMPLETED", 1000, 1100, 1)))
        db.stockCountDao().insertCountLine(StockCountLineEntity("scl1", "sca1", "i1", "o1", "5", "5", "5", "0", "Notes", 100, 100))
        db.wasteDao().insert(WasteEventEntity("w1", "r1", "i1", "a1", "o1", "2", "2", "SPOILED", 1200, "Notes", null, "POSTED", 100, 100, 1200, null))
        db.inventoryMovementDao().insertAll(listOf(
            InventoryMovementEntity("m1", "r1", "i1", "a1", "PURCHASE", "20", "3", "60", 1000, "PURCHASE_RECEIPT", "p1", "op1", "pl1", null, 100),
            InventoryMovementEntity("m2", "r1", "i1", "a1", "WASTE", "-2", "3", "-6", 1200, "WASTE_EVENT", "w1", "op2", "w1", null, 100)
        ))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity("r1", "i1", "a1", "18", 1200))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity("r1", "i1", "3", 1200))
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
        assertThat(db.inventoryMovementDao().getAll().size).isEqualTo(2)
        assertThat(db.inventoryProjectionDao().getBalance("i1", "a1")?.quantityBase).isEqualTo("18")
        assertThat(db.ingredientCostProjectionDao().getCost("i1")?.averageUnitCostBase).isEqualTo("3")
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
