package com.miara.cuentame.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupValidationCode
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

    private fun createValidTableMetadata(dto: BackupSnapshotDto) = mapOf(
        "restaurants" to TableMetadata(dto.restaurants.size, false),
        "inventory_areas" to TableMetadata(dto.inventoryAreas.size, false),
        "ingredient_categories" to TableMetadata(dto.ingredientCategories.size, false),
        "units" to TableMetadata(dto.units.size, false),
        "ingredients" to TableMetadata(dto.ingredients.size, false),
        "ingredient_unit_options" to TableMetadata(dto.ingredientUnitOptions.size, false),
        "suppliers" to TableMetadata(dto.suppliers.size, false),
        "purchase_receipts" to TableMetadata(dto.purchaseReceipts.size, false),
        "purchase_lines" to TableMetadata(dto.purchaseLines.size, false),
        "stock_counts" to TableMetadata(dto.stockCounts.size, false),
        "stock_count_areas" to TableMetadata(dto.stockCountAreas.size, false),
        "stock_count_lines" to TableMetadata(dto.stockCountLines.size, false),
        "waste_events" to TableMetadata(dto.wasteEvents.size, false),
        "inventory_movements" to TableMetadata(dto.inventoryMovements.size, false),
        "inventory_balance_projections" to TableMetadata(dto.inventoryBalanceProjections.size, true),
        "ingredient_cost_projections" to TableMetadata(dto.ingredientCostProjections.size, true)
    )

    private fun createValidManifest(dto: BackupSnapshotDto) = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = "rest-1",
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = createValidTableMetadata(dto),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    private fun createEmptyDto() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto("rest-1", "Test Rest", "USD", "en-US", 0, 0, null)),
        inventoryAreas = emptyList(),
        ingredientCategories = emptyList(),
        units = emptyList(),
        ingredients = emptyList(),
        ingredientUnitOptions = emptyList(),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = emptyList(),
        stockCountAreas = emptyList(),
        stockCountLines = emptyList(),
        wasteEvents = emptyList(),
        inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )

    private fun createZip(file: File, manifest: BackupManifest, dto: BackupSnapshotDto) {
        val writer = Json { encodeDefaults = true; explicitNulls = true }
        val manifestJson = writer.encodeToString(manifest)
        val dbJson = writer.encodeToString(dto)
        val settingsJson = "{ \"themeMode\": \"SYSTEM\", \"dynamicColorEnabled\": true, \"appLocaleTag\": \"en-US\" }"
        
        val checksums = mutableMapOf<String, String>()
        fun addCheck(name: String, content: String) {
            checksums[name] = content.toByteArray().inputStream().use { checksumProvider.calculateChecksum(it) }
        }
        addCheck("manifest.json", manifestJson)
        addCheck("data/database.json", dbJson)
        addCheck("preferences/settings.json", settingsJson)
        
        val checksumsJson = writer.encodeToString(checksums)

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("data/database.json"))
            zos.write(dbJson.toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("preferences/settings.json"))
            zos.write(settingsJson.toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("checksums.json"))
            zos.write(checksumsJson.toByteArray())
            zos.closeEntry()
        }
    }

    @Test
    fun validate_rejectsDuplicateChecksumKey() = runBlocking {
        val file = File(context.cacheDir, "duplicate_checksum.zip")
        try {
            val dto = createEmptyDto()
            val manifest = createValidManifest(dto)
            val writer = Json { encodeDefaults = true }
            val manifestJson = writer.encodeToString(manifest)
            val dbJson = writer.encodeToString(dto)
            val settingsJson = "{ \"themeMode\": \"SYSTEM\", \"dynamicColorEnabled\": true, \"appLocaleTag\": \"en-US\" }"
            
            val manifestSum = manifestJson.toByteArray().inputStream().use { checksumProvider.calculateChecksum(it) }
            val checksumsJson = """{"manifest.json": "$manifestSum", "manifest.json": "$manifestSum"}"""

            ZipOutputStream(FileOutputStream(file)).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJson.toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("data/database.json"))
                zos.write(dbJson.toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("preferences/settings.json"))
                zos.write(settingsJson.toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("checksums.json"))
                zos.write(checksumsJson.toByteArray())
                zos.closeEntry()
            }

            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            assertThat((result as BackupValidationResult.Invalid).code).isEqualTo(BackupValidationCode.CHECKSUM_PARSE_FAILURE)
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsIncorrectBalanceProjectionValue() = runBlocking {
        val file = File(context.cacheDir, "bad_balance.zip")
        try {
            val dto = createEmptyDto().copy(
                ingredients = listOf(IngredientBackupDto("ing-1", "rest-1", "Ing", "ing", null, "u1", null, null, null, null, true, 0, 0, null)),
                inventoryAreas = listOf(InventoryAreaBackupDto("area-1", "rest-1", "Area", "area", 1, true, 0, 0, null)),
                units = listOf(UnitBackupDto("u1", "Unit", "u", "MASS", "1.0", true, 1)),
                purchaseReceipts = listOf(PurchaseReceiptBackupDto("p1", "rest-1", null, null, 0, "POSTED", null, null, 0, 0, 0, null)),
                purchaseLines = listOf(PurchaseLineBackupDto("l1", "p1", "ing-1", "area-1", "opt-1", "10.0", "10.0", "1.0", "10.0", null, 0, 0)),
                ingredientUnitOptions = listOf(IngredientUnitOptionBackupDto("opt-1", "ing-1", "kg", "kg", "u1", "1.0", true, true, true, true, 0, 0, null)),
                inventoryMovements = listOf(
                    InventoryMovementBackupDto("m1", "rest-1", "ing-1", "area-1", "PURCHASE", "10.0", "1.0", "10.0", 0, "PURCHASE_RECEIPT", "p1", "op1", "l1", null, 0)
                ),
                inventoryBalanceProjections = listOf(
                    InventoryBalanceProjectionBackupDto("rest-1", "ing-1", "area-1", "9.0", 0) // Should be 10.0
                )
            )
            val manifest = createValidManifest(dto)
            createZip(file, manifest, dto)

            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            assertThat((result as BackupValidationResult.Invalid).code).isEqualTo(BackupValidationCode.SNAPSHOT_INVALID)
            assertThat(result.reason).contains("match movement sum")
        } finally {
            file.delete()
        }
    }

    @Test
    fun validate_rejectsMissingBalanceProjection() = runBlocking {
        val file = File(context.cacheDir, "missing_balance.zip")
        try {
            val dto = createEmptyDto().copy(
                ingredients = listOf(IngredientBackupDto("ing-1", "rest-1", "Ing", "ing", null, "u1", null, null, null, null, true, 0, 0, null)),
                inventoryAreas = listOf(InventoryAreaBackupDto("area-1", "rest-1", "Area", "area", 1, true, 0, 0, null)),
                units = listOf(UnitBackupDto("u1", "Unit", "u", "MASS", "1.0", true, 1)),
                purchaseReceipts = listOf(PurchaseReceiptBackupDto("p1", "rest-1", null, null, 0, "POSTED", null, null, 0, 0, 0, null)),
                purchaseLines = listOf(PurchaseLineBackupDto("l1", "p1", "ing-1", "area-1", "opt-1", "10.0", "10.0", "1.0", "10.0", null, 0, 0)),
                ingredientUnitOptions = listOf(IngredientUnitOptionBackupDto("opt-1", "ing-1", "kg", "kg", "u1", "1.0", true, true, true, true, 0, 0, null)),
                inventoryMovements = listOf(
                    InventoryMovementBackupDto("m1", "rest-1", "ing-1", "area-1", "PURCHASE", "10.0", "1.0", "10.0", 0, "PURCHASE_RECEIPT", "p1", "op1", "l1", null, 0)
                ),
                inventoryBalanceProjections = emptyList() // Missing projection
            )
            val manifest = createValidManifest(dto)
            createZip(file, manifest, dto)

            val result = repository.validateBackup(Uri.fromFile(file).toString())
            assertThat(result).isInstanceOf(BackupValidationResult.Invalid::class.java)
            assertThat((result as BackupValidationResult.Invalid).code).isEqualTo(BackupValidationCode.SNAPSHOT_INVALID)
            assertThat(result.reason).contains("Missing balance projection")
        } finally {
            file.delete()
        }
    }
}
