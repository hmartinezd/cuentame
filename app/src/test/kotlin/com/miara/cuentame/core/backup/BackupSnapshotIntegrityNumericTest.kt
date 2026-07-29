package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

/**
 * Focused tests for [BackupSnapshotIntegrityValidator] numeric validation.
 * Verifies that all [BigDecimal] fields are validated correctly.
 */
class BackupSnapshotIntegrityNumericTest {

    private val restId = "rest-1"
    private val manifest = BackupManifest(
        backupFormatVersion = 1, createdAtUtc = "2026-01-01T00:00:00Z",
        applicationId = "com.miara.cuentame", appVersionName = "1.0",
        appVersionCode = 1L, databaseSchemaVersion = 2,
        restaurantId = restId, restaurantName = "Test", localeTag = "en-US",
        currencyCode = "USD", tableMetadata = emptyMap(), attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    private fun emptyDto() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
        inventoryAreas = emptyList(), ingredientCategories = emptyList(), units = emptyList(),
        ingredients = emptyList(), ingredientUnitOptions = emptyList(), suppliers = emptyList(),
        purchaseReceipts = emptyList(), purchaseLines = emptyList(), stockCounts = emptyList(),
        stockCountAreas = emptyList(), stockCountLines = emptyList(), wasteEvents = emptyList(),
        inventoryMovements = emptyList(), inventoryBalanceProjections = emptyList(),
        ingredientCostProjections = emptyList()
    )

    private fun assertCode(dto: BackupSnapshotDto, code: BackupSnapshotIntegrityCode) {
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
        assertThat(ex?.code).isEqualTo(code)
    }

    // ── units.factorToCanonical ────────────────────────────────────────────────

    @Test
    fun `factorToCanonical invalid string is INVALID_DECIMAL`() {
        val dto = emptyDto().copy(units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "not-a-number", true, 1)))
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_DECIMAL)
    }

    @Test
    fun `factorToCanonical zero is INVALID_NUMERIC_RANGE`() {
        val dto = emptyDto().copy(units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "0", true, 1)))
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `factorToCanonical negative is INVALID_NUMERIC_RANGE`() {
        val dto = emptyDto().copy(units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "-1", true, 1)))
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `factorToCanonical positive value passes`() {
        val dto = emptyDto().copy(units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.5", true, 1)))
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    // ── ingredient_unit_options.factorToBase ──────────────────────────────────

    @Test
    fun `factorToBase invalid string is INVALID_DECIMAL`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)),
            ingredientUnitOptions = listOf(IngredientUnitOptionBackupDto("o1", "i1", "L", "l", null, "abc", true, true, true, true, 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_DECIMAL)
    }

    @Test
    fun `factorToBase zero is INVALID_NUMERIC_RANGE`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)),
            ingredientUnitOptions = listOf(IngredientUnitOptionBackupDto("o1", "i1", "L", "l", null, "0", true, true, true, true, 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    // ── ingredients.reorderPointBase ──────────────────────────────────────────

    @Test
    fun `reorderPointBase invalid string is INVALID_DECIMAL`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, "bad", true, 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_DECIMAL)
    }

    @Test
    fun `reorderPointBase negative is INVALID_NUMERIC_RANGE`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, "-5", true, 0, 0, null))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `reorderPointBase null passes without error`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null))
        )
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    // ── inventory_movements.unitCostBaseSnapshot ──────────────────────────────

    @Test
    fun `unitCostBaseSnapshot null is allowed`() {
        val dto = emptyDto().copy(
            inventoryAreas = listOf(InventoryAreaBackupDto("a1", restId, "A", "a", 1, true, 0, 0, null)),
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)),
            ingredientUnitOptions = listOf(IngredientUnitOptionBackupDto("o1", "i1", "L", "l", null, "1.0", true, true, true, true, 0, 0, null)),
            wasteEvents = listOf(WasteEventBackupDto("w1", restId, "i1", "a1", "o1", "1.0", "1.0", "EXPIRED", 0L, null, null, "POSTED", 0, 1, 1L, null)),
            inventoryMovements = listOf(
                InventoryMovementBackupDto("mv1", restId, "i1", "a1", "WASTE", "-1.0", null, null, 0L, "WASTE_EVENT", "w1", "op1", "w1", null, 0L)
            ),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "-1.0", 0L))
        )
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `unitCostBaseSnapshot negative is INVALID_NUMERIC_RANGE`() {
        val dto = emptyDto().copy(
            inventoryAreas = listOf(InventoryAreaBackupDto("a1", restId, "A", "a", 1, true, 0, 0, null)),
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)),
            ingredientUnitOptions = listOf(IngredientUnitOptionBackupDto("o1", "i1", "L", "l", null, "1.0", true, true, true, true, 0, 0, null)),
            wasteEvents = listOf(WasteEventBackupDto("w1", restId, "i1", "a1", "o1", "1.0", "1.0", "EXPIRED", 0L, null, null, "POSTED", 0, 1, 1L, null)),
            inventoryMovements = listOf(
                InventoryMovementBackupDto("mv1", restId, "i1", "a1", "WASTE", "-1.0", "-5.00", "-5.00", 0L, "WASTE_EVENT", "w1", "op1", "w1", null, 0L)
            ),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "-1.0", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    // ── ingredient_cost_projections.averageUnitCostBase ───────────────────────

    @Test
    fun `averageUnitCostBase null is allowed`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)),
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "i1", null, 0L))
        )
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `averageUnitCostBase negative is INVALID_NUMERIC_RANGE`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)),
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "i1", "-0.01", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `averageUnitCostBase invalid string is INVALID_DECIMAL`() {
        val dto = emptyDto().copy(
            units = listOf(UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)),
            ingredients = listOf(IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)),
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "i1", "NaN", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_DECIMAL)
    }
}
