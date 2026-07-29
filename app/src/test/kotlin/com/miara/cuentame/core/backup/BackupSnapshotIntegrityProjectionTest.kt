package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

/**
 * Tests for [BackupSnapshotIntegrityValidator] balance and cost projection validation.
 */
class BackupSnapshotIntegrityProjectionTest {

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

    private val area = InventoryAreaBackupDto("a1", restId, "A", "a", 1, true, 0, 0, null)
    private val unit = UnitBackupDto("u1", "U", "u", "MASS", "1.0", true, 1)
    private val ing = IngredientBackupDto("i1", restId, "I", "i", null, "u1", null, null, null, null, true, 0, 0, null)
    private val opt = IngredientUnitOptionBackupDto("o1", "i1", "L", "l", null, "1.0", true, true, true, true, 0, 0, null)
    private val wastePosted = WasteEventBackupDto("w1", restId, "i1", "a1", "o1", "5.0", "5.0", "EXPIRED", 0L, null, null, "POSTED", 0, 1, 1L, null)
    private val wasteMove = InventoryMovementBackupDto("mv1", restId, "i1", "a1", "WASTE", "-5.0", null, null, 1L, "WASTE_EVENT", "w1", "op1", "w1", null, 0L)

    private fun emptyBase() = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
        inventoryAreas = listOf(area), ingredientCategories = emptyList(),
        units = listOf(unit), ingredients = listOf(ing), ingredientUnitOptions = listOf(opt),
        suppliers = emptyList(), purchaseReceipts = emptyList(), purchaseLines = emptyList(),
        stockCounts = emptyList(), stockCountAreas = emptyList(), stockCountLines = emptyList(),
        wasteEvents = emptyList(), inventoryMovements = emptyList(),
        inventoryBalanceProjections = emptyList(), ingredientCostProjections = emptyList()
    )

    private fun assertCode(dto: BackupSnapshotDto, code: BackupSnapshotIntegrityCode) {
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
        assertThat(ex?.code).isEqualTo(code)
    }

    // ── Balance projections ────────────────────────────────────────────────────

    @Test
    fun `balance projection present for movement passes`() {
        val dto = emptyBase().copy(
            wasteEvents = listOf(wastePosted),
            inventoryMovements = listOf(wasteMove),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "-5.0", 0L))
        )
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `missing balance projection for movement combination is INVALID_BALANCE_PROJECTION`() {
        val dto = emptyBase().copy(
            wasteEvents = listOf(wastePosted),
            inventoryMovements = listOf(wasteMove),
            inventoryBalanceProjections = emptyList()
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_BALANCE_PROJECTION)
    }

    @Test
    fun `extra balance projection with no movements is INVALID_BALANCE_PROJECTION`() {
        val dto = emptyBase().copy(
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "10.0", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_BALANCE_PROJECTION)
    }

    @Test
    fun `balance projection value mismatch is INVALID_BALANCE_PROJECTION`() {
        val dto = emptyBase().copy(
            wasteEvents = listOf(wastePosted),
            inventoryMovements = listOf(wasteMove),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "-4.9", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_BALANCE_PROJECTION)
    }

    @Test
    fun `balance projection with malformed quantityBase is INVALID_DECIMAL`() {
        val dto = emptyBase().copy(
            wasteEvents = listOf(wastePosted),
            inventoryMovements = listOf(wasteMove),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "not-a-number", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_DECIMAL)
    }

    // ── Cost projections ───────────────────────────────────────────────────────

    @Test
    fun `cost projection with valid ingredient and null cost passes`() {
        val dto = emptyBase().copy(
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "i1", null, 0L))
        )
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `cost projection with valid ingredient and valid cost passes`() {
        val dto = emptyBase().copy(
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "i1", "12.50", 0L))
        )
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `cost projection for unknown ingredient is BROKEN_FOREIGN_KEY`() {
        val dto = emptyBase().copy(
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "nonexistent-ing", null, 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.BROKEN_FOREIGN_KEY)
    }

    @Test
    fun `cost projection with wrong restaurant is RESTAURANT_ISOLATION_FAILURE`() {
        val dto = emptyBase().copy(
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto("other-rest", "i1", null, 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.RESTAURANT_ISOLATION_FAILURE)
    }

    @Test
    fun `duplicate cost projection composite key is DUPLICATE_COMPOSITE_KEY`() {
        val dto = emptyBase().copy(
            ingredientCostProjections = listOf(
                IngredientCostProjectionBackupDto(restId, "i1", null, 0L),
                IngredientCostProjectionBackupDto(restId, "i1", "5.0", 0L)
            )
        )
        assertCode(dto, BackupSnapshotIntegrityCode.DUPLICATE_COMPOSITE_KEY)
    }

    @Test
    fun `cost projection with malformed averageUnitCostBase is INVALID_DECIMAL`() {
        val dto = emptyBase().copy(
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "i1", "NaN", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_DECIMAL)
    }

    @Test
    fun `cost projection with negative averageUnitCostBase is INVALID_NUMERIC_RANGE`() {
        val dto = emptyBase().copy(
            ingredientCostProjections = listOf(IngredientCostProjectionBackupDto(restId, "i1", "-0.01", 0L))
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }
}
