package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import org.junit.Test
import java.math.BigDecimal

class ProductionBackupIntegrityTest {

    private val restId = "r1"
    private val areaId = "a1"
    private val ingCompId = "ing-comp"
    private val ingOutId = "ing-output"
    private val optCompId = "opt-comp"
    private val optOutId = "opt-output"
    private val recipeId = "rec-1"
    private val batchId = "pb1"

    @Test
    fun validProductionSnapshot_passes() {
        val dto = createValidSnapshot()
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun componentCostMismatch_returnsInvalidProductionCostHistory() {
        val dto = createValidSnapshot().let { base ->
            // Keep movements and projections valid (10.00 for comp, 10.00 for output)
            // But set component snapshot in the batch to something else (20.00)
            val components = base.productionBatchComponents.map { 
                if (it.id == "pbc1") it.copy(unitCostBaseSnapshot = "20.00", totalCostSnapshot = "20.00")
                else it
            }
            // Keep batch header internally consistent with components: total = 20.00, output unit cost = 20/2 = 10.00
            val batches = base.productionBatches.map {
                if (it.id == batchId) it.copy(totalComponentCostSnapshot = "20.00", outputUnitCostBaseSnapshot = "10.00")
                else it
            }
            // All movements must match the updated snapshots for internal consistency
            val movements = base.inventoryMovements.map {
                if (it.id == "m2") it.copy(unitCostBaseSnapshot = "20.00", totalValueSnapshot = "-20.00")
                else if (it.id == "m3") it.copy(unitCostBaseSnapshot = "10.00", totalValueSnapshot = "20.00")
                else it
            }
            // Projections must match movements: ing-comp=10.00, ing-output=10.00
            val projections = base.ingredientCostProjections.map {
                if (it.ingredientId == ingOutId) it.copy(averageUnitCostBase = "10.00")
                else it
            }
            base.copy(
                productionBatchComponents = components, 
                productionBatches = batches, 
                inventoryMovements = movements,
                ingredientCostProjections = projections
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_PRODUCTION_COST_HISTORY)
    }

    @Test
    fun duplicateComponentIngredient_returnsDuplicateProductionComponent() {
        val dto = createValidSnapshot().let { base ->
            val extraCompId = "pbc-extra"
            val extraComp = base.productionBatchComponents[0].copy(id = extraCompId)
            
            val extraMove = base.inventoryMovements.find { it.id == "m2" }!!.copy(
                id = "m-extra",
                sourceLineId = extraCompId,
                sourceOperationId = "production-post:$batchId:consume:$extraCompId"
            )
            
            // Adjust batch total cost to be consistent: 10 + 10 = 20
            val batches = base.productionBatches.map {
                if (it.id == batchId) it.copy(totalComponentCostSnapshot = "20.00", outputUnitCostBaseSnapshot = "10.00")
                else it
            }
            
            // Adjust output movement total value
            val movements = base.inventoryMovements.map {
                if (it.id == "m3") it.copy(totalValueSnapshot = "20.00", unitCostBaseSnapshot = "10.00")
                else it
            } + extraMove

            // Adjust projections
            val balanceProjections = base.inventoryBalanceProjections.map {
                if (it.ingredientId == ingCompId) it.copy(quantityBase = "8") // 10 - 1 - 1
                else if (it.ingredientId == ingOutId) it.copy(quantityBase = "2")
                else it
            }
            val costProjections = base.ingredientCostProjections.map {
                if (it.ingredientId == ingOutId) it.copy(averageUnitCostBase = "10.00")
                else it
            }

            base.copy(
                productionBatchComponents = base.productionBatchComponents + extraComp,
                productionBatches = batches,
                inventoryMovements = movements,
                inventoryBalanceProjections = balanceProjections,
                ingredientCostProjections = costProjections
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.DUPLICATE_PRODUCTION_COMPONENT)
    }

    private fun createValidSnapshot(): BackupSnapshotDto {
        val t0 = 1000L
        val t1 = 2000L
        
        return BackupSnapshotDto(
            restaurants = listOf(RestaurantBackupDto(restId, "Rest", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(InventoryAreaBackupDto(areaId, restId, "Area", "area", 0, true, 0, 0, null)),
            ingredientCategories = emptyList(),
            units = listOf(UnitBackupDto("u1", "U", "u", "COUNT", "1.0", true, 0)),
            ingredients = listOf(
                IngredientBackupDto(ingCompId, restId, "Comp", "comp", null, "u1", areaId, null, null, null, true, 0, 0, null),
                IngredientBackupDto(ingOutId, restId, "Out", "out", null, "u1", areaId, null, null, null, true, 0, 0, null)
            ),
            ingredientUnitOptions = listOf(
                IngredientUnitOptionBackupDto(optCompId, ingCompId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null),
                IngredientUnitOptionBackupDto(optOutId, ingOutId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
            ),
            suppliers = emptyList(),
            purchaseReceipts = listOf(
                PurchaseReceiptBackupDto("p1", restId, null, "INV1", t0, "POSTED", null, null, 0, 0, t0, null)
            ),
            purchaseLines = listOf(
                PurchaseLineBackupDto("pl1", "p1", ingCompId, areaId, optCompId, "10", "10", "10.00", "100.00", null, 0, 0)
            ),
            stockCounts = emptyList(),
            stockCountAreas = emptyMap<String, String>().let { emptyList() }, // fix for ambiguity if any
            stockCountLines = emptyList(),
            wasteEvents = emptyList(),
            inventoryMovements = listOf(
                InventoryMovementBackupDto("m1", restId, ingCompId, areaId, "PURCHASE", "10", "10.00", "100.00", t0, "PURCHASE_RECEIPT", "p1", "op1", "pl1", null, t0),
                InventoryMovementBackupDto("m2", restId, ingCompId, areaId, "PRODUCTION_CONSUMPTION", "-1", "10.00", "-10.00", t1, "PRODUCTION_BATCH", batchId, "production-post:$batchId:consume:pbc1", "pbc1", null, t1),
                InventoryMovementBackupDto("m3", restId, ingOutId, areaId, "PRODUCTION_OUTPUT", "2", "5.00", "10.00", t1, "PRODUCTION_BATCH", batchId, "production-post:$batchId:output", batchId, null, t1)
            ),
            inventoryBalanceProjections = listOf(
                InventoryBalanceProjectionBackupDto(restId, ingCompId, areaId, "9", t1),
                InventoryBalanceProjectionBackupDto(restId, ingOutId, areaId, "2", t1)
            ),
            ingredientCostProjections = listOf(
                IngredientCostProjectionBackupDto(restId, ingCompId, "10.00", t1),
                IngredientCostProjectionBackupDto(restId, ingOutId, "5.00", t1)
            ),
            preparationRecipes = listOf(
                PreparationRecipeBackupDto(recipeId, restId, ingOutId, "Recipe", "recipe", "2", "2", optOutId, "ACTIVE", null, 0, 0, null)
            ),
            preparationRecipeComponents = listOf(
                PreparationRecipeComponentBackupDto("rc1", recipeId, ingCompId, optCompId, "1", "1", 0, null, 0, 0)
            ),
            productionBatches = listOf(
                ProductionBatchBackupDto(batchId, restId, recipeId, "Recipe", ingOutId, "1.0", "2", "2", optOutId, "2", "2", "2", "2", optOutId, areaId, false, "10.00", "5.00", t1, "POSTED", null, 0, t1, t1, null)
            ),
            productionBatchComponents = listOf(
                ProductionBatchComponentBackupDto("pbc1", batchId, "rc1", ingCompId, "1", "1", optCompId, "1", "1", "1", "1", optCompId, false, areaId, "10.00", "10.00", 0, null, 0, t1)
            )
        )
    }

    private fun createManifest(dto: BackupSnapshotDto) = BackupManifest(
        backupFormatVersion = 1,
        createdAtUtc = "2026-01-01T12:00:00Z",
        applicationId = "com.miara.cuentame",
        appVersionName = "1.0",
        appVersionCode = 1,
        databaseSchemaVersion = 4,
        restaurantId = restId,
        restaurantName = "Rest",
        localeTag = "en-US",
        currencyCode = "USD",
        tableMetadata = emptyMap(),
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments")
    )

    private fun assertIntegrityCode(result: Result<Unit>, expected: BackupSnapshotIntegrityCode) {
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as BackupSnapshotIntegrityException
        assertThat(exception.code).isEqualTo(expected)
    }
}
