package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.model.backup.BackupSnapshot
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var context: Context
    private val backupDao = mockk<BackupDao>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val checksumProvider = Sha256ChecksumProvider()

    private lateinit var repository: AndroidBackupRepository

    private val jsonReader = Json { ignoreUnknownKeys = false; isLenient = false }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = AndroidBackupRepository(
            context,
            backupDao,
            restaurantRepository,
            preferencesRepository,
            timeProvider,
            appVersionProvider,
            checksumProvider
        )
    }

    @Test
    fun backup_roundTrip_all16TablesAndAttachments() = runTest {
        val tempFile = File(context.cacheDir, "roundtrip_full.zip")
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        val backupUri = Uri.fromFile(tempFile)

        val now = Instant.parse("2026-01-01T10:00:00Z")
        every { timeProvider.now() } returns now
        every { appVersionProvider.applicationId } returns "com.miara.cuentame"
        every { appVersionProvider.versionName } returns "1.0"
        every { appVersionProvider.versionCode } returns 1L
        every { appVersionProvider.databaseSchemaVersion } returns 2

        val customPrefs = AppPreferences(
            onboardingCompleted = true,
            themeMode = ThemeMode.DARK,
            dynamicColorEnabled = true,
            appLocaleTag = "es-US"
        )
        every { preferencesRepository.observePreferences() } returns flowOf(customPrefs)

        val restId = "rest-1"
        val restaurant = com.miara.cuentame.core.model.restaurant.Restaurant(
            RestaurantId(restId), "Cuentame Rest", "USD", "en-US", Instant.EPOCH, Instant.EPOCH
        )
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        // Create dummy attachment files
        val attFile1Bytes = byteArrayOf(10, 20, 30, 40)
        val attFile1 = File(context.cacheDir, "purchase_receipt.jpg").apply { writeBytes(attFile1Bytes) }
        val attUri1 = Uri.fromFile(attFile1)

        val attFile2Bytes = byteArrayOf(50, 60, 70)
        val attFile2 = File(context.cacheDir, "waste_photo.jpg").apply { writeBytes(attFile2Bytes) }
        val attUri2 = Uri.fromFile(attFile2)

        // Populate all 16 tables with referentially valid data
        val restaurants = listOf(RestaurantEntity(restId, "Cuentame Rest", "USD", "en-US", 100, 200, null))
        val areas = listOf(
            InventoryAreaEntity("area-1", restId, "Freezer", "freezer", 1, true, 100, 200, null),
            InventoryAreaEntity("area-2", restId, "Dry", "dry", 2, true, 100, 200, null)
        )
        val categories = listOf(IngredientCategoryEntity("cat-1", restId, "Produce", "produce", 1, true, 100, 200, null))
        val units = listOf(
            UnitEntity("u-1", "Kilogram", "kg", "Mass", BigDecimal.ONE, true, 1),
            UnitEntity("u-2", "Gram", "g", "Mass", BigDecimal("0.001"), true, 2)
        )
        val ingredients = listOf(
            IngredientEntity("ing-1", restId, "Tomato", "tomato", "cat-1", "u-1", "area-1", "SKU1", "Fresh", BigDecimal("10.0"), true, 100, 200, null),
            IngredientEntity("ing-2", restId, "Onion", "onion", "cat-1", "u-1", "area-2", "SKU2", "Yellow", BigDecimal("5.0"), true, 100, 200, null)
        )
        val options = listOf(
            IngredientUnitOptionEntity("opt-1", "ing-1", "1kg Bag", "kg", "u-1", BigDecimal.ONE, true, true, true, true, 100, 200, null),
            IngredientUnitOptionEntity("opt-2", "ing-2", "1kg Bag", "kg", "u-1", BigDecimal.ONE, true, true, true, true, 100, 200, null)
        )
        val suppliers = listOf(SupplierEntity("sup-1", restId, "Farm Co", "farm co", "555-1234", "farm@test.com", "Good", true, 100, 200, null))

        val receipts = listOf(PurchaseReceiptEntity("pr-1", restId, "sup-1", "INV-001", 1000, "POSTED", "Bought tomatoes", attUri1.toString(), 100, 200, 300, null))
        val purchaseLines = listOf(PurchaseLineEntity("pl-1", "pr-1", "ing-1", "area-1", "opt-1", "5.0", "5.0", "2.00", "10.00", null, 100, 200))

        val stockCounts = listOf(StockCountEntity("sc-1", restId, "Monthly Count", 1000, 1000, 1100, "COMPLETED", null, 100, 200, null))
        val stockCountAreas = listOf(StockCountAreaEntity("sca-1", "sc-1", "area-1", "COMPLETED", 1000, 1100, 1))
        val stockCountLines = listOf(StockCountLineEntity("scl-1", "sca-1", "ing-1", "opt-1", "4.5", "4.5", "5.0", "-0.5", null, 100, 200))

        val wasteEvents = listOf(WasteEventEntity("we-1", restId, "ing-1", "area-1", "opt-1", "0.5", "0.5", "Spoiled", 1050, "Mould", attUri2.toString(), "POSTED", 100, 200, 300, null))

        val movements = listOf(
            InventoryMovementEntity("m-1", restId, "ing-1", "area-1", "PURCHASE_POST", "5.0", "2.00", "10.00", 1000, "PURCHASE_RECEIPT", "pr-1", "op-1", "pl-1", null, 100),
            InventoryMovementEntity("m-2", restId, "ing-1", "area-1", "WASTE_POST", "-0.5", "2.00", "-1.00", 1050, "WASTE_EVENT", "we-1", "op-2", null, null, 105)
        )

        val balanceProjections = listOf(
            InventoryBalanceProjectionEntity(restId, "ing-1", "area-1", "4.5", 200),
            InventoryBalanceProjectionEntity(restId, "ing-2", "area-2", "0.0", 200)
        )

        val costProjections = listOf(
            IngredientCostProjectionEntity(restId, "ing-1", "2.00", 200),
            IngredientCostProjectionEntity(restId, "ing-2", null, 200) // Nullable cost projection
        )

        val snapshot = BackupSnapshot(
            restaurants = restaurants,
            inventoryAreas = areas,
            ingredientCategories = categories,
            units = units,
            ingredients = ingredients,
            ingredientUnitOptions = options,
            suppliers = suppliers,
            purchaseReceipts = receipts,
            purchaseLines = purchaseLines,
            stockCounts = stockCounts,
            stockCountAreas = stockCountAreas,
            stockCountLines = stockCountLines,
            wasteEvents = wasteEvents,
            inventoryMovements = movements,
            inventoryBalanceProjections = balanceProjections,
            ingredientCostProjections = costProjections
        )
        coEvery { backupDao.createSnapshot(restId) } returns snapshot

        try {
            // 1. Create Backup
            val results = repository.createBackup(backupUri.toString()).toList()
            assertThat(results.last()).isInstanceOf(BackupOperationStatus.Success::class.java)

            // 2. Validate Backup
            val validation = repository.validateBackup(backupUri.toString())
            assertThat(validation).isInstanceOf(BackupValidationResult.Valid::class.java)
            val valid = validation as BackupValidationResult.Valid

            assertThat(valid.manifest.databaseSchemaVersion).isEqualTo(2)
            assertThat(valid.manifest.attachments).hasSize(2)
            assertThat(valid.manifest.tableMetadata["restaurants"]?.entryCount).isEqualTo(1)
            assertThat(valid.manifest.tableMetadata["ingredients"]?.entryCount).isEqualTo(2)
            assertThat(valid.manifest.tableMetadata["ingredient_cost_projections"]?.entryCount).isEqualTo(2)

            // 3. Inspect archive entries directly
            var dbDto: BackupSnapshotDto? = null
            var prefsDto: BackupPreferencesDto? = null
            val attachmentBytesRead = mutableMapOf<String, ByteArray>()

            val stream: InputStream? = context.contentResolver.openInputStream(backupUri)
            assertThat(stream).isNotNull()
            stream!!.use { ins ->
                ZipInputStream(ins).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val baos = ByteArrayOutputStream()
                        zis.copyTo(baos)
                        val bytes = baos.toByteArray()

                        if (name == "data/database.json") {
                            dbDto = jsonReader.decodeFromString<BackupSnapshotDto>(bytes.toString(Charsets.UTF_8))
                        } else if (name == "preferences/settings.json") {
                            prefsDto = jsonReader.decodeFromString<BackupPreferencesDto>(bytes.toString(Charsets.UTF_8))
                        } else if (name.startsWith("attachments/")) {
                            attachmentBytesRead[name] = bytes
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Assert DTO fields
            assertThat(dbDto).isNotNull()
            assertThat(dbDto?.restaurants).hasSize(1)
            assertThat(dbDto?.inventoryAreas).hasSize(2)
            assertThat(dbDto?.ingredients).hasSize(2)
            assertThat(dbDto?.ingredientCostProjections).hasSize(2)

            // Assert nullable cost projection
            val costIng2 = dbDto?.ingredientCostProjections?.find { it.ingredientId == "ing-2" }
            assertThat(costIng2).isNotNull()
            assertThat(costIng2?.averageUnitCostBase).isNull()

            // Assert non-default preferences
            assertThat(prefsDto).isNotNull()
            assertThat(prefsDto?.themeMode).isEqualTo("DARK")
            assertThat(prefsDto?.dynamicColorEnabled).isTrue()
            assertThat(prefsDto?.appLocaleTag).isEqualTo("es-US")

            // Assert attachment bytes
            assertThat(attachmentBytesRead.size).isEqualTo(2)
            val readAtt1 = attachmentBytesRead.values.find { it.contentEquals(attFile1Bytes) }
            val readAtt2 = attachmentBytesRead.values.find { it.contentEquals(attFile2Bytes) }
            assertThat(readAtt1).isNotNull()
            assertThat(readAtt2).isNotNull()

        } finally {
            tempFile.delete()
            attFile1.delete()
            attFile2.delete()
        }
    }
}
