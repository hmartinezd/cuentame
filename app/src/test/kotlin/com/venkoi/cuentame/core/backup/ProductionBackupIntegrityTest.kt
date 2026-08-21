package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.model.*
import com.venkoi.cuentame.core.model.backup.BackupManifest
import com.venkoi.cuentame.core.backup.api.BackupFormatV1Contract
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
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
        if (result.isFailure) {
            val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
            throw Exception("Failed with code: ${ex?.code}, msg: ${ex?.message}")
        }
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

        val empty = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(RestaurantBackupDto(restId, "Rest", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(InventoryAreaBackupDto(areaId, restId, "Area", "area", 0, true, 0, 0, null)),
            units = listOf(UnitBackupDto("u1", "U", "u", "COUNT", "1.0", true, 0)),
            ingredients = listOf(
                IngredientBackupDto(ingCompId, restId, "Comp", "comp", null, "u1", areaId, null, null, null, true, 0, 0, null),
                IngredientBackupDto(ingOutId, restId, "Out", "out", null, "u1", areaId, null, null, null, true, 0, 0, null)
            ),
            ingredientUnitOptions = listOf(
                IngredientUnitOptionBackupDto(optCompId, ingCompId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null),
                IngredientUnitOptionBackupDto(optOutId, ingOutId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
            )
        )

        val baseWithPurchase = BackupTestFixtures.addPostedPurchase(
            snapshot = empty,
            receiptId = "p1",
            lineId = "pl1",
            movementId = "m1",
            ingredientId = ingCompId,
            areaId = areaId,
            optionId = optCompId,
            quantityBase = BigDecimal("10"),
            unitCostBase = BigDecimal("10.00"),
            effectiveAt = t0,
            createdAt = t0
        )

        return baseWithPurchase.copy(
            inventoryMovements = baseWithPurchase.inventoryMovements + listOf(
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

    private fun createManifest(dto: BackupSnapshotDto): BackupManifest {
        val actualCounts = mapOf(
            "restaurants" to dto.restaurants.size,
            "inventory_areas" to dto.inventoryAreas.size,
            "ingredient_categories" to dto.ingredientCategories.size,
            "units" to dto.units.size,
            "ingredients" to dto.ingredients.size,
            "ingredient_unit_options" to dto.ingredientUnitOptions.size,
            "suppliers" to dto.suppliers.size,
            "purchase_receipts" to dto.purchaseReceipts.size,
            "purchase_lines" to dto.purchaseLines.size,
            "stock_counts" to dto.stockCounts.size,
            "stock_count_areas" to dto.stockCountAreas.size,
            "stock_count_lines" to dto.stockCountLines.size,
            "waste_events" to dto.wasteEvents.size,
            "inventory_movements" to dto.inventoryMovements.size,
            "inventory_balance_projections" to dto.inventoryBalanceProjections.size,
            "ingredient_cost_projections" to dto.ingredientCostProjections.size,
            "preparation_recipes" to dto.preparationRecipes.size,
            "preparation_recipe_components" to dto.preparationRecipeComponents.size,
            "production_batches" to dto.productionBatches.size,
            "production_batch_components" to dto.productionBatchComponents.size
        )

        return BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.venkoi.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseSchemaVersion = 4,
            restaurantId = restId,
            restaurantName = "Rest",
            localeTag = "en-US",
            currencyCode = "USD",
            tableMetadata = actualCounts.mapValues { (table, count) ->
                com.venkoi.cuentame.core.model.backup.TableMetadata(count, table in BackupFormatV1Contract.DERIVED_TABLES)
            },
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments"),
            checksumAlgorithm = "SHA-256"
        )
    }

    private fun assertIntegrityCode(result: Result<Unit>, expected: BackupSnapshotIntegrityCode) {
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as? BackupSnapshotIntegrityException
        assertThat(exception?.code).isEqualTo(expected)
    }

    @Test
    fun futurePurchaseExcluded_remainsValid() {
        val tFuture = 3000L
        val dto = createValidSnapshot().let { base ->
            // Add a complete future purchase at a different cost (30.00)
            val baseWithFuture = BackupTestFixtures.addPostedPurchase(
                snapshot = base,
                receiptId = "p2",
                lineId = "pl2",
                movementId = "m-future",
                ingredientId = ingCompId,
                areaId = areaId,
                optionId = optCompId,
                quantityBase = BigDecimal("1"),
                unitCostBase = BigDecimal("30.00"),
                effectiveAt = tFuture,
                createdAt = tFuture
            )

            // Update projections to reflect current state (including future)
            // Balance: 9 + 1 = 10
            // Cost: (9*10 + 1*30) / 10 = 120 / 10 = 12.00
            val balance = baseWithFuture.inventoryBalanceProjections.map {
                if (it.ingredientId == ingCompId) it.copy(quantityBase = "10") else it
            }
            val cost = baseWithFuture.ingredientCostProjections.map {
                if (it.ingredientId == ingCompId) it.copy(averageUnitCostBase = "12.00") else it
            }
            baseWithFuture.copy(
                inventoryBalanceProjections = balance,
                ingredientCostProjections = cost
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        if (result.isFailure) {
            val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
            throw Exception("Failed with code: ${ex?.code}, msg: ${ex?.message}")
        }
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun backdatedLaterCreatedPurchaseExcluded_remainsValid() {
        val tPast = 500L
        val tCreatedLater = 4000L
        val dto = createValidSnapshot().let { base ->
            // Add a complete backdated purchase but created AFTER the production batch was posted
            val baseWithBackdated = BackupTestFixtures.addPostedPurchase(
                snapshot = base,
                receiptId = "p2",
                lineId = "pl2",
                movementId = "m-backdated",
                ingredientId = ingCompId,
                areaId = areaId,
                optionId = optCompId,
                quantityBase = BigDecimal("1"),
                unitCostBase = BigDecimal("30.00"),
                effectiveAt = tPast,
                createdAt = tCreatedLater
            )

            // Projections reflect current state
            // Backdated purchase first: 1 at 30
            // Original purchase second: 10 at 10
            // Weighted average before consumption: (1*30 + 10*10) / 11 = 130 / 11
            val expectedCost = BigDecimal("130").divide(BigDecimal("11"), java.math.MathContext.DECIMAL128).toPlainString()

            val balance = baseWithBackdated.inventoryBalanceProjections.map {
                if (it.ingredientId == ingCompId) it.copy(quantityBase = "10") else it
            }
            val cost = baseWithBackdated.ingredientCostProjections.map {
                if (it.ingredientId == ingCompId) it.copy(averageUnitCostBase = expectedCost) else it
            }
            baseWithBackdated.copy(
                inventoryBalanceProjections = balance,
                ingredientCostProjections = cost
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        if (result.isFailure) {
            val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
            throw Exception("Failed with code: ${ex?.code}, msg: ${ex?.message}")
        }
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun futureReversalExcluded_remainsValid() {
        val tFuture = 3000L
        val dto = createValidSnapshot().let { base ->
            // Void the original purchase in the future
            val originalReceipt = base.purchaseReceipts.find { it.id == "p1" }!!
            val updatedReceipts = base.purchaseReceipts.map {
                if (it.id == "p1") it.copy(status = "VOIDED", voidedAt = tFuture) else it
            }

            val originalMove = base.inventoryMovements.find { it.id == "m1" }!!
            val reversal = originalMove.copy(
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
                purchaseReceipts = updatedReceipts,
                inventoryMovements = base.inventoryMovements + reversal,
                inventoryBalanceProjections = balance,
                ingredientCostProjections = cost
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        if (result.isFailure) {
            val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
            throw Exception("Failed with code: ${ex?.code}, msg: ${ex?.message}")
        }
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun priorReversalApplied_failsOnMismatch() {
        val tPurchase = 500L
        val tReversal = 1000L
        val tProduction = 2000L

        val empty = BackupTestFixtures.createEmptySnapshotDto().copy(
            restaurants = listOf(RestaurantBackupDto(restId, "Rest", "USD", "en-US", 0, 0, null)),
            inventoryAreas = listOf(InventoryAreaBackupDto(areaId, restId, "Area", "area", 0, true, 0, 0, null)),
            units = listOf(UnitBackupDto("u1", "U", "u", "COUNT", "1.0", true, 0)),
            ingredients = listOf(
                IngredientBackupDto(ingCompId, restId, "Comp", "comp", null, "u1", areaId, null, null, null, true, 0, 0, null),
                IngredientBackupDto(ingOutId, restId, "Out", "out", null, "u1", areaId, null, null, null, true, 0, 0, null)
            ),
            ingredientUnitOptions = listOf(
                IngredientUnitOptionBackupDto(optCompId, ingCompId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null),
                IngredientUnitOptionBackupDto(optOutId, ingOutId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
            )
        )

        val baseWithPurchase = BackupTestFixtures.addPostedPurchase(
            snapshot = empty,
            receiptId = "p1",
            lineId = "pl1",
            movementId = "m1",
            ingredientId = ingCompId,
            areaId = areaId,
            optionId = optCompId,
            quantityBase = BigDecimal("10"),
            unitCostBase = BigDecimal("10.00"),
            effectiveAt = tPurchase,
            createdAt = tPurchase
        )

        val dto = baseWithPurchase.let { base ->
            // Void the original purchase BEFORE production
            val updatedReceipts = base.purchaseReceipts.map {
                if (it.id == "p1") it.copy(status = "VOIDED", voidedAt = tReversal, postedAt = tPurchase) else it
            }

            val originalMove = base.inventoryMovements.find { it.id == "m1" }!!
            val reversal = originalMove.copy(
                id = "m-rev",
                movementType = "REVERSAL",
                quantityBaseSigned = "-10",
                totalValueSnapshot = "-100.00",
                effectiveAt = tReversal,
                createdAt = tReversal,
                reversalOfMovementId = "m1",
                sourceOperationId = "reversal:m1"
            )

            val batch = ProductionBatchBackupDto(
                batchId, restId, recipeId, "Recipe", ingOutId, "1.0", "2", "2", optOutId, "2", "2", "2", "2", optOutId, areaId, false, "10.00", "5.00", tProduction, "POSTED", null, 0, tProduction, tProduction, null
            )
            val component = ProductionBatchComponentBackupDto(
                "pbc1", batchId, "rc1", ingCompId, "1", "1", optCompId, "1", "1", "1", "1", optCompId, false, areaId, "10.00", "10.00", 0, null, 0, tProduction
            )
            val moveConsume = InventoryMovementBackupDto(
                "m2", restId, ingCompId, areaId, "PRODUCTION_CONSUMPTION", "-1", "10.00", "-10.00", tProduction, "PRODUCTION_BATCH", batchId, "production-post:$batchId:consume:pbc1", "pbc1", null, tProduction
            )
            val moveOutput = InventoryMovementBackupDto(
                "m3", restId, ingOutId, areaId, "PRODUCTION_OUTPUT", "2", "5.00", "10.00", tProduction, "PRODUCTION_BATCH", batchId, "production-post:$batchId:output", batchId, null, tProduction
            )

            // Projections:
            // ingCompId: 10 (m1) - 10 (rev) - 1 (consume) = -1. No cost.
            // ingOutId: 2 (output). Cost 5.00.
            val balance = listOf(
                InventoryBalanceProjectionBackupDto(restId, ingCompId, areaId, "-1", tProduction),
                InventoryBalanceProjectionBackupDto(restId, ingOutId, areaId, "2", tProduction)
            )
            val cost = listOf(
                IngredientCostProjectionBackupDto(restId, ingOutId, "5.00", tProduction)
            )

            base.copy(
                purchaseReceipts = updatedReceipts,
                inventoryMovements = base.inventoryMovements + reversal + moveConsume + moveOutput,
                productionBatches = listOf(batch),
                productionBatchComponents = listOf(component),
                inventoryBalanceProjections = balance,
                ingredientCostProjections = cost,
                preparationRecipes = listOf(
                    PreparationRecipeBackupDto(recipeId, restId, ingOutId, "Recipe", "recipe", "2", "2", optOutId, "ACTIVE", null, 0, 0, null)
                ),
                preparationRecipeComponents = listOf(
                    PreparationRecipeComponentBackupDto("rc1", recipeId, ingCompId, optCompId, "1", "1", 0, null, 0, 0)
                )
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_PRODUCTION_COST_HISTORY)
    }

    @Test
    fun validVoidedProductionSnapshot_passes() {
        val dto = createValidVoidedSnapshot()
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        if (result.isFailure) {
            val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
            throw Exception("Failed with code: ${ex?.code}, msg: ${ex?.message}")
        }
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun voided_missingOutputReversal_returnsInvalidDocumentLifecycle() {
        val dto = createValidVoidedSnapshot().let { base ->
            base.copy(inventoryMovements = base.inventoryMovements.filter { it.id != "m-rev-output" })
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_DOCUMENT_LIFECYCLE)
    }

    @Test
    fun voided_duplicateReversalTarget_returnsInvalidReversal() {
        val dto = createValidVoidedSnapshot().let { base ->
            val extraRev = base.inventoryMovements.find { it.id == "m-rev-consume" }!!.copy(
                id = "m-rev-extra",
                sourceOperationId = "reversal-extra:m2"
            )
            base.copy(inventoryMovements = base.inventoryMovements + extraRev)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun voided_wrongReversalQuantity_returnsInvalidReversal() {
        val dto = createValidVoidedSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m-rev-consume") it.copy(quantityBaseSigned = "99") else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun voided_wrongReversalUnitCost_returnsInvalidReversal() {
        val dto = createValidVoidedSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m-rev-consume") it.copy(unitCostBaseSnapshot = "99.00") else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun voided_wrongReversalTotalValue_returnsInvalidReversal() {
        val dto = createValidVoidedSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m-rev-consume") it.copy(totalValueSnapshot = "99.00") else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun voided_wrongReversalIngredient_returnsInvalidReversal() {
        val dto = createValidVoidedSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m-rev-consume") it.copy(ingredientId = ingOutId) else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun voided_wrongReversalArea_returnsInvalidReversal() {
        val base = createValidVoidedSnapshot()
        
        // If non-existent, it fails BROKEN_FOREIGN_KEY first.
        // To test INVALID_REVERSAL, we need an area that exists but is wrong.
        val area2 = InventoryAreaBackupDto("a2", restId, "Area 2", "area 2", 1, true, 0, 0, null)
        val dtoWithArea = base.copy(inventoryAreas = base.inventoryAreas + area2)
        val movements2 = dtoWithArea.inventoryMovements.map {
            if (it.id == "m-rev-consume") it.copy(areaId = "a2") else it
        }
        val finalDto = dtoWithArea.copy(inventoryMovements = movements2)
        val manifest2 = createManifest(finalDto)

        val result2 = BackupSnapshotIntegrityValidator.validate(finalDto, manifest2)
        assertIntegrityCode(result2, BackupSnapshotIntegrityCode.INVALID_REVERSAL)
    }

    @Test
    fun voided_wrongReversalEffectiveAt_returnsInvalidTimestampOrder() {
        val dto = createValidVoidedSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m-rev-consume") it.copy(effectiveAt = 0) else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_TIMESTAMP_ORDER)
    }

    @Test
    fun voided_wrongReversalCreatedAt_returnsInvalidTimestampOrder() {
        val dto = createValidVoidedSnapshot().let { base ->
            val movements = base.inventoryMovements.map {
                if (it.id == "m-rev-consume") it.copy(createdAt = 0) else it
            }
            base.copy(inventoryMovements = movements)
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        assertIntegrityCode(result, BackupSnapshotIntegrityCode.INVALID_TIMESTAMP_ORDER)
    }

    @Test
    fun missingHistoricalCost_returnsInvalidProductionCostHistory() {
        val dto = createValidSnapshot().let { base ->
            // Keep movements to satisfy lifecycle, but remove the unit cost established by m1
            val movements = base.inventoryMovements.map {
                if (it.id == "m1") it.copy(unitCostBaseSnapshot = null, totalValueSnapshot = null)
                else it
            }
            // Update purchase line to match
            val lines = base.purchaseLines.map {
                if (it.id == "pl1") it.copy(unitCostBase = "0.00", lineTotal = "0.00")
                else it
            }
            // Update projections
            val balance = base.inventoryBalanceProjections.map {
                if (it.ingredientId == ingCompId) it.copy(quantityBase = "9") else it
            }
            val cost = base.ingredientCostProjections.filter { it.ingredientId != ingCompId }

            base.copy(inventoryMovements = movements, purchaseLines = lines, inventoryBalanceProjections = balance, ingredientCostProjections = cost)
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
            val extraIngredients = listOf(
                IngredientBackupDto(rawIngId, restId, "Raw", "raw", null, "u1", areaId, null, null, null, true, 0, 0, null),
                IngredientBackupDto(interIngId, restId, "Inter", "inter", null, "u1", areaId, null, null, null, true, 0, 0, null),
                IngredientBackupDto(finalIngId, restId, "Final", "final", null, "u1", areaId, null, null, null, true, 0, 0, null)
            )
            val extraOptions = listOf(
                IngredientUnitOptionBackupDto("opt-raw", rawIngId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null),
                IngredientUnitOptionBackupDto("opt-inter", interIngId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null),
                IngredientUnitOptionBackupDto("opt-final", finalIngId, "Opt", "o", null, "1.0", true, true, true, true, 0, 0, null)
            )

            val baseWithRawPurchase = BackupTestFixtures.addPostedPurchase(
                snapshot = base.copy(
                    ingredients = base.ingredients + extraIngredients,
                    ingredientUnitOptions = base.ingredientUnitOptions + extraOptions
                ),
                receiptId = "p-raw",
                lineId = "pl-raw",
                movementId = "m-raw-in",
                ingredientId = rawIngId,
                areaId = areaId,
                optionId = "opt-raw",
                quantityBase = BigDecimal("10"),
                unitCostBase = BigDecimal("10.00"),
                effectiveAt = t0,
                createdAt = t0
            )

            val movements = baseWithRawPurchase.inventoryMovements + listOf(
                // Intermediate production at T1
                InventoryMovementBackupDto("m-inter-consume", restId, rawIngId, areaId, "PRODUCTION_CONSUMPTION", "-5", "10.00", "-50.00", t1, "PRODUCTION_BATCH", "pb-inter", "production-post:pb-inter:consume:pbc-inter", "pbc-inter", null, t1),
                InventoryMovementBackupDto("m-inter-out", restId, interIngId, areaId, "PRODUCTION_OUTPUT", "1", "50.00", "50.00", t1, "PRODUCTION_BATCH", "pb-inter", "production-post:pb-inter:output", "pb-inter", null, t1),
                // Final production at T2
                InventoryMovementBackupDto("m-final-consume", restId, interIngId, areaId, "PRODUCTION_CONSUMPTION", "-1", "50.00", "-50.00", t2, "PRODUCTION_BATCH", "pb-final", "production-post:pb-final:consume:pbc-f1", "pbc-f1", null, t2),
                InventoryMovementBackupDto("m-final-out", restId, finalIngId, areaId, "PRODUCTION_OUTPUT", "1", "50.00", "50.00", t2, "PRODUCTION_BATCH", "pb-final", "production-post:pb-final:output", "pb-final", null, t2)
            )

            val extraBatches = listOf(
                ProductionBatchBackupDto("pb-inter", restId, "rec-inter", "Rec Inter", interIngId, "1.0", "1", "1", "opt-inter", "1", "1", "1", "1", "opt-inter", areaId, false, "50.00", "50.00", t1, "POSTED", null, 0, t1, t1, null),
                ProductionBatchBackupDto("pb-final", restId, "rec-final", "Rec Final", finalIngId, "1.0", "1", "1", "opt-final", "1", "1", "1", "1", "opt-final", areaId, false, "50.00", "50.00", t2, "POSTED", null, 0, t2, t2, null)
            )

            val extraBatchComponents = listOf(
                ProductionBatchComponentBackupDto("pbc-inter", "pb-inter", "rc-inter", rawIngId, "5", "5", "opt-raw", "5", "5", "5", "5", "opt-raw", false, areaId, "10.00", "50.00", 0, null, 0, t1),
                ProductionBatchComponentBackupDto("pbc-f1", "pb-final", "rc-f1", interIngId, "1", "1", "opt-inter", "1", "1", "1", "1", "opt-inter", false, areaId, "50.00", "50.00", 0, null, 0, t2)
            )

            val projections = listOf(
                InventoryBalanceProjectionBackupDto(restId, rawIngId, areaId, "5", t2),
                InventoryBalanceProjectionBackupDto(restId, interIngId, areaId, "0", t2),
                InventoryBalanceProjectionBackupDto(restId, finalIngId, areaId, "1", t2),
                InventoryBalanceProjectionBackupDto(restId, ingCompId, areaId, "9", t2),
                InventoryBalanceProjectionBackupDto(restId, ingOutId, areaId, "2", t2)
            )
            val costProjections = listOf(
                IngredientCostProjectionBackupDto(restId, rawIngId, "10.00", t2),
                IngredientCostProjectionBackupDto(restId, interIngId, "50.00", t2),
                IngredientCostProjectionBackupDto(restId, finalIngId, "50.00", t2),
                IngredientCostProjectionBackupDto(restId, ingCompId, "10.00", t2),
                IngredientCostProjectionBackupDto(restId, ingOutId, "5.00", t2)
            )

            baseWithRawPurchase.copy(
                inventoryMovements = movements,
                productionBatches = baseWithRawPurchase.productionBatches + extraBatches,
                productionBatchComponents = baseWithRawPurchase.productionBatchComponents + extraBatchComponents,
                inventoryBalanceProjections = projections,
                ingredientCostProjections = costProjections,
                preparationRecipes = baseWithRawPurchase.preparationRecipes + listOf(
                    PreparationRecipeBackupDto("rec-inter", restId, interIngId, "Rec Inter", "rec inter", "1", "1", "opt-inter", "ACTIVE", null, 0, 0, null),
                    PreparationRecipeBackupDto("rec-final", restId, finalIngId, "Rec Final", "rec final", "1", "1", "opt-final", "ACTIVE", null, 0, 0, null)
                ),
                preparationRecipeComponents = baseWithRawPurchase.preparationRecipeComponents + listOf(
                    PreparationRecipeComponentBackupDto("rc-inter", "rec-inter", rawIngId, "opt-raw", "5", "5", 0, null, 0, 0),
                    PreparationRecipeComponentBackupDto("rc-f1", "rec-final", interIngId, "opt-inter", "1", "1", 0, null, 0, 0)
                )
            )
        }
        val manifest = createManifest(dto)
        val result = BackupSnapshotIntegrityValidator.validate(dto, manifest)
        if (result.isFailure) {
            val ex = result.exceptionOrNull() as? BackupSnapshotIntegrityException
            throw Exception("Failed with code: ${ex?.code}, msg: ${ex?.message}")
        }
        assertThat(result.isSuccess).isTrue()
    }

    private fun createValidVoidedSnapshot(): BackupSnapshotDto {
        val tVoid = 3000L
        val base = createValidSnapshot()

        val batch = base.productionBatches[0].copy(
            status = "VOIDED",
            voidedAt = tVoid,
            updatedAt = tVoid
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
            InventoryBalanceProjectionBackupDto(restId, ingCompId, areaId, "10", tVoid)
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
