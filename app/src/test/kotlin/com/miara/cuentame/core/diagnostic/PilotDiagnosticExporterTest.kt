package com.miara.cuentame.core.diagnostic

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.DeviceInfoProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import android.database.Cursor
import com.miara.cuentame.core.database.backup.BackupSnapshot
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Test
import java.time.Instant

class PilotDiagnosticExporterTest {

    private val backupDao = mockk<BackupDao>()
    private val database = mockk<RestaurantInventoryDatabase>()
    private val appVersionProvider = mockk<AppVersionProvider>()
    private val deviceInfoProvider = mockk<DeviceInfoProvider>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var exporter: PilotDiagnosticExporter

    @Before
    fun setup() {
        exporter = PilotDiagnosticExporter(
            context = mockk(),
            backupDao = backupDao,
            database = database,
            appVersionProvider = appVersionProvider,
            deviceInfoProvider = deviceInfoProvider,
            preferencesRepository = preferencesRepository,
            timeProvider = timeProvider,
            json = json
        )

        every { deviceInfoProvider.manufacturer } returns "TestManufacturer"
        every { deviceInfoProvider.model } returns "TestModel"
        every { deviceInfoProvider.sdkInt } returns 35

        every { appVersionProvider.versionName } returns "1.0-test"
        every { appVersionProvider.versionCode } returns 100L
        every { appVersionProvider.applicationId } returns "com.miara.test"
        every { appVersionProvider.databaseSchemaVersion } returns 12
        every { timeProvider.now() } returns Instant.parse("2026-08-12T22:00:00Z")
        val sqlite = mockk<androidx.sqlite.db.SupportSQLiteDatabase>()
        every { database.openHelper.readableDatabase } returns sqlite
        every { sqlite.query("PRAGMA quick_check") } returns cursorWith(first = true, value = "ok")
        every { sqlite.query("PRAGMA foreign_key_check") } returns cursorWith(first = false)
        
        coEvery { preferencesRepository.observePreferences() } returns flowOf(
            AppPreferences.DEFAULT.copy(
                lastAutoBackupSuccessTimestamp = 1755034200000L,
                lastAutoBackupResult = "SUCCESS"
            )
        )
    }

    @Test
    fun `generateReport contains correct technical metadata and counts`() = runBlocking {
        val snapshot = BackupSnapshot(
            restaurants = listOf(RestaurantEntity("r1", "Secret Restaurant Name", "USD", "en-US", 0, 0, null)),
            inventoryAreas = emptyList(),
            ingredientCategories = emptyList(),
            units = emptyList(),
            ingredients = List(5) { mockk() },
            ingredientUnitOptions = emptyList(),
            suppliers = List(2) { mockk() },
            purchaseReceipts = emptyList(),
            purchaseLines = emptyList(),
            stockCounts = emptyList(),
            stockCountAreas = emptyList(),
            stockCountLines = emptyList(),
            wasteEvents = emptyList(),
            inventoryMovements = emptyList(),
            inventoryBalanceProjections = emptyList(),
            ingredientCostProjections = emptyList(),
            preparationRecipes = emptyList(),
            preparationRecipeComponents = emptyList(),
            productionBatches = emptyList(),
            productionBatchComponents = emptyList(),
            purchaseInvoiceOcrResults = emptyList(),
            purchaseInvoiceOcrPages = emptyList(),
            purchaseInvoiceParseResults = emptyList(),
            purchaseInvoiceParsedLines = emptyList(),
            supplierItemMappings = emptyList(),
            purchaseInvoiceLineMatches = emptyList(),
            purchaseInvoiceDraftApplications = emptyUnitOrigins(),
            purchaseInvoiceLineOrigins = emptyList(),
            stockCountItemOrder = emptyList(),
            menuRecipes = List(3) { mockk() },
            menuRecipeComponents = emptyList()
        )

        coEvery { backupDao.createGlobalSnapshot() } returns snapshot

        val reportJson = exporter.generateReport()
        val report = json.parseToJsonElement(reportJson).jsonObject

        assertThat(report["diagnosticFormatVersion"]?.jsonPrimitive?.content).isEqualTo("1")
        assertThat(report["app"]?.jsonObject?.get("versionName")?.jsonPrimitive?.content).isEqualTo("1.0-test")
        
        val counts = report["counts"]?.jsonObject
        assertThat(counts?.get("restaurants")?.jsonPrimitive?.content).isEqualTo("1")
        assertThat(counts?.get("ingredients")?.jsonPrimitive?.content).isEqualTo("5")
        assertThat(counts?.get("menuRecipes")?.jsonPrimitive?.content).isEqualTo("3")

        // Privacy check: Ensure secret business data is NOT in the JSON
        assertThat(reportJson).doesNotContain("Secret Restaurant Name")
    }

    // Helper to avoid type mismatch in BackupSnapshot constructor if I misread the order/types
    private fun emptyUnitOrigins() = emptyList<com.miara.cuentame.core.database.entity.PurchaseInvoiceDraftApplicationEntity>()

    private fun cursorWith(first: Boolean, value: String = "") = mockk<Cursor>().also {
        every { it.moveToFirst() } returns first
        every { it.getString(0) } returns value
        every { it.close() } returns Unit
    }
}
