package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import org.junit.Test

class ProductionBackupIntegrityTest {

    private val restId = "rest-1"
    private val manifest = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1L,
        databaseSchemaVersion = 4,
        restaurantId = restId,
        restaurantName = "Test Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = listOf("data"),
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
        ingredientCostProjections = emptyList(),
        preparationRecipes = emptyList(),
        preparationRecipeComponents = emptyList(),
        productionBatches = emptyList(),
        productionBatchComponents = emptyList()
    )

    private fun createValidSnapshot(): BackupSnapshotDto {
        val unit = mockkUnit("u1")
        val outputIng = mockkIngredient("ing-output")
        val yieldOpt = mockkOption("opt-yield", "ing-output")
        val compIng = mockkIngredient("ing-comp")
        val compOpt = mockkOption("opt-comp", "ing-comp")
        val area = mockkArea("area-1")
        
        val recipe = PreparationRecipeBackupDto(
            "r1", restId, "ing-output", "Recipe", "recipe", 
            "1.0", "1.0", "opt-yield", "ACTIVE", null, 1000, 1000, null
        )
        val recipeComp = PreparationRecipeComponentBackupDto(
            "rc1", "r1", "ing-comp", "opt-comp", "0.5", "0.5", 0, null, 1000, 1000
        )
        
        val batch = ProductionBatchBackupDto(
            "b1", restId, "r1", "Recipe", "ing-output", "2.0",
            "1.0", "1.0", "opt-yield", "2.0", "2.0", "2.0", "2.0", 
            "opt-yield", "area-1", false, "10.0", "5.0", 2000, "POSTED", null,
            1500, 2500, 2500, null
        )
        val batchComp = ProductionBatchComponentBackupDto(
            "bc1", "b1", "rc1", "ing-comp", "0.5", "0.5", "opt-comp",
            "1.0", "1.0", "1.0", "1.0", "opt-comp", false, "area-1", 
            "10.0", "10.0", 0, null, 1500, 2500
        )
        
        val moveCons = InventoryMovementBackupDto(
            "m1", restId, "ing-comp", "area-1", "PRODUCTION_CONSUMPTION", "-1.0", "10.0", "-10.0", 2000,
            "PRODUCTION_BATCH", "b1", "production-post:b1:consume:bc1", "bc1", null, 2500
        )
        val moveOut = InventoryMovementBackupDto(
            "m2", restId, "ing-output", "area-1", "PRODUCTION_OUTPUT", "2.0", "5.0", "10.0", 2000,
            "PRODUCTION_BATCH", "b1", "production-post:b1:output", "b1", null, 2500
        )

        return createEmptyDto().copy(
            units = listOf(unit),
            ingredients = listOf(outputIng, compIng),
            ingredientUnitOptions = listOf(yieldOpt, compOpt),
            inventoryAreas = listOf(area),
            preparationRecipes = listOf(recipe),
            preparationRecipeComponents = listOf(recipeComp),
            productionBatches = listOf(batch),
            productionBatchComponents = listOf(batchComp),
            inventoryMovements = listOf(moveCons, moveOut),
            inventoryBalanceProjections = listOf(
                InventoryBalanceProjectionBackupDto(restId, "ing-comp", "area-1", "-1.0", 2500),
                InventoryBalanceProjectionBackupDto(restId, "ing-output", "area-1", "2.0", 2500)
            ),
            ingredientCostProjections = listOf(
                IngredientCostProjectionBackupDto(restId, "ing-output", "5.0", 2500)
            )
        )
    }

    @Test
    fun `validate accepts valid production snapshot`() {
        val result = BackupSnapshotIntegrityValidator.validate(createValidSnapshot(), manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects wrong consumption quantity`() {
        val dto = createValidSnapshot()
        val badMove = dto.inventoryMovements[0].copy(quantityBaseSigned = "-1.5")
        val newDto = dto.copy(inventoryMovements = listOf(badMove, dto.inventoryMovements[1]))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `validate rejects wrong output unit cost`() {
        val dto = createValidSnapshot()
        val badMove = dto.inventoryMovements[1].copy(unitCostBaseSnapshot = "6.0")
        val newDto = dto.copy(inventoryMovements = listOf(dto.inventoryMovements[0], badMove))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun `validate rejects missing component movement`() {
        val dto = createValidSnapshot()
        val newDto = dto.copy(inventoryMovements = listOf(dto.inventoryMovements[1]))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE)
    }

    @Test
    fun `validate rejects duplicate component ingredient`() {
        val dto = createValidSnapshot()
        val comp2 = dto.productionBatchComponents[0].copy(id = "bc2")
        val newDto = dto.copy(productionBatchComponents = listOf(dto.productionBatchComponents[0], comp2))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
    }

    @Test
    fun `validate rejects draft batch referencing archived option`() {
        val dto = createValidSnapshot()
        val draftBatch = dto.productionBatches[0].copy(status = "DRAFT", postedAt = null, totalComponentCostSnapshot = null, outputUnitCostBaseSnapshot = null)
        val archivedOpt = dto.ingredientUnitOptions[0].copy(isActive = false, deletedAt = 500)
        val newDto = dto.copy(
            productionBatches = listOf(draftBatch),
            ingredientUnitOptions = listOf(archivedOpt, dto.ingredientUnitOptions[1]),
            productionBatchComponents = listOf(dto.productionBatchComponents[0].copy(unitCostBaseSnapshot = null, totalCostSnapshot = null)),
            inventoryMovements = emptyList()
        )
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.RELATIONSHIP_MISMATCH)
    }

    @Test
    fun `validate rejects value conservation failure`() {
        val dto = createValidSnapshot()
        val badBatch = dto.productionBatches[0].copy(totalComponentCostSnapshot = "11.0")
        // Now Output movement (total 10.0) + Consumptions (total -10.0) sum to 0, 
        // but batch.totalComponentCostSnapshot (11.0) != sum of components (10.0)
        val newDto = dto.copy(productionBatches = listOf(badBatch))
        
        val result = BackupSnapshotIntegrityValidator.validate(newDto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    private fun assertIntegrityCode(result: Result<Unit>, expected: BackupSnapshotIntegrityCode) {
        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull() as BackupSnapshotIntegrityException
        assertThat(ex.code).isEqualTo(expected)
    }

    private fun mockkIngredient(id: String) = IngredientBackupDto(id, restId, "Name", "name", null, "u1", "area-1", null, null, null, true, 0, 0, null)
    private fun mockkUnit(id: String) = UnitBackupDto(id, "Unit", "u", "MASS", "1.0", true, 0)
    private fun mockkOption(id: String, ingId: String) = IngredientUnitOptionBackupDto(id, ingId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
    private fun mockkArea(id: String) = InventoryAreaBackupDto(id, restId, "Area", "area", 0, true, 0, 0, null)
}
