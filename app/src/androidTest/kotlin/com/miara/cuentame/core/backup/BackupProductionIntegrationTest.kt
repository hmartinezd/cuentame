package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.database.repository.*
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.CreateWasteDraftCommand
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BackupProductionIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: RestaurantInventoryDatabase

    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val checksumProvider = Sha256ChecksumProvider()

    private var idCounter = 0
    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "gen-id-${++idCounter}"
    }

    private lateinit var backupRepository: AndroidBackupRepository
    private lateinit var purchaseRepository: RoomPurchaseRepository
    private lateinit var wasteRepository: RoomWasteRepository
    private lateinit var restaurantRepository: RoomRestaurantRepository

    private val restaurantIdStr = BackupTestFixtures.RESTAURANT_ID

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val now = Instant.parse("2026-01-01T12:00:00Z")
        every { timeProvider.now() } returns now
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 2
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences.DEFAULT)

        val costCalculator = WeightedAverageCostCalculator()
        val movementValidator = InventoryMovementValidator()

        val projectionRebuilder = RoomInventoryProjectionRebuilder(
            db, db.ingredientDao(), db.inventoryMovementDao(), db.inventoryProjectionDao(),
            db.ingredientCostProjectionDao(), costCalculator, timeProvider
        )

        val purchaseRefValidator = PurchaseReferenceValidator(
            db.purchaseDao(), db.supplierDao(), db.ingredientDao(), db.inventoryAreaDao(), db.ingredientUnitOptionDao()
        )

        restaurantRepository = RoomRestaurantRepository(db.restaurantDao())

        purchaseRepository = RoomPurchaseRepository(
            db, db.purchaseDao(), db.supplierDao(), db.ingredientDao(),
            db.ingredientUnitOptionDao(), db.inventoryAreaDao(), db.inventoryMovementDao(),
            db.restaurantDao(), projectionRebuilder, purchaseRefValidator, PurchaseLineCalculator(),
            PurchaseMovementHistoryValidator(), idGenerator, timeProvider
        )

        val wasteSnapshotService = RoomInventorySnapshotService(db.inventoryMovementDao(), costCalculator, movementValidator)
        val wasteHistoryValidator = WasteMovementHistoryValidator(movementValidator)

        wasteRepository = RoomWasteRepository(
            db, db.wasteDao(), db.inventoryMovementDao(), db.ingredientDao(),
            db.inventoryAreaDao(), db.ingredientUnitOptionDao(), db.restaurantDao(),
            wasteSnapshotService, wasteHistoryValidator, projectionRebuilder, idGenerator, timeProvider,
            NoOpFailureBoundary()
        )

        backupRepository = AndroidBackupRepository(
            context,
            db.backupDao(),
            restaurantRepository,
            preferencesRepository,
            timeProvider,
            appVersionProvider,
            checksumProvider
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun productionRepositories_createAndValidateBackup_withRealPostedAndVoidedData() = runBlocking {
        // 1. Seed Restaurant & Reference Entities into DB
        db.restaurantDao().insert(
            RestaurantEntity(
                id = restaurantIdStr,
                name = "Prod Restaurant",
                currencyCode = "USD",
                localeTag = "en-US",
                createdAt = 1000L,
                updatedAt = 2000L,
                deletedAt = null
            )
        )

        val area1 = InventoryAreaEntity(
            id = "area-1",
            restaurantId = restaurantIdStr,
            name = "Kitchen",
            normalizedName = "kitchen",
            sortOrder = 1,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )
        db.inventoryAreaDao().upsert(area1)

        val unit1 = UnitEntity(
            id = "u-1",
            name = "Kilogram",
            symbol = "kg",
            dimension = UnitDimension.MASS.name,
            factorToCanonical = BigDecimal.ONE,
            isSystem = true,
            sortOrder = 1
        )
        db.unitDao().insertSeedUnits(listOf(unit1))

        val ing1 = IngredientEntity(
            id = "ing-1",
            restaurantId = restaurantIdStr,
            name = "Tomato",
            normalizedName = "tomato",
            categoryId = null,
            baseUnitId = unit1.id,
            defaultAreaId = area1.id,
            sku = "SKU-TOM",
            notes = "Fresh Tomatoes",
            reorderPointBase = BigDecimal("10.0"),
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )
        db.ingredientDao().insert(ing1)

        val opt1 = IngredientUnitOptionEntity(
            id = "opt-1",
            ingredientId = ing1.id,
            displayName = "1kg Bag",
            shortLabel = "kg",
            standardUnitId = unit1.id,
            factorToBase = BigDecimal.ONE,
            isBase = true,
            isDefaultCount = true,
            isDefaultPurchase = true,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )
        db.ingredientUnitOptionDao().insert(opt1)

        val supplier1 = SupplierEntity(
            id = "sup-1",
            restaurantId = restaurantIdStr,
            name = "Supplier Co",
            normalizedName = "supplier co",
            phone = null,
            email = null,
            notes = null,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null
        )
        db.supplierDao().insert(supplier1)

        // 2. Create and Post Purchase via RoomPurchaseRepository
        val purchaseId = purchaseRepository.createDraft(
            CreatePurchaseDraftCommand(
                restaurantId = RestaurantId(restaurantIdStr),
                supplierId = SupplierId(supplier1.id),
                invoiceNumber = "INV-200",
                purchaseDate = Instant.parse("2026-01-01T12:00:00Z"),
                notes = "Initial purchase"
            )
        )

        purchaseRepository.saveLine(
            SavePurchaseLineCommand(
                receiptId = purchaseId,
                lineId = null,
                ingredientId = IngredientId(ing1.id),
                areaId = InventoryAreaId(area1.id),
                ingredientUnitOptionId = IngredientUnitOptionId(opt1.id),
                quantityEntered = BigDecimal("20.0"),
                lineTotal = BigDecimal("60.00"),
                notes = null
            )
        )
        purchaseRepository.post(purchaseId)

        // 3. Create and Post Waste via RoomWasteRepository
        val wasteId = wasteRepository.createDraft(
            CreateWasteDraftCommand(
                restaurantId = RestaurantId(restaurantIdStr),
                ingredientId = IngredientId(ing1.id),
                areaId = InventoryAreaId(area1.id),
                ingredientUnitOptionId = IngredientUnitOptionId(opt1.id),
                quantityEntered = BigDecimal("2.0"),
                reason = WasteReason.SPOILED,
                effectiveAt = Instant.parse("2026-01-01T11:00:00Z"),
                notes = "Spoiled tomato",
                attachmentUri = null
            )
        )
        wasteRepository.post(wasteId)

        // 4. Void Waste Event to verify REVERSAL generation
        wasteRepository.void(wasteId)

        // 5. Backup Creation & Validation
        val tempFile = File(context.cacheDir, "prod_integration_backup.zip")
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        val backupUri = Uri.fromFile(tempFile)

        try {
            val createResults = backupRepository.createBackup(backupUri.toString()).toList()
            assertThat(createResults.last()).isInstanceOf(BackupOperationStatus.Success::class.java)

            val validation = backupRepository.validateBackup(backupUri.toString())
            assertThat(validation).isInstanceOf(BackupValidationResult.Valid::class.java)

            val valid = validation as BackupValidationResult.Valid
            assertThat(valid.manifest.tableMetadata["restaurants"]?.entryCount).isEqualTo(1)
            assertThat(valid.manifest.tableMetadata["inventory_movements"]?.entryCount).isEqualTo(3) // 1 PURCHASE + 1 WASTE + 1 REVERSAL
        } finally {
            tempFile.delete()
        }
    }
}
