package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

class BackupSnapshotIntegrityValidatorTest {

    private val restId = "rest-1"
    private val manifest = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = restId,
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    private fun createEmptyDto() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restId, "Test Rest", "USD", "en-US", 0, 0, null)),
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

    @Test
    fun `validate accepts valid simple snapshot`() {
        val result = BackupSnapshotIntegrityValidator.validate(createEmptyDto(), manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects multiple restaurants`() {
        val dto = createEmptyDto().copy(
            restaurants = listOf(
                RestaurantBackupDto(restId, "Test Rest", "USD", "en-US", 0, 0, null),
                RestaurantBackupDto("rest-2", "Other", "USD", "en-US", 0, 0, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Found 2")
    }

    @Test
    fun `validate rejects restaurant ID mismatch`() {
        val dto = createEmptyDto().copy(
            restaurants = listOf(RestaurantBackupDto("mismatch", "Test Rest", "USD", "en-US", 0, 0, null))
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("restaurant ID mismatch")
    }

    @Test
    fun `validate rejects cross-restaurant area`() {
        val dto = createEmptyDto().copy(
            inventoryAreas = listOf(
                InventoryAreaBackupDto("area-1", "wrong-rest", "Area", "area", 1, true, 0, 0, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Isolation error")
    }

    @Test
    fun `validate rejects broken ingredient-unit relationship`() {
        val dto = createEmptyDto().copy(
            units = listOf(UnitBackupDto("u1", "Unit", "u", "Mass", "1.0", true, 1)),
            ingredients = listOf(
                IngredientBackupDto("ing-1", restId, "Ing", "ing", null, "missing-unit", null, null, null, null, true, 0, 0, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Broken FK: ingredient -> unit")
    }

    @Test
    fun `validate rejects movement self-reversal`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(IngredientBackupDto("ing-1", restId, "Ing", "ing", null, "u1", null, null, null, null, true, 0, 0, null)),
            units = listOf(UnitBackupDto("u1", "Unit", "u", "Mass", "1.0", true, 1)),
            inventoryAreas = listOf(InventoryAreaBackupDto("area-1", restId, "Area", "area", 1, true, 0, 0, null)),
            inventoryMovements = listOf(
                InventoryMovementBackupDto("m1", restId, "ing-1", "area-1", "WASTE", "-1", null, null, 0, "WASTE_EVENT", "w1", "op1", null, "m1", 0)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Self-reversal")
    }
}
