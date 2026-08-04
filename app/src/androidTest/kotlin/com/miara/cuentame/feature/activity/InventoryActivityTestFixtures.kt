package com.miara.cuentame.feature.activity

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.*
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Instant

data class CanonicalInventoryActivityFixture(
    val restaurantId: RestaurantId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val purchaseReceiptId: PurchaseReceiptId,
    val wasteEventId: WasteEventId,
    val stockCountId: StockCountId,
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
    restaurantRepository: RestaurantRepository,
    ingredientRepository: IngredientRepository,
    areaRepository: InventoryAreaRepository,
    purchaseRepository: PurchaseRepository,
    wasteRepository: WasteRepository,
    stockCountRepository: StockCountRepository,
    productionBatchRepository: ProductionBatchRepository,
    preparationRecipeRepository: PreparationRecipeRepository,
    activityRepository: InventoryActivityRepository
): CanonicalInventoryActivityFixture {
    val restaurant = restaurantRepository.getRestaurant()!!
    
    // 1. Setup Ingredient and Area
    val area = areaRepository.observeAllAreas().first().first()
    val allIngredients = ingredientRepository.observeIngredients(restaurant.id, true).first()
    val ingredientId = allIngredients.first().id
    val options = ingredientRepository.getUnitOptions(ingredientId)
    val unitOptionId = options.first().id

    // Helper to get movement ID
    suspend fun getRecentMovementId(docId: String): InventoryMovementId {
        kotlinx.coroutines.delay(300) // Wait for Flow to emit
        return activityRepository.observeActivity(
            InventoryActivityQuery(restaurant.id, Instant.EPOCH, Instant.now().plusSeconds(3600))
        ).first().first { it.movement.sourceDocumentId == docId }.movement.id
    }

    // 2. Posted Purchase
    val purchaseId = purchaseRepository.createDraft(CreatePurchaseDraftCommand(restaurant.id, null, "INV-001", Instant.now(), null))
    purchaseRepository.saveLine(SavePurchaseLineCommand(purchaseId, null, ingredientId, area.id, unitOptionId, BigDecimal("10.0"), BigDecimal("50.0"), null))
    purchaseRepository.post(purchaseId)
    val purchaseMovementId = getRecentMovementId(purchaseId.value)

    // 3. Posted Waste
    val wasteId = wasteRepository.createDraft(CreateWasteDraftCommand(restaurant.id, ingredientId, area.id, unitOptionId, BigDecimal("1.0"), WasteReason.SPOILED, Instant.now(), null, null))
    wasteRepository.post(wasteId)
    val wasteMovementId = getRecentMovementId(wasteId.value)

    // 4. Completed Stock Count
    val countId = stockCountRepository.start(StartStockCountCommand(restaurant.id, "Monthly Count", Instant.now(), listOf(area.id), null))
    val countDetails = stockCountRepository.observeCount(countId).first()!!
    val countAreaId = countDetails.areas.first().area.id
    stockCountRepository.saveLine(SaveStockCountLineCommand(countId, countAreaId, null, ingredientId, unitOptionId, BigDecimal("15.0"), null))
    stockCountRepository.completeCount(countId)
    val stockCountMovementId = getRecentMovementId(countId.value)

    // 5. Posted Production
    val recipeId = preparationRecipeRepository.createDraft(CreatePreparationRecipeCommand(restaurant.id, ingredientId, "Grilled Chicken", BigDecimal.ONE, unitOptionId, null))
    preparationRecipeRepository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, ingredientId, unitOptionId, BigDecimal.ONE, 0, null))
    preparationRecipeRepository.activate(recipeId)
    
    val batchId = productionBatchRepository.createDraft(CreateProductionBatchDraftCommand(restaurant.id, recipeId, BigDecimal.ONE, area.id, BigDecimal.ONE, unitOptionId, Instant.now(), null))
    val batch = productionBatchRepository.getBatch(batchId)!!
    val componentId = batch.components.first().id
    productionBatchRepository.updateComponent(UpdateProductionBatchComponentCommand(batchId, componentId, area.id, BigDecimal.ONE, unitOptionId, null))
    productionBatchRepository.post(batchId)
    
    kotlinx.coroutines.delay(300)
    val prodMovements = activityRepository.observeActivity(InventoryActivityQuery(restaurant.id, Instant.EPOCH, Instant.now().plusSeconds(3600))).first().filter { it.movement.sourceDocumentId == batchId.value }
    val productionConsumptionMovementId = prodMovements.first { it.movement.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION }.movement.id
    val productionOutputMovementId = prodMovements.first { it.movement.movementType == InventoryMovementType.PRODUCTION_OUTPUT }.movement.id

    // 6. Voided Purchase with Reversal
    val voidedPId = purchaseRepository.createDraft(CreatePurchaseDraftCommand(restaurant.id, null, "VOID-ME", Instant.now(), null))
    purchaseRepository.saveLine(SavePurchaseLineCommand(voidedPId, null, ingredientId, area.id, unitOptionId, BigDecimal("5.0"), BigDecimal("25.0"), null))
    purchaseRepository.post(voidedPId)
    purchaseRepository.void(voidedPId)
    
    kotlinx.coroutines.delay(300)
    val voidMovements = activityRepository.observeActivity(InventoryActivityQuery(restaurant.id, Instant.EPOCH, Instant.now().plusSeconds(3600))).first().filter { it.movement.sourceDocumentId == voidedPId.value }
    val originalMovementId = voidMovements.first { it.movement.reversalOfMovementId == null }.movement.id
    val reversalMovementId = voidMovements.first { it.movement.reversalOfMovementId != null }.movement.id

    return CanonicalInventoryActivityFixture(
        restaurantId = restaurant.id,
        ingredientId = ingredientId,
        areaId = area.id,
        purchaseReceiptId = purchaseId,
        wasteEventId = wasteId,
        stockCountId = countId,
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
