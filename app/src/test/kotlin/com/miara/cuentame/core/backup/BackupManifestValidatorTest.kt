package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.TableMetadata
import org.junit.Test
import java.time.Instant

class BackupManifestValidatorTest {

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
        createdAtUtc = Instant.now().toString(),
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
        includedSections = listOf("data", "preferences", "attachments")
    )

    @Test
    fun `validate accepts valid manifest`() {
        assertThat(BackupManifestValidator.validate(validManifest).isSuccess).isTrue()
    }

    @Test
    fun `validate rejects wrong version`() {
        val invalid = validManifest.copy(backupFormatVersion = 2)
        assertThat(BackupManifestValidator.validate(invalid).isFailure).isTrue()
    }

    @Test
    fun `validate rejects malformed timestamp`() {
        val invalid = validManifest.copy(createdAtUtc = "not-a-date")
        assertThat(BackupManifestValidator.validate(invalid).isFailure).isTrue()
    }

    @Test
    fun `validate rejects blank restaurant ID`() {
        val invalid = validManifest.copy(restaurantId = "")
        assertThat(BackupManifestValidator.validate(invalid).isFailure).isTrue()
    }

    @Test
    fun `validate rejects missing sections`() {
        val invalid = validManifest.copy(includedSections = listOf("data"))
        assertThat(BackupManifestValidator.validate(invalid).isFailure).isTrue()
    }

    @Test
    fun `validate rejects missing table`() {
        val invalid = validManifest.copy(tableMetadata = validTableMetadata.filterKeys { it != "ingredients" })
        assertThat(BackupManifestValidator.validate(invalid).isFailure).isTrue()
    }

    @Test
    fun `validate rejects incorrect isDerived flag`() {
        val invalid = validManifest.copy(tableMetadata = validTableMetadata.toMutableMap().apply {
            put("ingredients", TableMetadata(1, true))
        })
        assertThat(BackupManifestValidator.validate(invalid).isFailure).isTrue()
    }
}
