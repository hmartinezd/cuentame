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
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
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
    fun backup_roundTrip_postedAndVoidedLifecycleGraphs_exhaustiveVerification() = runTest {
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

        val restId = BackupTestFixtures.RESTAURANT_ID
        val restaurant = com.miara.cuentame.core.model.restaurant.Restaurant(
            RestaurantId(restId), "Test Restaurant", "USD", "en-US", Instant.EPOCH, Instant.EPOCH
        )
        coEvery { restaurantRepository.getRestaurant() } returns restaurant

        val attFile1Bytes = byteArrayOf(10, 20, 30, 40)
        val attFile1 = File(context.cacheDir, "purchase_receipt.jpg").apply { writeBytes(attFile1Bytes) }
        val attUri1 = Uri.fromFile(attFile1)

        val attFile2Bytes = byteArrayOf(50, 60, 70)
        val attFile2 = File(context.cacheDir, "waste_photo.jpg").apply { writeBytes(attFile2Bytes) }
        val attUri2 = Uri.fromFile(attFile2)

        val snapshot = BackupTestFixtures.createPostedLifecycleSnapshot(
            restaurantId = restId,
            purchaseAttPath = attUri1.toString(),
            wasteAttPath = attUri2.toString()
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
            assertThat(valid.manifest.tableMetadata["inventory_areas"]?.entryCount).isEqualTo(2)
            assertThat(valid.manifest.tableMetadata["ingredients"]?.entryCount).isEqualTo(2)
            assertThat(valid.manifest.tableMetadata["inventory_movements"]?.entryCount).isEqualTo(3)
            assertThat(valid.manifest.tableMetadata["ingredient_cost_projections"]?.entryCount).isEqualTo(2)

            // 3. Inspect archive entries directly
            var dbDto: BackupSnapshotDto? = null
            var prefsDto: BackupPreferencesDto? = null
            var reportedChecksums: Map<String, String>? = null
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

                        when {
                            name == "data/database.json" -> {
                                dbDto = jsonReader.decodeFromString<BackupSnapshotDto>(bytes.toString(Charsets.UTF_8))
                            }
                            name == "preferences/settings.json" -> {
                                prefsDto = jsonReader.decodeFromString<BackupPreferencesDto>(bytes.toString(Charsets.UTF_8))
                            }
                            name == "checksums.json" -> {
                                reportedChecksums = jsonReader.decodeFromString<Map<String, String>>(bytes.toString(Charsets.UTF_8))
                            }
                            name.startsWith("attachments/") -> {
                                attachmentBytesRead[name] = bytes
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Assert Checksums JSON self-exclusion
            assertThat(reportedChecksums).isNotNull()
            assertThat(reportedChecksums!!.containsKey("checksums.json")).isFalse()

            // Assert DTO fields & lists
            assertThat(dbDto).isNotNull()
            assertThat(dbDto?.restaurants).hasSize(1)
            assertThat(dbDto?.inventoryAreas).hasSize(2)
            assertThat(dbDto?.ingredients).hasSize(2)
            assertThat(dbDto?.inventoryMovements).hasSize(3)
            assertThat(dbDto?.ingredientCostProjections).hasSize(2)

            // Assert nullable cost projection
            val costIng2 = dbDto?.ingredientCostProjections?.find { it.ingredientId == "ing-2" }
            assertThat(costIng2).isNotNull()
            assertThat(costIng2?.averageUnitCostBase).isNull()

            // Assert Preferences DTO
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

            // 4. Test Voided Lifecycle Snapshot Round-Trip
            val voidedSnapshot = BackupTestFixtures.createVoidedLifecycleSnapshot(restaurantId = restId)
            coEvery { backupDao.createSnapshot(restId) } returns voidedSnapshot

            val voidedResults = repository.createBackup(backupUri.toString()).toList()
            assertThat(voidedResults.last()).isInstanceOf(BackupOperationStatus.Success::class.java)

            val voidedValidation = repository.validateBackup(backupUri.toString())
            assertThat(voidedValidation).isInstanceOf(BackupValidationResult.Valid::class.java)
            val validVoided = voidedValidation as BackupValidationResult.Valid
            assertThat(validVoided.manifest.tableMetadata["inventory_movements"]?.entryCount).isEqualTo(4) // PURCHASE + WASTE + COUNT + REVERSAL

        } finally {
            tempFile.delete()
            attFile1.delete()
            attFile2.delete()
        }
    }
}
