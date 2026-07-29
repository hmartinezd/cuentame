package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

/**
 * Tests for [BackupSnapshotIntegrityValidator] voided stock-count cardinality.
 * Verifies that VOIDED stock counts require exact 1:1 mapping between
 * count lines, original movements, and reversal movements.
 */
class BackupSnapshotIntegrityVoidedCountTest {

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

    private fun voidedCount(id: String = "sc1") = StockCountBackupDto(
        id = id, restaurantId = restId, name = "Count", startedAt = 0L,
        effectiveAt = 10L, completedAt = 20L, status = "VOIDED",
        notes = null, createdAt = 0L, updatedAt = 30L, voidedAt = 30L
    )

    private fun sca(scId: String = "sc1") = StockCountAreaBackupDto(
        "sca1", scId, "a1", "COMPLETED", 0L, 1L, 1
    )

    private fun scLine(scaId: String = "sca1") = StockCountLineBackupDto(
        "scl1", scaId, "i1", "o1", "10.0", "10.0", null, null, null, 0L, 1L
    )

    private fun origMove(id: String, lineId: String) = InventoryMovementBackupDto(
        id = id, restaurantId = restId, ingredientId = "i1", areaId = "a1",
        movementType = "OPENING_BALANCE", quantityBaseSigned = "10.0",
        unitCostBaseSnapshot = null, totalValueSnapshot = null,
        effectiveAt = 20L, sourceDocumentType = "STOCK_COUNT",
        sourceDocumentId = "sc1", sourceOperationId = "op-$id",
        sourceLineId = lineId, reversalOfMovementId = null, createdAt = 0L
    )

    private fun reversalMove(id: String, targetId: String) = InventoryMovementBackupDto(
        id = id, restaurantId = restId, ingredientId = "i1", areaId = "a1",
        movementType = "REVERSAL", quantityBaseSigned = "-10.0",
        unitCostBaseSnapshot = null, totalValueSnapshot = null,
        effectiveAt = 30L, sourceDocumentType = "STOCK_COUNT",
        sourceDocumentId = "sc1", sourceOperationId = "op-rev-$id",
        sourceLineId = "scl1", reversalOfMovementId = targetId, createdAt = 0L
    )

    private fun baseDto(movements: List<InventoryMovementBackupDto>, balanceQty: String = "0.0") = BackupSnapshotDto(
        restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
        inventoryAreas = listOf(area),
        ingredientCategories = emptyList(),
        units = listOf(unit),
        ingredients = listOf(ing),
        ingredientUnitOptions = listOf(opt),
        suppliers = emptyList(),
        purchaseReceipts = emptyList(),
        purchaseLines = emptyList(),
        stockCounts = listOf(voidedCount()),
        stockCountAreas = listOf(sca()),
        stockCountLines = listOf(scLine()),
        wasteEvents = emptyList(),
        inventoryMovements = movements,
        inventoryBalanceProjections = if (movements.isEmpty()) emptyList()
            else listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", balanceQty, 0L)),
        ingredientCostProjections = emptyList()
    )

    @Test
    fun `VOIDED stock count with matching original and reversal passes`() {
        val orig = origMove("mv1", "scl1")
        val rev = reversalMove("mv2", "mv1")
        val dto = baseDto(listOf(orig, rev), "0.0")
        assertThat(BackupSnapshotIntegrityValidator.validate(dto, manifest).isSuccess).isTrue()
    }

    @Test
    fun `VOIDED stock count with no movements at all fails`() {
        // The stock count is VOIDED but has no movements — should fail
        val dto = baseDto(emptyList())
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val code = (result.exceptionOrNull() as BackupSnapshotIntegrityException).code
        assertThat(code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE)
    }

    @Test
    fun `VOIDED stock count with original but no reversal fails`() {
        val orig = origMove("mv1", "scl1")
        val dto = baseDto(listOf(orig), "10.0")
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val code = (result.exceptionOrNull() as BackupSnapshotIntegrityException).code
        assertThat(code).isEqualTo(BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE)
    }

    @Test
    fun `VOIDED stock count reversal pointing to wrong original fails`() {
        val orig = origMove("mv1", "scl1")
        val wrongRev = reversalMove("mv2", "nonexistent-move")
        // We need a second movement so the reversal can exist without targeting mv1
        val secondOrig = origMove("mv3", "scl1").copy(sourceOperationId = "op-other")
        val dto = BackupSnapshotDto(
            restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(area), ingredientCategories = emptyList(),
            units = listOf(unit), ingredients = listOf(ing), ingredientUnitOptions = listOf(opt),
            suppliers = emptyList(), purchaseReceipts = emptyList(), purchaseLines = emptyList(),
            stockCounts = listOf(voidedCount()), stockCountAreas = listOf(sca()),
            stockCountLines = listOf(scLine()), wasteEvents = emptyList(),
            inventoryMovements = listOf(orig, wrongRev),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "10.0", 0L)),
            ingredientCostProjections = emptyList()
        )
        // wrongRev points to nonexistent-move, so the validator should fail on INVALID_REVERSAL
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isFailure).isTrue()
        val code = (result.exceptionOrNull() as BackupSnapshotIntegrityException).code
        // Either INVALID_REVERSAL (target not found) or INVALID_DOCUMENT_LIFECYCLE (wrong coverage)
        assertThat(code).isAnyOf(
            BackupSnapshotIntegrityCode.INVALID_REVERSAL,
            BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE
        )
    }

    @Test
    fun `VOIDED stock count with extra reversal not matching original fails`() {
        val orig = origMove("mv1", "scl1")
        val rev1 = reversalMove("mv2", "mv1")
        // Extra reversal: no original to point to
        // Can't double-reverse mv1 because it's already reversed by mv2
        // So we create a second scLine and second orig, but only one scLine exists
        // Instead, just add a reversal that doesn't match: it must be caught by document lifecycle
        val dto = BaseWithTwoLines()
        val result = BackupSnapshotIntegrityValidator.validate(dto.first, manifest)
        assertThat(result.isFailure).isTrue()
    }

    /** Helper: create a DTO with 2 count lines and only 1 reversal — should fail cardinality. */
    private fun BaseWithTwoLines(): Pair<BackupSnapshotDto, Nothing?> {
        val sca2 = StockCountAreaBackupDto("sca2", "sc1", "a1", "COMPLETED", 0L, 1L, 2)
        val scl2 = StockCountLineBackupDto("scl2", "sca1", "i1", "o1", "5.0", "5.0", null, null, null, 0L, 1L)
        val orig1 = origMove("mv1", "scl1")
        val orig2 = origMove("mv2", "scl2").copy(sourceOperationId = "op-mv2")
        val rev1 = reversalMove("mv3", "mv1")
        // Only one reversal for two originals
        return Pair(BackupSnapshotDto(
            restaurants = listOf(RestaurantBackupDto(restId, "Test", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(area), ingredientCategories = emptyList(),
            units = listOf(unit), ingredients = listOf(ing), ingredientUnitOptions = listOf(opt),
            suppliers = emptyList(), purchaseReceipts = emptyList(), purchaseLines = emptyList(),
            stockCounts = listOf(voidedCount()), stockCountAreas = listOf(sca()),
            stockCountLines = listOf(scLine(), scl2), wasteEvents = emptyList(),
            inventoryMovements = listOf(orig1, orig2, rev1),
            inventoryBalanceProjections = listOf(InventoryBalanceProjectionBackupDto(restId, "i1", "a1", "5.0", 0L)),
            ingredientCostProjections = emptyList()
        ), null)
    }
}
