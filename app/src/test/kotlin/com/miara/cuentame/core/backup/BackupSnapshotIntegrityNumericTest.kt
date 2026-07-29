package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.backup.TableMetadata
import org.junit.Test
import java.math.BigDecimal

class BackupSnapshotIntegrityNumericTest {

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
    fun `rejects negative purchase quantity`() {
        val dto = createEmptyDto().copy(
            purchaseReceipts = listOf(PurchaseReceiptBackupDto("p1", restId, null, null, 0, "DRAFT", null, null, 0, 0, null, null)),
            purchaseLines = listOf(PurchaseLineBackupDto("l1", "p1", "i1", "a1", "o1", "-1.0", "-1.0", "1.0", "1.0", null, 0, 0))
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects malformed decimal`() {
        val dto = createEmptyDto().copy(
            purchaseReceipts = listOf(PurchaseReceiptBackupDto("p1", restId, null, null, 0, "DRAFT", null, null, 0, 0, null, null)),
            purchaseLines = listOf(PurchaseLineBackupDto("l1", "p1", "i1", "a1", "o1", "not-a-number", "1.0", "1.0", "1.0", null, 0, 0))
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
    }
}
