package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

/**
 * Tests that [BackupSnapshotIntegrityValidator] emits stable [BackupSnapshotIntegrityCode]s.
 * Assertions are on the typed code, not on human-readable messages.
 */
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

    private fun assertCode(dto: BackupSnapshotDto, code: BackupSnapshotIntegrityCode) {
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull()
        assertThat(ex).isInstanceOf(BackupSnapshotIntegrityException::class.java)
        assertThat((ex as BackupSnapshotIntegrityException).code).isEqualTo(code)
    }

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
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_RESTAURANT_COUNT)
        // Also check message contains "Found 2" for backward compat with integration tests
        val ex = BackupSnapshotIntegrityValidator.validate(dto, manifest).exceptionOrNull()
        assertThat(ex?.message).contains("found 2")
    }

    @Test
    fun `validate rejects restaurant ID mismatch`() {
        val dto = createEmptyDto().copy(
            restaurants = listOf(RestaurantBackupDto("mismatch", "Test Rest", "USD", "en-US", 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.RESTAURANT_ID_MISMATCH)
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).exceptionOrNull()?.message)
            .contains("restaurant ID")
    }

    @Test
    fun `validate rejects restaurant name mismatch`() {
        val dto = createEmptyDto().copy(
            restaurants = listOf(RestaurantBackupDto(restId, "Wrong Name", "USD", "en-US", 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.RESTAURANT_NAME_MISMATCH)
    }

    @Test
    fun `validate rejects currency code mismatch`() {
        val dto = createEmptyDto().copy(
            restaurants = listOf(RestaurantBackupDto(restId, "Test Rest", "EUR", "en-US", 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.RESTAURANT_CURRENCY_MISMATCH)
    }

    @Test
    fun `validate rejects locale mismatch`() {
        val dto = createEmptyDto().copy(
            restaurants = listOf(RestaurantBackupDto(restId, "Test Rest", "USD", "es-US", 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.RESTAURANT_LOCALE_MISMATCH)
    }

    @Test
    fun `validate rejects isolation error in areas`() {
        val dto = createEmptyDto().copy(
            inventoryAreas = listOf(
                InventoryAreaBackupDto("area-1", "other-rest", "Area", "area", 1, true, 0, 0, null)
            )
        )
        assertCode(dto, BackupSnapshotIntegrityCode.RESTAURANT_ISOLATION_FAILURE)
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).exceptionOrNull()?.message)
            .contains("Isolation error in inventory_areas")
    }

    @Test
    fun `validate rejects broken FK in ingredients`() {
        val dto = createEmptyDto().copy(
            units = listOf(UnitBackupDto("u1", "Unit", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(
                IngredientBackupDto("ing-1", restId, "Ing", "ing", null, "missing-unit", null, null, null, null, true, 0, 0, null)
            )
        )
        assertCode(dto, BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).exceptionOrNull()?.message)
            .contains("Broken FK: ingredient to unit")
    }

    @Test
    fun `validate rejects unit option for wrong ingredient`() {
        val dto = createEmptyDto().copy(
            ingredients = listOf(
                IngredientBackupDto("ing-1", restId, "Ing 1", "ing 1", null, "u1", null, null, null, null, true, 0, 0, null),
                IngredientBackupDto("ing-2", restId, "Ing 2", "ing 2", null, "u1", null, null, null, null, true, 0, 0, null)
            ),
            units = listOf(UnitBackupDto("u1", "Unit", "u", "MASS", "1.0", true, 1)),
            ingredientUnitOptions = listOf(
                IngredientUnitOptionBackupDto("opt-1", "ing-2", "Label", "lbl", null, "1.0", true, true, true, true, 0, 0, null)
            ),
            purchaseReceipts = listOf(
                PurchaseReceiptBackupDto("p1", restId, null, null, 0, "DRAFT", null, null, 0, 0, null, null)
            ),
            purchaseLines = listOf(
                PurchaseLineBackupDto("l1", "p1", "ing-1", "area-1", "opt-1", "1.0", "1.0", "1.0", "1.0", null, 0, 0)
            ),
            inventoryAreas = listOf(InventoryAreaBackupDto("area-1", restId, "Area", "area", 1, true, 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).exceptionOrNull()?.message)
            .contains("Unit option mismatch in purchase line")
    }

    @Test
    fun `validate rejects invalid decimal format in factorToCanonical`() {
        val dto = createEmptyDto().copy(
            units = listOf(UnitBackupDto("u1", "Unit", "u", "MASS", "invalid", true, 1))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_DECIMAL)
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).exceptionOrNull()?.message)
            .contains("Invalid numeric format in units.factorToCanonical")
    }

    @Test
    fun `validate rejects invalid enum value in dimension`() {
        val dto = createEmptyDto().copy(
            units = listOf(UnitBackupDto("u1", "Unit", "u", "NOT_A_DIMENSION", "1.0", true, 1))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_ENUM)
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).exceptionOrNull()?.message)
            .contains("Invalid value")
    }
}
