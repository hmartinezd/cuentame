package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.model.*
import com.venkoi.cuentame.core.model.backup.BackupManifest
import org.junit.Test

class BackupSnapshotIntegrityReversalTest {

    private val restId = "rest-1"
    private val manifest = createManifest(restId)

    private fun createManifest(id: String) = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.venkoi.cuentame",
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
    fun `rejects reversal targeting another reversal`() {
        val dto = createEmptyDto().copy(
            inventoryMovements = listOf(
                InventoryMovementBackupDto("m1", restId, "i1", "a1", "PURCHASE", "10.0", "1.0", "10.0", 100L, "MANUAL", "op1", "op1", null, null, 0L),
                InventoryMovementBackupDto("m2", restId, "i1", "a1", "REVERSAL", "-10.0", "1.0", "-10.0", 200L, "MANUAL", "op1", "op1", null, "m1", 0L),
                InventoryMovementBackupDto("m3", restId, "i1", "a1", "REVERSAL", "10.0", "1.0", "10.0", 300L, "MANUAL", "op1", "op1", null, "m2", 0L)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
    }
}
