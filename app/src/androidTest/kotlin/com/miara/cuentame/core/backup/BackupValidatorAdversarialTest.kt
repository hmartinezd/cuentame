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

    @Test
    fun validate_rejectsMissingManifest() {
        runBlocking {
            val file = File(context.cacheDir, "missing_manifest.zip")
            ZipOutputStream(FileOutputStream(file)).use { zos ->
                zos.putNextEntry(ZipEntry("data/database.json"))
                zos.write("{}".toByteArray())
                zos.closeEntry()
            }

            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            assertThat((result as BackupValidationResult.Invalid).reason).contains("Missing manifest.json")
            file.delete()
        }
    }

    @Test
    fun validate_rejectsUnsafePath() {
        runBlocking {
            val file = File(context.cacheDir, "unsafe_path.zip")
            ZipOutputStream(FileOutputStream(file)).use { zos ->
                try {
                    zos.putNextEntry(ZipEntry("../traversal.json"))
                    zos.write("{}".toByteArray())
                    zos.closeEntry()
                } catch (e: Exception) {}
            }

            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val reason = (result as BackupValidationResult.Invalid).reason
            assertThat(reason.contains("Unsafe entry name") || reason.contains("Invalid zip entry path")).isTrue()
            file.delete()
        }
    }

    @Test
    fun validate_rejectsMissingDatabase() {
        runBlocking {
            val file = File(context.cacheDir, "missing_db.zip")
            val tableMetadata = mapOf(
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
            val manifest = BackupManifest(
                backupFormatVersion = 1,
                createdAtUtc = Instant.now().toString(),
                applicationId = "com.miara.cuentame",
                appVersionName = "1.0",
                appVersionCode = 1L,
                databaseSchemaVersion = 1,
                restaurantId = "rest-1",
                restaurantName = "Test Rest",
                localeTag = "en-US",
                currencyCode = "USD",
                tableMetadata = tableMetadata,
                attachments = emptyList(),
                includedSections = listOf("data", "preferences", "attachments"),
                checksumAlgorithm = "SHA-256"
            )
            val manifestJson = Json.encodeToString(manifest)
            val manifestChecksum = manifestJson.toByteArray().inputStream().use { checksumProvider.calculateChecksum(it) }
            val checksums = mapOf("manifest.json" to manifestChecksum)

            ZipOutputStream(FileOutputStream(file)).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJson.toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("checksums.json"))
                zos.write(Json.encodeToString(checksums).toByteArray())
                zos.closeEntry()
            }

            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            val reason = (result as BackupValidationResult.Invalid).reason
            assertThat(reason).contains("Missing data/database.json")
            file.delete()
        }
    }
}
