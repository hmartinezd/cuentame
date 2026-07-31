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
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.miara.cuentame.core.domain.usecase.locale.LocaleReconciliationResult
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
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
            mockk(relaxed = true), // preferencesSource
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
        val preferencesApplier = mockk<RestorePreferencesApplier>(relaxed = true)
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
        // 1. Seed data across multiple tables
        val restaurant = RestaurantEntity("r1", "Original", "USD", "en-US", 0, 0, null)
        db.restaurantDao().insert(restaurant)
        
        val area = com.miara.cuentame.core.database.entity.InventoryAreaEntity("a1", "r1", "Area 1", "area1", 0, true, 0, 0, null)
        db.inventoryAreaDao().upsert(area)
        
        val category = com.miara.cuentame.core.database.entity.IngredientCategoryEntity("c1", "r1", "Cat 1", "cat1", 0, true, 0, 0, null)
        db.ingredientCategoryDao().upsert(category)
        
        val unit = com.miara.cuentame.core.database.entity.UnitEntity("u1", "Unit", "u", "COUNT", java.math.BigDecimal.ONE, true, 0)
        db.unitDao().insertSeedUnits(listOf(unit))
        
        val ingredient = com.miara.cuentame.core.database.entity.IngredientEntity("i1", "r1", "Ing 1", "ing1", "c1", "u1", "a1", null, null, null, true, 0, 0, null)
        db.ingredientDao().insert(ingredient)

        // 2. Create backup
        coEvery { localeReconciler.reconcile() } returns LocaleReconciliationResult.InSync
        val backupUri = "content://backup/1.zip"
        val statuses = backupRepository.createBackup(backupUri).toList()
        assertThat(statuses.last()).isInstanceOf(com.miara.cuentame.core.domain.repository.BackupOperationStatus.Success::class.java)
        
        // 3. Mutate data (Delete everything)
        db.restoreDao().clearAllInOrder()
        
        // 4. Inspect
        val inspection = coordinator.inspect(BackupDocumentUri(backupUri))
        assertThat(inspection).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        val ready = inspection as BackupArchiveInspectionResult.Ready
        assertThat(ready.eligibility).isEqualTo(BackupRestoreEligibility.Eligible)
        
        // 5. Apply
        val result = coordinator.apply(BackupDocumentUri(backupUri), ready.archive.fingerprint) {}
        assertThat(result).isEqualTo(BackupRestoreApplyResult.Success)
        
        // 6. Verify original data restored
        assertThat(db.restaurantDao().getById("r1")?.name).isEqualTo("Original")
        assertThat(db.inventoryAreaDao().getById("a1")?.name).isEqualTo("Area 1")
        assertThat(db.ingredientCategoryDao().getById("c1")?.name).isEqualTo("Cat 1")
        assertThat(db.ingredientDao().getById("i1")?.name).isEqualTo("Ing 1")
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
