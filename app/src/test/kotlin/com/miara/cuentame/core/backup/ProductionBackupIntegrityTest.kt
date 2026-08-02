package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.*
import com.miara.cuentame.core.model.backup.BackupManifest
import com.miara.cuentame.core.backup.api.BackupFormatV1Contract
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
        tableMetadata = BackupFormatV1Contract.EXPECTED_TABLES.associateWith { 
            com.miara.cuentame.core.model.backup.TableMetadata(0, it in BackupFormatV1Contract.DERIVED_TABLES)
        },
        attachments = emptyList(),
        includedSections = listOf("data", "preferences", "attachments"),
        checksumAlgorithm = "SHA-256"
    )

    private fun assertIntegrityCode(result: Result<Unit>, expected: BackupSnapshotIntegrityCode) {
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as? BackupSnapshotIntegrityException
        assertThat(exception?.code).isEqualTo(expected)
    }

    @Test
    fun futurePurchaseExcluded_remainsValid() {
        val tFuture = 3000L
        val dto = createValidSnapshot().let { base ->
            // Add a future purchase at a different cost (30.00)
            val futureMove = InventoryMovementBackupDto(
                "m-future", restId, ingCompId, areaId, "PURCHASE", "1", "30.00", "30.00", tFuture, "PURCHASE_RECEIPT", "p2", "op2", "pl2", null, tFuture
            )
            // Update projections to reflect current state (including future)
            // Balance: 9 + 1 = 10
            // Cost: (9*10 + 1*30) / 10 = 120 / 10 = 12.00
            val balance = base.inventoryBalanceProjections.map {
                if (it.ingredientId == ingCompId) it.copy(quantityBase = "10") else it
            }
            val cost = base.ingredientCostProjections.map {
                if (it.ingredientId == ingCompId) it.copy(averageUnitCostBase = "12.00") else it
            }
            base.copy(
                inventoryMovements = base.inventoryMovements + futureMove,
                inventoryBalanceProjections = balance,
                ingredientCostProjections = cost
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun backdatedLaterCreatedPurchaseExcluded_remainsValid() {
        val tPast = 500L
        val tCreatedLater = 4000L
        val dto = createValidSnapshot().let { base ->
            // Add a backdated purchase but created AFTER the production batch was posted
            val backdatedMove = InventoryMovementBackupDto(
                "m-backdated", restId, ingCompId, areaId, "PURCHASE", "1", "30.00", "30.00", tPast, "PURCHASE_RECEIPT", "p2", "op2", "pl2", null, tCreatedLater
            )
            // Projections reflect current state
            val balance = base.inventoryBalanceProjections.map {
                if (it.ingredientId == ingCompId) it.copy(quantityBase = "10") else it
            }
            val cost = base.ingredientCostProjections.map {
                if (it.ingredientId == ingCompId) it.copy(averageUnitCostBase = "12.00") else it
            }
            base.copy(
                inventoryMovements = base.inventoryMovements + backdatedMove,
                inventoryBalanceProjections = balance,
                ingredientCostProjections = cost
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun futureReversalExcluded_remainsValid() {
        val tFuture = 3000L
        val dto = createValidSnapshot().let { base ->
            // Reverse the original purchase in the future
            val original = base.inventoryMovements.find { it.id == "m1" }!!
            val reversal = original.copy(
                id = "m-rev",
                movementType = "REVERSAL",
                quantityBaseSigned = "-10",
                totalValueSnapshot = "-100.00",
                effectiveAt = tFuture,
                createdAt = tFuture,
                reversalOfMovementId = "m1",
                sourceOperationId = "reversal:m1"
            )
            // Projections reflect current state (m1 no longer contributes)
            // Balance for ingCompId: 9 - 10 = -1 (Waste/Consumption remain)
            // Cost for ingCompId: null
            val balance = base.inventoryBalanceProjections.map {
                if (it.ingredientId == ingCompId) it.copy(quantityBase = "-1") else it
            }
            val cost = base.ingredientCostProjections.filter { it.ingredientId != ingCompId }
            
            base.copy(
                inventoryMovements = base.inventoryMovements + reversal,
                inventoryBalanceProjections = balance,
                ingredientCostProjections = cost
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun priorReversalApplied_failsOnMismatch() {
        val tPrior = 800L
        val dto = createValidSnapshot().let { base ->
            // Reverse the original purchase BEFORE production
            val original = base.inventoryMovements.find { it.id == "m1" }!!
            val reversal = original.copy(
                id = "m-rev",
                movementType = "REVERSAL",
                quantityBaseSigned = "-10",
                totalValueSnapshot = "-100.00",
                effectiveAt = tPrior,
                createdAt = tPrior,
                reversalOfMovementId = "m1",
                sourceOperationId = "reversal:m1"
            )
            // Now at t1 (batch post), there is NO cost established for ingCompId
            base.copy(inventoryMovements = base.inventoryMovements + reversal)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        // Rebuilder would fail to build cost, and validator sees mismatch because batch has cost 10.00
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_PRODUCTION_COST_HISTORY)
    }

    @Test
    fun validVoidedProductionSnapshot_passes() {
        val dto = createValidVoidedSnapshot()
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun voided_missingReversal_returnsInvalidDocumentLifecycle() {
        val dto = createValidVoidedSnapshot().let { base ->
            base.copy(inventoryMovements = base.inventoryMovements.filter { it.id != "m-rev-consume" })
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE)
    }

    @Test
    fun missingHistoricalCost_returnsInvalidProductionCostHistory() {
        val dto = createValidSnapshot().let { base ->
            // Remove the establishing purchase
            base.copy(inventoryMovements = base.inventoryMovements.filter { it.movementType != "PURCHASE" })
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_PRODUCTION_COST_HISTORY)
    }

    @Test
    fun duplicateConsumptionSourceLineId_returnsInvalidMovementGraph() {
        val dto = createValidSnapshot().let { base ->
            val m2 = base.inventoryMovements.find { it.id == "m2" }!!
            val duplicateMove = m2.copy(id = "m2-dup")
            base.copy(inventoryMovements = base.inventoryMovements + duplicateMove)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_MOVEMENT_GRAPH)
    }

    @Test
    fun wrongConsumptionQuantity_returnsInvalidNumericRange() {
        val dto = createValidSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m2") it.copy(quantityBaseSigned = "-99") else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_NUMERIC_RANGE)
    }

    @Test
    fun wrongOperationId_returnsInvalidMovementGraph() {
        val dto = createValidSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m2") it.copy(sourceOperationId = "wrong-op") else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_MOVEMENT_GRAPH)
    }

    @Test
    fun upstreamProductionIncluded_remainsValid() {
        val t0 = 500L
        val t1 = 1500L
        val t2 = 2500L
        // T0: raw purchase
        // T1: intermediate production (raw -> intermediate)
        // T2: final production (intermediate -> final)
        
        val rawIngId = "raw"
        val interIngId = "intermediate"
        val finalIngId = "final"
        
        val dto = createValidSnapshot().let { base ->
            val ingredients = listOf(
                IngredientBackupDto(rawIngId, restId, "Raw", "raw", null, "u1", areaId, null, null, null, true, 0, 0, null),
                IngredientBackupDto(interIngId, restId, "Inter", "inter", null, "u1", areaId, null, null, null, true, 0, 0, null),
                IngredientBackupDto(finalIngId, restId, "Final", "final", null, "u1", areaId, null, null, null, true, 0, 0, null)
            )
            val options = listOf(
                IngredientUnitOptionBackupDto("opt-raw", rawIngId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null),
                IngredientUnitOptionBackupDto("opt-inter", interIngId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null),
                IngredientUnitOptionBackupDto("opt-final", finalIngId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
            )
            
            val movements = listOf(
                InventoryMovementBackupDto("m-raw-in", restId, rawIngId, areaId, "PURCHASE", "10", "10.00", "100.00", t0, "PURCHASE_RECEIPT", "p-raw", "op1", "pl1", null, t0),
                // Intermediate production at T1
                InventoryMovementBackupDto("m-inter-consume", restId, rawIngId, areaId, "PRODUCTION_CONSUMPTION", "-5", "10.00", "-50.00", t1, "PRODUCTION_BATCH", "pb-inter", "production-post:pb-inter:consume:pbc1", "pbc1", null, t1),
                InventoryMovementBackupDto("m-inter-out", restId, interIngId, areaId, "PRODUCTION_OUTPUT", "1", "50.00", "50.00", t1, "PRODUCTION_BATCH", "pb-inter", "production-post:pb-inter:output", "pb-inter", null, t1),
                // Final production at T2
                InventoryMovementBackupDto("m-final-consume", restId, interIngId, areaId, "PRODUCTION_CONSUMPTION", "-1", "50.00", "-50.00", t2, "PRODUCTION_BATCH", "pb-final", "production-post:pb-final:consume:pbc1", "pbc-f1", null, t2),
                InventoryMovementBackupDto("m-final-out", restId, finalIngId, areaId, "PRODUCTION_OUTPUT", "1", "50.00", "50.00", t2, "PRODUCTION_BATCH", "pb-final", "production-post:pb-final:output", "pb-final", null, t2)
            )
            
            val batches = listOf(
                ProductionBatchBackupDto("pb-inter", restId, "rec-inter", "Rec Inter", interIngId, "1.0", "1", "1", "opt-inter", "1", "1", "1", "1", "opt-inter", areaId, false, "50.00", "50.00", t1, "POSTED", null, 0, t1, t1, null),
                ProductionBatchBackupDto("pb-final", restId, "rec-final", "Rec Final", finalIngId, "1.0", "1", "1", "opt-final", "1", "1", "1", "1", "opt-final", areaId, false, "50.00", "50.00", t2, "POSTED", null, 0, t2, t2, null)
            )
            
            val batchComponents = listOf(
                ProductionBatchComponentBackupDto("pbc1", "pb-inter", "rc1", rawIngId, "5", "5", "opt-raw", "5", "5", "5", "5", "opt-raw", false, areaId, "10.00", "50.00", 0, null, 0, t1),
                ProductionBatchComponentBackupDto("pbc-f1", "pb-final", "rc-f1", interIngId, "1", "1", "opt-inter", "1", "1", "1", "1", "opt-inter", false, areaId, "50.00", "50.00", 0, null, 0, t2)
            )

            val projections = listOf(
                InventoryBalanceProjectionBackupDto(restId, rawIngId, areaId, "5", t2),
                InventoryBalanceProjectionBackupDto(restId, interIngId, areaId, "0", t2),
                InventoryBalanceProjectionBackupDto(restId, finalIngId, areaId, "1", t2)
            )
            val costProjections = listOf(
                IngredientCostProjectionBackupDto(restId, rawIngId, "10.00", t2),
                IngredientCostProjectionBackupDto(restId, interIngId, "50.00", t2),
                IngredientCostProjectionBackupDto(restId, finalIngId, "50.00", t2)
            )

            base.copy(
                ingredients = ingredients,
                ingredientUnitOptions = options,
                inventoryMovements = movements,
                productionBatches = batches,
                productionBatchComponents = batchComponents,
                inventoryBalanceProjections = projections,
                ingredientCostProjections = costProjections,
                // Keep recipes valid if needed, though validator mainly uses DTO fields
                preparationRecipes = listOf(
                    PreparationRecipeBackupDto("rec-inter", restId, interIngId, "Rec Inter", "rec inter", "1", "1", "opt-inter", "ACTIVE", null, 0, 0, null),
                    PreparationRecipeBackupDto("rec-final", restId, finalIngId, "Rec Final", "rec final", "1", "1", "opt-final", "ACTIVE", null, 0, 0, null)
                ),
                preparationRecipeComponents = listOf(
                    PreparationRecipeComponentBackupDto("rc1", "rec-inter", rawIngId, "opt-raw", "5", "5", 0, null, 0, 0),
                    PreparationRecipeComponentBackupDto("rc-f1", "rec-final", interIngId, "opt-inter", "1", "1", 0, null, 0, 0)
                )
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertThat(result.isSuccess).isTrue()
    }

    private fun createValidVoidedSnapshot(): BackupSnapshotDto {
        val tVoid = 3000L
        val base = createValidSnapshot()
        
        val batch = base.productionBatches[0].copy(
            status = "VOIDED",
            voidedAt = tVoid
        )
        
        val consumeRev = base.inventoryMovements.find { it.id == "m2" }!!.copy(
            id = "m-rev-consume",
            movementType = "REVERSAL",
            quantityBaseSigned = "1",
            totalValueSnapshot = "10.00",
            effectiveAt = tVoid,
            createdAt = tVoid,
            reversalOfMovementId = "m2",
            sourceOperationId = "reversal:m2"
        )
        
        val outputRev = base.inventoryMovements.find { it.id == "m3" }!!.copy(
            id = "m-rev-output",
            movementType = "REVERSAL",
            quantityBaseSigned = "-2",
            totalValueSnapshot = "-10.00",
            effectiveAt = tVoid,
            createdAt = tVoid,
            reversalOfMovementId = "m3",
            sourceOperationId = "reversal:m3"
        )
        
        // Effective balances after void (only m1 purchase remains)
        val balance = listOf(
            InventoryBalanceProjectionBackupDto(restId, ingCompId, areaId, "10", tVoid),
            InventoryBalanceProjectionBackupDto(restId, ingOutId, areaId, "0", tVoid)
        )
        
        // Effective cost after void (i1 cost 10, i2 cost null)
        val cost = listOf(
            IngredientCostProjectionBackupDto(restId, ingCompId, "10.00", tVoid)
        )
        
        return base.copy(
            productionBatches = listOf(batch),
            inventoryMovements = base.inventoryMovements + consumeRev + outputRev,
            inventoryBalanceProjections = balance,
            ingredientCostProjections = cost
        )
    }
}
