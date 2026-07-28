package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.TableMetadata
import org.junit.Test

class BackupManifestHardeningTest {

    private val validTableMetadata = mapOf(
        "restaurants" to TableMetadata(1, false),
        "inventory_areas" to TableMetadata(1, false),
        "ingredient_categories" to TableMetadata(1, false),
        "units" to TableMetadata(1, false),
        "ingredients" to TableMetadata(1, false),
        "ingredient_unit_options" to TableMetadata(1, false),
        "suppliers" to TableMetadata(1, false),
        "purchase_receipts" to TableMetadata(1, false),
        "purchase_lines" to TableMetadata(1, false),
        "stock_counts" to TableMetadata(1, false),
        "stock_count_areas" to TableMetadata(1, false),
        "stock_count_lines" to TableMetadata(1, false),
        "waste_events" to TableMetadata(1, false),
        "inventory_movements" to TableMetadata(1, false),
        "inventory_balance_projections" to TableMetadata(1, true),
        "ingredient_cost_projections" to TableMetadata(1, true)
    )

    private val validManifest = BackupManifest(
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
        includedSections = listOf("attachments", "data", "preferences"),
        checksumAlgorithm = "SHA-256"
    )

    @Test
    fun `validate accepts valid manifest`() {
        assertThat(BackupManifestValidator.validate(validManifest).isSuccess).isTrue()
    }

    @Test
    fun `validate rejects non-canonical timestamp`() {
        val invalid = validManifest.copy(createdAtUtc = "2026-01-01T12:00:00.000Z") 
        val result = BackupManifestValidator.validate(invalid)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("canonical")
    }

    @Test
    fun `validate rejects invalid currency`() {
        val invalid = validManifest.copy(currencyCode = "INVALID")
        val result = BackupManifestValidator.validate(invalid)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("currencyCode")
    }

    @Test
    fun `validate rejects invalid locale`() {
        val invalid = validManifest.copy(localeTag = "invalid")
        assertThat(BackupManifestValidator.validate(invalid).isFailure).isTrue()
    }
    
    @Test
    fun `validate rejects missing table metadata keys`() {
        val invalid = validManifest.copy(tableMetadata = validTableMetadata.filterKeys { it != "ingredients" })
        val result = BackupManifestValidator.validate(invalid)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Missing: [ingredients]")
    }
}
