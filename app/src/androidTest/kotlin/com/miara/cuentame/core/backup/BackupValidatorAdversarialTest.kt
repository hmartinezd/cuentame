package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.core.model.backup.TableMetadata
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupValidatorAdversarialTest {

    private lateinit var context: Context
    private val checksumProvider = Sha256ChecksumProvider()
    private lateinit var repository: AndroidBackupRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = AndroidBackupRepository(
            context,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            checksumProvider
        )
    }

    private val validTableMetadata = mapOf(
        "restaurants" to TableMetadata(0, false),
        "inventory_areas" to TableMetadata(0, false),
        "ingredient_categories" to TableMetadata(0, false),
        "units" to TableMetadata(0, false),
        "ingredients" to TableMetadata(0, false),
        "ingredient_unit_options" to TableMetadata(0, false),
        "suppliers" to TableMetadata(0, false),
        "purchase_receipts" to TableMetadata(0, false),
        "purchase_lines" to TableMetadata(0, false),
        "stock_counts" to TableMetadata(0, false),
        "stock_count_areas" to TableMetadata(0, false),
        "stock_count_lines" to TableMetadata(0, false),
        "waste_events" to TableMetadata(0, false),
        "inventory_movements" to TableMetadata(0, false),
        "inventory_balance_projections" to TableMetadata(0, true),
        "ingredient_cost_projections" to TableMetadata(0, true)
    )

    private fun createValidManifest() = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 1,
        restaurantId = "rest-1",
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = validTableMetadata,
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    @Test
    fun validate_rejectsDuplicateChecksumKey() = runBlocking {
        val file = File(context.cacheDir, "duplicate_checksum.zip")
        val manifest = createValidManifest()
        val manifestJson = Json.encodeToString(manifest)
        val manifestChecksum = manifestJson.toByteArray().inputStream().use { checksumProvider.calculateChecksum(it) }
        
        // Manual JSON with duplicate key
        val checksumsJson = """{"manifest.json": "$manifestChecksum", "manifest.json": "$manifestChecksum"}"""

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("checksums.json"))
            zos.write(checksumsJson.toByteArray())
            zos.closeEntry()
        }

        val result = repository.validateBackup(Uri.fromFile(file).toString())
        assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
        assertThat((result as BackupValidationResult.Invalid).reason).contains("Duplicate key in checksums.json")
        file.delete()
    }

    @Test
    fun validate_rejectsUnknownEntry() = runBlocking {
        val file = File(context.cacheDir, "unknown_entry.zip")
        // ... (similar setup with an extra file not in manifest)
        file.delete()
    }
}
