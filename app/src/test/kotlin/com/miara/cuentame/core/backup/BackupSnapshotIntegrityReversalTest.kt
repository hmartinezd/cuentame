package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

/**
 * Tests for [BackupSnapshotIntegrityValidator] reversal validation:
 * quantity negation, totalValueSnapshot null symmetry, and error codes.
 */
class BackupSnapshotIntegrityReversalTest {

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

    private fun voidedWaste() = WasteEventBackupDto(
        "w1", restId, "i1", "a1", "o1", "5.0", "5.0", "EXPIRED", 0L, null, null, "VOIDED", 0, 1, 1L, 2L
    )

    private fun buildDto(
        origQty: String,
        origValue: String?,
        revQty: String,
        revValue: String?,
        balanceQty: String,
    ) = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
        inventoryAreas = listOf(area), ingredientCategories = emptyList(),
        units = listOf(unit), ingredients = listOf(ing), ingredientUnitOptions = listOf(opt),
        suppliers = emptyList(), purchaseReceipts = emptyList(), purchaseLines = emptyList(),
        stockCounts = emptyList(), stockCountAreas = emptyList(), stockCountLines = emptyList(),
        wasteEvents = listOf(voidedWaste()),
        inventoryMovements = listOf(
            InventoryMovementBackupDto(
                "mv1", restId, "i1", "a1", "WASTE", origQty, null, origValue,
                0L, "WASTE_EVENT", "w1", "op1", "w1", null, 0L
            ),
            InventoryMovementBackupDto(
                "mv2", restId, "i1", "a1", "REVERSAL", revQty, null, revValue,
                2L, "WASTE_EVENT", "w1", "op2", "w1", "mv1", 0L
            )
        ),
        inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", balanceQty, 0L)),
        ingredientCostProjections = emptyList()
    )

    private fun assertCode(dto: BackupSnapshotDto, code: BackupSnapshotIntegrityCode) {
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
        assertThat(ex?.code).isEqualTo(code)
    }

    @Test
    fun `reversal with matching negation and null values passes`() {
        val dto = buildDto("-5.0", null, "5.0", null, "0.0")
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `reversal with matching negation and both values passes`() {
        val dto = buildDto("-5.0", "-15.00", "5.0", "15.00", "0.0")
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `reversal quantity not exact negation is INVALID_REVERSAL`() {
        val dto = buildDto("-5.0", null, "4.9", null, "-0.1")
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun `reversal totalValueSnapshot present but original is null is INVALID_REVERSAL`() {
        val dto = buildDto("-5.0", null, "5.0", "15.00", "0.0")
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun `reversal totalValueSnapshot null but original is present is INVALID_REVERSAL`() {
        val dto = buildDto("-5.0", "-15.00", "5.0", null, "0.0")
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun `reversal totalValueSnapshot not exact negation is INVALID_REVERSAL`() {
        val dto = buildDto("-5.0", "-15.00", "5.0", "14.99", "0.0")
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun `reversal pointing to itself is INVALID_REVERSAL`() {
        val dto = BackupSnapshotDto(
            restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(area), ingredientCategories = emptyList(),
            units = listOf(unit), ingredients = listOf(ing), ingredientUnitOptions = listOf(opt),
            suppliers = emptyList(), purchaseReceipts = emptyList(), purchaseLines = emptyList(),
            stockCounts = emptyList(), stockCountAreas = emptyList(), stockCountLines = emptyList(),
            wasteEvents = listOf(voidedWaste()),
            inventoryMovements = listOf(
                InventoryMovementBackupDto(
                    "mv1", restId, "i1", "a1", "REVERSAL", "5.0", null, null,
                    2L, "WASTE_EVENT", "w1", "op1", "w1", "mv1", 0L
                )
            ),
            inventoryBalanceProjections = emptyList(),
            ingredientCostProjections = emptyList()
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun `double reversal pointing to same original is INVALID_REVERSAL`() {
        val dto = BackupSnapshotDto(
            restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(area), ingredientCategories = emptyList(),
            units = listOf(unit), ingredients = listOf(ing), ingredientUnitOptions = listOf(opt),
            suppliers = emptyList(), purchaseReceipts = emptyList(), purchaseLines = emptyList(),
            stockCounts = emptyList(), stockCountAreas = emptyList(), stockCountLines = emptyList(),
            wasteEvents = listOf(voidedWaste()),
            inventoryMovements = listOf(
                // Original waste movement
                InventoryMovementBackupDto(
                    "mv1", restId, "i1", "a1", "WASTE", "-5.0", null, null,
                    0L, "WASTE_EVENT", "w1", "op1", "w1", null, 0L
                ),
                // First reversal
                InventoryMovementBackupDto(
                    "mv2", restId, "i1", "a1", "REVERSAL", "5.0", null, null,
                    2L, "WASTE_EVENT", "w1", "op2", "w1", "mv1", 0L
                ),
                // Second reversal pointing to same original
                InventoryMovementBackupDto(
                    "mv3", restId, "i1", "a1", "REVERSAL", "5.0", null, null,
                    2L, "WASTE_EVENT", "w1", "op3", "w1", "mv1", 0L
                )
            ),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "5.0", 0L)),
            ingredientCostProjections = emptyList()
        )
        assertCode(dto, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }
}
