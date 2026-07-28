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
        assertThat(result.exceptionOrNull()?.message).contains("exactly one restaurant")
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
    fun `validate rejects unit option belonging to different ingredient in purchase lines`() {
        val baseDto = createEmptyDto()
        val dto = baseDto.copy(
            units = listOf(UnitBackupDto("u1", "Unit", "u", "Mass", "1.0", true, 1)),
            inventoryAreas = listOf(InventoryAreaBackupDto("area-1", restId, "Area", "area", 1, true, 0, 0, null)),
            ingredients = listOf(
                IngredientBackupDto("ing-1", restId, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0, 0, null),
                IngredientBackupDto("ing-2", restId, "Ing 2", "ing 2", null, "u1", null, null, null, null, true, 0, 0, null)
            ),
            ingredientUnitOptions = listOf(
                IngredientUnitOptionBackupDto("opt-2", "ing-2", "Option 2", "opt2", "u1", "1.0", true, true, true, true, 0, 0, null)
            ),
            purchaseReceipts = listOf(
                PurchaseReceiptBackupDto("pr-1", restId, null, "INV-100", 0, "DRAFT", null, null, 0, 0, null, null)
            ),
            purchaseLines = listOf(
                PurchaseLineBackupDto("pl-1", "pr-1", "ing-1", "area-1", "opt-2", "1.0", "1.0", "10.0", "10.0", null, 0, 0)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Unit option ingredient mismatch")
    }

    @Test
    fun `validate rejects invalid decimal strings without revealing sensitive values`() {
        val baseDto = createEmptyDto()
        val dto = baseDto.copy(
            units = listOf(UnitBackupDto("u1", "Unit", "u", "Mass", "NOT_A_NUMBER", true, 1))
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid decimal format in units.factorToCanonical")
        assertThat(result.exceptionOrNull()?.message).doesNotContain("NOT_A_NUMBER")
    }

    @Test
    fun `validate rejects invalid purchase status`() {
        val baseDto = createEmptyDto()
        val dto = baseDto.copy(
            purchaseReceipts = listOf(
                PurchaseReceiptBackupDto("pr-1", restId, null, "INV-100", 0, "UNKNOWN_STATUS", null, null, 0, 0, null, null)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid purchase receipt status")
    }

    @Test
    fun `validate rejects blank primary key in stock_count_lines`() {
        val baseDto = createEmptyDto()
        val dto = baseDto.copy(
            stockCountLines = listOf(
                StockCountLineBackupDto("", "sca-1", "ing-1", "opt-1", "1.0", "1.0", null, null, null, 0, 0)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Blank ID in table stock_count_lines")
    }

    @Test
    fun `validate rejects movement self-reversal`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(IngredientBackupDto("ing-1", restId, "Ing", "ing", null, "u1", null, null, null, null, true, 0, 0, null)),
            units = listOf(UnitBackupDto("u1", "Unit", "u", "Mass", "1.0", true, 1)),
            inventoryAreas = listOf(InventoryAreaBackupDto("area-1", restId, "Area", "area", 1, true, 0, 0, null)),
            inventoryMovements = listOf(
                InventoryMovementBackupDto("m1", restId, "ing-1", "area-1", "WASTE_POST", "-1", null, null, 0, "WASTE_EVENT", "w1", "op1", null, "m1", 0)
            )
        )
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Self-reversal")
    }
}
