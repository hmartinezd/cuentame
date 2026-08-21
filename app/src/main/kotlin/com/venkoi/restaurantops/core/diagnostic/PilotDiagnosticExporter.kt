package com.venkoi.restaurantops.core.diagnostic

import android.content.Context
import com.venkoi.restaurantops.core.common.AppVersionProvider
import com.venkoi.restaurantops.core.common.DeviceInfoProvider
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.dao.BackupDao
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.backup.api.BackupFormatV1Contract
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PilotDiagnosticReport(
    val diagnosticFormatVersion: Int,
    val generatedAt: String,
    val app: AppInfo,
    val device: DeviceInfo,
    val database: DatabaseInfo,
    val backup: BackupStatusInfo,
    val counts: Map<String, Int>
)

@Serializable
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String
)

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: Int
)

@Serializable
data class DatabaseInfo(
    val schemaVersion: Int,
    val integrityOk: Boolean,
    val foreignKeysOk: Boolean
)

@Serializable
data class BackupStatusInfo(
    val backupFormatVersion: Int,
    val lastAutoBackupSuccess: String?,
    val lastAutoBackupAttempt: String?,
    val lastAutoBackupResult: String?
)

@Singleton
class PilotDiagnosticExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val database: RestaurantInventoryDatabase,
    private val appVersionProvider: AppVersionProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: TimeProvider,
    private val json: Json
) {
    suspend fun generateReport(): String {
        val prefs = preferencesRepository.observePreferences().first()
        val snapshot = backupDao.createGlobalSnapshot()
        val sqlite = database.openHelper.readableDatabase
        val integrityOk = sqlite.query("PRAGMA quick_check").use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
        }
        val foreignKeysOk = sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            !cursor.moveToFirst()
        }
        
        val report = PilotDiagnosticReport(
            diagnosticFormatVersion = 1,
            generatedAt = DateTimeFormatter.ISO_INSTANT.format(timeProvider.now()),
            app = AppInfo(
                versionName = appVersionProvider.versionName,
                versionCode = appVersionProvider.versionCode,
                applicationId = appVersionProvider.applicationId
            ),
            device = DeviceInfo(
                manufacturer = deviceInfoProvider.manufacturer,
                model = deviceInfoProvider.model,
                androidVersion = deviceInfoProvider.sdkInt
            ),
            database = DatabaseInfo(
                schemaVersion = appVersionProvider.databaseSchemaVersion,
                integrityOk = integrityOk,
                foreignKeysOk = foreignKeysOk
            ),
            backup = BackupStatusInfo(
                backupFormatVersion = BackupFormatV1Contract.BACKUP_FORMAT_VERSION,
                lastAutoBackupSuccess = prefs.lastAutoBackupSuccessTimestamp?.let { DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochMilli(it)) },
                lastAutoBackupAttempt = prefs.lastAutoBackupAttemptTimestamp?.let { DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochMilli(it)) },
                lastAutoBackupResult = prefs.lastAutoBackupResult
            ),
            counts = mapOf(
                "restaurants" to snapshot.restaurants.size,
                "ingredients" to snapshot.ingredients.size,
                "suppliers" to snapshot.suppliers.size,
                "purchaseReceipts" to snapshot.purchaseReceipts.size,
                "wasteEvents" to snapshot.wasteEvents.size,
                "stockCounts" to snapshot.stockCounts.size,
                "menuRecipes" to snapshot.menuRecipes.size,
                "preparationRecipes" to snapshot.preparationRecipes.size,
                "productionBatches" to snapshot.productionBatches.size
            )
        )
        
        return json.encodeToString(report)
    }
}
