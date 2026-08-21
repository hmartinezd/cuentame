package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.BackupFormatV1Contract
import com.venkoi.cuentame.core.backup.model.BackupSnapshotDto
import com.venkoi.cuentame.core.backup.platform.BackupManifestContractValidator
import com.venkoi.cuentame.core.model.backup.BackupManifest
import com.venkoi.cuentame.core.model.backup.TableMetadata
import com.venkoi.cuentame.core.model.inventory.InventoryMovementOperationIds
import org.junit.Test
import java.math.BigDecimal

class FixtureParityTest {

    @Test
    fun populatedSchema4Snapshot_isSelfConsistent() {
        val snapshot = BackupTestFixtures.createPopulatedSchema4Snapshot()
        val manifest = createManifestForSnapshot(
            snapshot = snapshot,
            schemaVersion = 4,
            restaurantName = "Test Rest",
            localeTag = "en-US",
            currencyCode = "USD"
        )

        // 1. Manifest consistency
        val consistencyResult = BackupManifestContractValidator.validateSnapshotConsistency(manifest, snapshot)
        assertThat(consistencyResult).isNull()

        // 2. Snapshot integrity
        val integrityResult = BackupSnapshotIntegrityValidator.validate(snapshot, manifest)
        assertThat(integrityResult.isSuccess).isTrue()

        // 3. Explicit verification (Requirement 12)
        assertThat(snapshot.preparationRecipes.find { it.id == "rec1" }).isNotNull()
        val batch = snapshot.productionBatches.find { it.id == "pb1" }!!
        assertThat(batch.recipeId).isEqualTo("rec1")
        
        val component = snapshot.productionBatchComponents.find { it.id == "pbc1" }!!
        assertThat(component.sourceRecipeComponentIdSnapshot).isEqualTo("rc1")

        val purchase = snapshot.inventoryMovements.find { it.movementType == "PURCHASE" }!!
        val consumption = snapshot.inventoryMovements.find { it.movementType == "PRODUCTION_CONSUMPTION" }!!
        val output = snapshot.inventoryMovements.find { it.movementType == "PRODUCTION_OUTPUT" }!!

        assertThat(purchase).isNotNull()
        assertThat(consumption).isNotNull()
        assertThat(output).isNotNull()

        assertThat(BigDecimal(component.unitCostBaseSnapshot!!).compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(BigDecimal(batch.totalComponentCostSnapshot!!).compareTo(BigDecimal("50"))).isEqualTo(0)
        assertThat(BigDecimal(batch.actualOutputQuantityBase).compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(BigDecimal(batch.outputUnitCostBaseSnapshot!!).compareTo(BigDecimal("5"))).isEqualTo(0)

        val balI2 = snapshot.inventoryBalanceProjections.find { it.ingredientId == "i2" }!!
        assertThat(balI2.quantityBase).isEqualTo("5")
        
        val balI1 = snapshot.inventoryBalanceProjections.find { it.ingredientId == "i1" }!!
        assertThat(balI1.quantityBase).isEqualTo("10")

        val costI2 = snapshot.ingredientCostProjections.find { it.ingredientId == "i2" }!!
        assertThat(costI2.averageUnitCostBase).isEqualTo("10")

        val costI1 = snapshot.ingredientCostProjections.find { it.ingredientId == "i1" }!!
        assertThat(costI1.averageUnitCostBase).isEqualTo("5")
    }

    private fun createManifestForSnapshot(
        snapshot: BackupSnapshotDto,
        schemaVersion: Int,
        restaurantName: String,
        localeTag: String,
        currencyCode: String
    ): BackupManifest {
        val restaurantId = snapshot.restaurants.firstOrNull()?.id ?: "r1"
        
        val tableMetadata = mutableMapOf<String, TableMetadata>()
        val expectedTables = BackupFormatV1Contract.expectedTablesForSchema(schemaVersion)
        val derivedTables = BackupFormatV1Contract.DERIVED_TABLES
        
        val counts = mapOf(
            "restaurants" to snapshot.restaurants.size,
            "inventory_areas" to snapshot.inventoryAreas.size,
            "ingredient_categories" to snapshot.ingredientCategories.size,
            "units" to snapshot.units.size,
            "ingredients" to snapshot.ingredients.size,
            "ingredient_unit_options" to snapshot.ingredientUnitOptions.size,
            "suppliers" to snapshot.suppliers.size,
            "purchase_receipts" to snapshot.purchaseReceipts.size,
            "purchase_lines" to snapshot.purchaseLines.size,
            "stock_counts" to snapshot.stockCounts.size,
            "stock_count_areas" to snapshot.stockCountAreas.size,
            "stock_count_lines" to snapshot.stockCountLines.size,
            "waste_events" to snapshot.wasteEvents.size,
            "inventory_movements" to snapshot.inventoryMovements.size,
            "inventory_balance_projections" to snapshot.inventoryBalanceProjections.size,
            "ingredient_cost_projections" to snapshot.ingredientCostProjections.size,
            "preparation_recipes" to snapshot.preparationRecipes.size,
            "preparation_recipe_components" to snapshot.preparationRecipeComponents.size,
            "production_batches" to snapshot.productionBatches.size,
            "production_batch_components" to snapshot.productionBatchComponents.size
            ,"menu_recipes" to snapshot.menuRecipes.size,"menu_recipe_components" to snapshot.menuRecipeComponents.size,
            "menus" to snapshot.menus.size,"menu_categories" to snapshot.menuCategories.size,"menu_placements" to snapshot.menuPlacements.size,
            "menu_publications" to snapshot.menuPublications.size,"menu_publication_categories" to snapshot.menuPublicationCategories.size,"menu_publication_items" to snapshot.menuPublicationItems.size,"menu_publication_item_components" to snapshot.menuPublicationItemComponents.size
        )

        for (table in expectedTables) {
            val count = counts[table] ?: 0
            tableMetadata[table] = TableMetadata(
                entryCount = count,
                isDerived = table in derivedTables
            )
        }

        return BackupManifest(
            backupFormatVersion = 1,
            createdAtUtc = "2026-01-01T12:00:00Z",
            applicationId = "com.venkoi.cuentame",
            appVersionName = "1.0",
            appVersionCode = 1L,
            databaseSchemaVersion = schemaVersion,
            restaurantId = restaurantId,
            restaurantName = restaurantName,
            localeTag = localeTag,
            currencyCode = currencyCode,
            tableMetadata = tableMetadata,
            attachments = emptyList(),
            includedSections = listOf("data", "preferences", "attachments"),
            checksumAlgorithm = "SHA-256"
        )
    }

    @Test
    fun purchasePostOperationId_matchesContract() {
        val receiptId = "r1"
        val lineId = "l1"
        
        val expected = InventoryMovementOperationIds.purchasePost(receiptId, lineId)
        
        val snapshot = BackupTestFixtures.addPostedPurchase(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            receiptId = receiptId,
            lineId = lineId,
            movementId = "m1",
            ingredientId = "i1",
            areaId = "a1",
            optionId = "o1",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.first()
        assertThat(move.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("purchase-post:r1:l1")
    }

    @Test
    fun wastePostOperationId_matchesContract() {
        val eventId = "w1"
        
        val expected = InventoryMovementOperationIds.wastePost(eventId)
        
        val snapshot = BackupTestFixtures.addPostedWaste(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            eventId = eventId,
            movementId = "m1",
            ingredientId = "i1",
            areaId = "a1",
            optionId = "o1",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.first()
        assertThat(move.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("waste-post:w1")
    }

    @Test
    fun productionOutputOperationId_matchesContract() {
        val batchId = "pb1"
        val expected = InventoryMovementOperationIds.productionOutput(batchId)
        
        val snapshot = BackupTestFixtures.addPostedProduction(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            batchId = batchId,
            recipeId = "rec1",
            recipeComponentId = "rc1",
            componentId = "pbc1",
            componentIngredientId = "i-comp",
            componentAreaId = "a-out",
            componentOptionId = "o-comp",
            componentQuantityBase = BigDecimal.ONE,
            componentUnitCostBase = BigDecimal.ONE,
            consumptionMovementId = "m-consume",
            outputMovementId = "m-out",
            outputIngredientId = "i-out",
            outputAreaId = "a-out",
            outputOptionId = "o-out",
            quantityBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )

        
        val move = snapshot.inventoryMovements.find { it.movementType == "PRODUCTION_OUTPUT" }!!
        assertThat(move.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("production-post:pb1:output")
    }

    @Test
    fun productionConsumptionOperationId_matchesContract() {
        val batchId = "pb1"
        val componentId = "pbc1"
        val expected = InventoryMovementOperationIds.productionConsumption(batchId, componentId)
        
        val snapshot = BackupTestFixtures.addProductionConsumption(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            batchId = batchId,
            componentId = componentId,
            movementId = "m-consume",
            ingredientId = "i-comp",
            areaId = "a-comp",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val move = snapshot.inventoryMovements.first()
        assertThat(move.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("production-post:pb1:consume:pbc1")
    }

    @Test
    fun reversalOperationId_matchesContract() {
        val originalId = "m1"
        val expected = InventoryMovementOperationIds.reversal(originalId)
        
        val snapshot = BackupTestFixtures.addPostedPurchase(
            snapshot = BackupTestFixtures.createEmptySnapshotDto(),
            receiptId = "r1",
            lineId = "l1",
            movementId = originalId,
            ingredientId = "i1",
            areaId = "a1",
            optionId = "o1",
            quantityBase = BigDecimal.ONE,
            unitCostBase = BigDecimal.ONE,
            effectiveAt = 1000L,
            createdAt = 1000L
        )
        
        val snapshotWithReversal = BackupTestFixtures.addReversal(
            snapshot = snapshot,
            originalMovementId = originalId,
            reversalMovementId = "m2",
            effectiveAt = 2000L,
            createdAt = 2000L
        )
        
        val reversalMove = snapshotWithReversal.inventoryMovements.last()
        assertThat(reversalMove.sourceOperationId).isEqualTo(expected)
        assertThat(expected).isEqualTo("reversal:m1")
    }
}
