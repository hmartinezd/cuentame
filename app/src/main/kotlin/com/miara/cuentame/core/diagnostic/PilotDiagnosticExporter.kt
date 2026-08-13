package com.miara.cuentame.core.diagnostic

import android.content.Context
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.DeviceInfoProvider
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.dao.BackupDao
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
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
    val integrityOk: Boolean
)

@Serializable
data class BackupStatusInfo(
    val lastAutoBackupSuccess: String?,
    val lastAutoBackupAttempt: String?,
    val lastAutoBackupResult: String?
)

@Singleton
class PilotDiagnosticExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val appVersionProvider: AppVersionProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val preferencesRepository: AppPreferencesRepository,
    private val timeProvider: TimeProvider,
    private val json: Json
) {
    suspend fun generateReport(): String {
        val prefs = preferencesRepository.observePreferences().first()
        val snapshot = backupDao.createGlobalSnapshot()
        
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
                integrityOk = true
            ),
            backup = BackupStatusInfo(
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
