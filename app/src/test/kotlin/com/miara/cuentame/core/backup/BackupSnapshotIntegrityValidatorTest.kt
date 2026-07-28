package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.BackupSnapshot
import org.junit.Test
import java.math.BigDecimal

class BackupSnapshotIntegrityValidatorTest {

    private val restId = "rest-1"
    private val manifest = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 1,
        restaurantId = restId,
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    private fun createEmptySnapshot() = BackupSnapshot(
        restaurants = listOf(RestaurantEntity(restId, "Test Rest", "USD", "en-US", 0L, 0L, null)),
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
        val result = BackupSnapshotIntegrityValidator.validate(createEmptySnapshot(), manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects multiple restaurants`() {
        val snapshot = createEmptySnapshot().copy(
            restaurants = listOf(
                RestaurantEntity(restId, "Test Rest", "USD", "en-US", 0L, 0L, null),
                RestaurantEntity("rest-2", "Other", "USD", "en-US", 0L, 0L, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(snapshot, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Found 2")
    }

    @Test
    fun `validate rejects restaurant ID mismatch`() {
        val snapshot = createEmptySnapshot().copy(
            restaurants = listOf(RestaurantEntity("mismatch", "Test Rest", "USD", "en-US", 0L, 0L, null))
        )
        val result = BackupSnapshotIntegrityValidator.validate(snapshot, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("does not match manifest")
    }

    @Test
    fun `validate rejects cross-restaurant area`() {
        val snapshot = createEmptySnapshot().copy(
            inventoryAreas = listOf(
                InventoryAreaEntity("area-1", "wrong-rest", "Area", "area", 1, true, 0L, 0L, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(snapshot, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("data for another restaurant")
    }

    @Test
    fun `validate rejects broken ingredient-unit relationship`() {
        val snapshot = createEmptySnapshot().copy(
            units = listOf(UnitEntity("u1", "Unit", "u", "Mass", BigDecimal.ONE, true, 1)),
            ingredients = listOf(
                IngredientEntity("ing-1", restId, "Ing", "ing", null, "missing-unit", null, null, null, null, true, 0L, 0L, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(snapshot, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("unknown unit")
    }

    @Test
    fun `validate rejects movement self-reversal`() {
        val snapshot = createEmptySnapshot().copy(
            ingredients = listOf(IngredientEntity("ing-1", restId, "Ing", "ing", null, "u1", null, null, null, null, true, 0L, 0L, null)),
            units = listOf(UnitEntity("u1", "Unit", "u", "Mass", BigDecimal.ONE, true, 1)),
            inventoryAreas = listOf(InventoryAreaEntity("area-1", restId, "Area", "area", 1, true, 0L, 0L, null)),
            inventoryMovements = listOf(
                InventoryMovementEntity("m1", restId, "ing-1", "area-1", "WASTE", "-1", null, null, 0L, "WASTE_EVENT", "w1", "op1", null, "m1", 0L)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(snapshot, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("reverses itself")
    }
}
