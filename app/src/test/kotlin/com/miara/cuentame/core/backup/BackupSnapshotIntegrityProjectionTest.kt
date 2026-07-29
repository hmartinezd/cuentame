package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

class BackupSnapshotIntegrityProjectionTest {

    private val restId = "rest-1"
    private val manifest = createManifest(restId)

    private fun createManifest(id: String) = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 2,
        restaurantId = id,
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
    fun `rejects balance projection mismatch`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", "a1", null, null, null, true, 0, 0, null)),
            inventoryAreas = listOf(InventoryAreaBackupDto("a1", restId, "A", "a", 0, true, 0, 0, null)),
            inventoryMovements = listOf(
                InventoryMovementBackupDto("m1", restId, "i1", "a1", "PURCHASE", "10.0", "1.0", "10.0", 0L, "MANUAL", "op1", "op1", null, null, 0L)
            ),
            inventoryBalanceProjections = listOf(
                InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "15.0", 0L) // Sum is 10
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
    }
}
