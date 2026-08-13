package com.miara.cuentame.feature.activity

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.test.TestSeeder
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.first

data class CanonicalInventoryActivityFixture(
    val restaurantId: RestaurantId,
    val componentIngredientId: IngredientId,
    val outputIngredientId: IngredientId,
    val componentUnitOptionId: IngredientUnitOptionId,
    val outputUnitOptionId: IngredientUnitOptionId,
    val areaId: InventoryAreaId,
    val baseUnitId: UnitId,
    val purchaseReceiptId: PurchaseReceiptId,
    val wasteEventId: WasteEventId,
    val stockCountId: StockCountId,
    val preparationRecipeId: PreparationRecipeId,
    val productionBatchId: ProductionBatchId,
    val purchaseMovementId: InventoryMovementId,
    val wasteMovementId: InventoryMovementId,
    val stockCountMovementId: InventoryMovementId,
    val productionConsumptionMovementId: InventoryMovementId,
    val productionOutputMovementId: InventoryMovementId,
    val voidedPurchaseReceiptId: PurchaseReceiptId,
    val originalMovementId: InventoryMovementId,
    val reversalMovementId: InventoryMovementId
)

suspend fun seedCanonicalInventoryActivity(
    ingredientRepository: IngredientRepository,
    purchaseRepository: PurchaseRepository,
    wasteRepository: WasteRepository,
    stockCountRepository: StockCountRepository,
    productionBatchRepository: ProductionBatchRepository,
    preparationRecipeRepository: PreparationRecipeRepository,
    movementDao: InventoryMovementDao
): CanonicalInventoryActivityFixture {
    val restaurantId = RestaurantId(TestSeeder.RESTAURANT_ID)
    val componentIngredientId = IngredientId(TestSeeder.ING_ID)
    val componentUnitOptionId = IngredientUnitOptionId(TestSeeder.OPTION_ID)
    val areaId = InventoryAreaId(TestSeeder.AREA_ID)
    val baseUnitId = UnitId(TestSeeder.UNIT_ID)
    
    val baseTime = Instant.now()
        .minusSeconds(60 * 60)
        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)

    // 1. Setup Output Ingredient (reuse baseline LB unit)
    val preparedChickenId = IngredientId("ing-prepared-chicken-test")
    val preparedChicken = Ingredient(
        id = preparedChickenId,
        restaurantId = restaurantId,
        name = "Prepared Chicken",
        normalizedName = "prepared chicken",
        categoryId = null,
        baseUnitId = baseUnitId,
        defaultAreaId = areaId,
        sku = null,
        notes = null,
        reorderPointBase = null,
        isActive = true,
        createdAt = baseTime.minusSeconds(10 * 3600),
        updatedAt = baseTime.minusSeconds(10 * 3600),
        deletedAt = null
    )
    val preparedChickenBaseOption = IngredientUnitOption(
        id = IngredientUnitOptionId("opt-prepared-chicken-base"),
        ingredientId = preparedChickenId,
        displayName = "lb",
        shortLabel = "lb",
        standardUnitId = baseUnitId,
        factorToBase = BigDecimal.ONE,
        isBase = true,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = baseTime.minusSeconds(10 * 3600),
        updatedAt = baseTime.minusSeconds(10 * 3600),
        deletedAt = null
    )
    ingredientRepository.createIngredientWithBaseOption(preparedChicken, preparedChickenBaseOption, emptyList())
    val outputUnitOptionId = preparedChickenBaseOption.id

    // 2. Posted Purchase: base minus 6 hours
    val purchaseTime = baseTime.minusSeconds(6 * 3600)
    val purchaseId = purchaseRepository.createDraft(CreatePurchaseDraftCommand(restaurantId, null, "INV-001", purchaseTime, null))
    purchaseRepository.saveLine(SavePurchaseLineCommand(purchaseId, null, componentIngredientId, areaId, componentUnitOptionId, BigDecimal("10.0"), BigDecimal("50.0"), null))
    purchaseRepository.post(purchaseId)
    val purchaseMovementId = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, purchaseId.value).first().id.let { InventoryMovementId(it) }

    // 3. Posted Waste: base minus 5 hours
    val wasteTime = baseTime.minusSeconds(5 * 3600)
    val wasteId = wasteRepository.createDraft(CreateWasteDraftCommand(restaurantId, componentIngredientId, areaId, componentUnitOptionId, BigDecimal("1.0"), WasteReason.SPOILED, wasteTime, null, null))
    wasteRepository.post(wasteId)
    val wasteMovementId = movementDao.getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, wasteId.value).first().id.let { InventoryMovementId(it) }

    // 4. Completed Stock Count: base minus 4 hours
    // Initial balance: Purchase (10) - Waste (1) = 9. Counted = 15. Adjustment = +6.
    val countTime = baseTime.minusSeconds(4 * 3600)
    val countId = stockCountRepository.start(StartStockCountCommand(restaurantId, "Monthly Count", countTime, listOf(areaId), null))
    val countDetails = stockCountRepository.observeCount(countId).first()!!
    val countAreaId = countDetails.areas.first().area.id
    stockCountRepository.saveLine(SaveStockCountLineCommand(countId, countAreaId, null, componentIngredientId, componentUnitOptionId, BigDecimal("15.0"), null))
    stockCountRepository.completeArea(countId, countAreaId)
    stockCountRepository.completeCount(countId)
    val stockCountMovementId = movementDao.getBySourceDocument(SourceDocumentType.STOCK_COUNT.name, countId.value).first().id.let { InventoryMovementId(it) }

    // 5. Posted Production: base minus 3 hours
    val prodTime = baseTime.minusSeconds(3 * 3600)
    val recipeId = preparationRecipeRepository.createDraft(CreatePreparationRecipeCommand(restaurantId, preparedChickenId, "Prepared Chicken Recipe", BigDecimal.ONE, outputUnitOptionId, null))
    preparationRecipeRepository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, componentIngredientId, componentUnitOptionId, BigDecimal.ONE, 0, null))
    preparationRecipeRepository.activate(recipeId)
    
    val batchId = productionBatchRepository.createDraft(CreateProductionBatchDraftCommand(restaurantId, recipeId, BigDecimal.ONE, areaId, BigDecimal.ONE, outputUnitOptionId, prodTime, null))
    val batch = productionBatchRepository.getBatch(batchId)!!
    val batchComponentId = batch.components.first().id
    productionBatchRepository.updateComponent(UpdateProductionBatchComponentCommand(batchId, batchComponentId, areaId, BigDecimal.ONE, componentUnitOptionId, null))
    productionBatchRepository.post(batchId)
    
    val prodMovements = movementDao.getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
    val productionConsumptionMovementId = prodMovements.first { it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name }.id.let { InventoryMovementId(it) }
    val productionOutputMovementId = prodMovements.first { it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name }.id.let { InventoryMovementId(it) }

    // 6. Voided Purchase with Reversal:
    // Original: base minus 2 hours
    // Void/Reversal: base minus 1 hour (reversal generated at void time)
    val originalPurchaseTime = baseTime.minusSeconds(2 * 3600)
    val voidedPId = purchaseRepository.createDraft(CreatePurchaseDraftCommand(restaurantId, null, "VOID-ME", originalPurchaseTime, null))
    purchaseRepository.saveLine(SavePurchaseLineCommand(voidedPId, null, componentIngredientId, areaId, componentUnitOptionId, BigDecimal("5.0"), BigDecimal("25.0"), null))
    purchaseRepository.post(voidedPId)
    
    purchaseRepository.void(voidedPId)
    
    val voidMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, voidedPId.value)
    val originalMovementId = voidMovements.first { it.reversalOfMovementId == null && it.movementType == InventoryMovementType.PURCHASE.name }.id.let { InventoryMovementId(it) }
    val reversalMovementId = voidMovements.first { it.reversalOfMovementId != null }.id.let { InventoryMovementId(it) }

    return CanonicalInventoryActivityFixture(
        restaurantId = restaurantId,
        componentIngredientId = componentIngredientId,
        outputIngredientId = preparedChickenId,
        componentUnitOptionId = componentUnitOptionId,
        outputUnitOptionId = outputUnitOptionId,
        areaId = areaId,
        baseUnitId = baseUnitId,
        purchaseReceiptId = purchaseId,
        wasteEventId = wasteId,
        stockCountId = countId,
        preparationRecipeId = recipeId,
        productionBatchId = batchId,
        purchaseMovementId = purchaseMovementId,
        wasteMovementId = wasteMovementId,
        stockCountMovementId = stockCountMovementId,
        productionConsumptionMovementId = productionConsumptionMovementId,
        productionOutputMovementId = productionOutputMovementId,
        voidedPurchaseReceiptId = voidedPId,
        originalMovementId = originalMovementId,
        reversalMovementId = reversalMovementId
    )
}
