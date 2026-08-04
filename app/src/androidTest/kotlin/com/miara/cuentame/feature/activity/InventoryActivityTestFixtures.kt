package com.miara.cuentame.feature.activity

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.dao.InventoryMovementDao
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.*
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class CanonicalInventoryActivityFixture(
    val restaurantId: RestaurantId,
    val componentIngredientId: IngredientId,
    val outputIngredientId: IngredientId,
    val areaId: InventoryAreaId,
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
    restaurantRepository: RestaurantRepository,
    ingredientRepository: IngredientRepository,
    areaRepository: InventoryAreaRepository,
    purchaseRepository: PurchaseRepository,
    wasteRepository: WasteRepository,
    stockCountRepository: StockCountRepository,
    productionBatchRepository: ProductionBatchRepository,
    preparationRecipeRepository: PreparationRecipeRepository,
    activityRepository: InventoryActivityRepository,
    movementDao: InventoryMovementDao
): CanonicalInventoryActivityFixture {
    val restaurant = restaurantRepository.getRestaurant()!!
    
    val baseTime = OffsetDateTime.of(2026, 8, 4, 12, 0, 0, 0, ZoneOffset.UTC).toInstant()

    // 1. Setup Ingredients and Area
    val area = areaRepository.observeAllAreas().first().first()
    
    val chickenId = IngredientId(UUID.randomUUID().toString())
    val chicken = Ingredient(
        id = chickenId,
        restaurantId = restaurant.id,
        name = "Chicken",
        normalizedName = "chicken",
        categoryId = null,
        baseUnitId = UnitId("kg"),
        defaultAreaId = area.id,
        sku = null,
        notes = null,
        reorderPointBase = null,
        isActive = true,
        createdAt = baseTime.minusSeconds(10 * 3600),
        updatedAt = baseTime.minusSeconds(10 * 3600),
        deletedAt = null
    )
    val chickenBaseOption = IngredientUnitOption(
        id = IngredientUnitOptionId(UUID.randomUUID().toString()),
        ingredientId = chickenId,
        displayName = "kg",
        shortLabel = "kg",
        standardUnitId = UnitId("kg"),
        factorToBase = BigDecimal.ONE,
        isBase = true,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = baseTime.minusSeconds(10 * 3600),
        updatedAt = baseTime.minusSeconds(10 * 3600),
        deletedAt = null
    )
    ingredientRepository.createIngredientWithBaseOption(chicken, chickenBaseOption, emptyList())
    
    val preparedChickenId = IngredientId(UUID.randomUUID().toString())
    val preparedChicken = Ingredient(
        id = preparedChickenId,
        restaurantId = restaurant.id,
        name = "Prepared Chicken",
        normalizedName = "prepared chicken",
        categoryId = null,
        baseUnitId = UnitId("kg"),
        defaultAreaId = area.id,
        sku = null,
        notes = null,
        reorderPointBase = null,
        isActive = true,
        createdAt = baseTime.minusSeconds(10 * 3600),
        updatedAt = baseTime.minusSeconds(10 * 3600),
        deletedAt = null
    )
    val preparedChickenBaseOption = IngredientUnitOption(
        id = IngredientUnitOptionId(UUID.randomUUID().toString()),
        ingredientId = preparedChickenId,
        displayName = "kg",
        shortLabel = "kg",
        standardUnitId = UnitId("kg"),
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

    val chickenUnitOptionId = chickenBaseOption.id
    val preparedChickenUnitOptionId = preparedChickenBaseOption.id

    // 2. Posted Purchase: base minus 6 hours
    val purchaseTime = baseTime.minusSeconds(6 * 3600)
    val purchaseId = purchaseRepository.createDraft(CreatePurchaseDraftCommand(restaurant.id, null, "INV-001", purchaseTime, null))
    purchaseRepository.saveLine(SavePurchaseLineCommand(purchaseId, null, chickenId, area.id, chickenUnitOptionId, BigDecimal("10.0"), BigDecimal("50.0"), null))
    purchaseRepository.post(purchaseId)
    val purchaseMovementId = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, purchaseId.value).first().id.let { InventoryMovementId(it) }

    // 3. Posted Waste: base minus 5 hours
    val wasteTime = baseTime.minusSeconds(5 * 3600)
    val wasteId = wasteRepository.createDraft(CreateWasteDraftCommand(restaurant.id, chickenId, area.id, chickenUnitOptionId, BigDecimal("1.0"), WasteReason.SPOILED, wasteTime, null, null))
    wasteRepository.post(wasteId)
    val wasteMovementId = movementDao.getBySourceDocument(SourceDocumentType.WASTE_EVENT.name, wasteId.value).first().id.let { InventoryMovementId(it) }

    // 4. Completed Stock Count: base minus 4 hours
    val countTime = baseTime.minusSeconds(4 * 3600)
    val countId = stockCountRepository.start(StartStockCountCommand(restaurant.id, "Monthly Count", countTime, listOf(area.id), null))
    val countDetails = stockCountRepository.observeCount(countId).first()!!
    val countAreaId = countDetails.areas.first().area.id
    stockCountRepository.saveLine(SaveStockCountLineCommand(countId, countAreaId, null, chickenId, chickenUnitOptionId, BigDecimal("15.0"), null))
    stockCountRepository.completeCount(countId)
    val stockCountMovementId = movementDao.getBySourceDocument(SourceDocumentType.STOCK_COUNT.name, countId.value).first().id.let { InventoryMovementId(it) }

    // 5. Posted Production: base minus 3 hours
    val prodTime = baseTime.minusSeconds(3 * 3600)
    val recipeId = preparationRecipeRepository.createDraft(CreatePreparationRecipeCommand(restaurant.id, preparedChickenId, "Prepared Chicken Recipe", BigDecimal.ONE, preparedChickenUnitOptionId, null))
    preparationRecipeRepository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, chickenId, chickenUnitOptionId, BigDecimal.ONE, 0, null))
    preparationRecipeRepository.activate(recipeId)
    
    val batchId = productionBatchRepository.createDraft(CreateProductionBatchDraftCommand(restaurant.id, recipeId, BigDecimal.ONE, area.id, BigDecimal.ONE, preparedChickenUnitOptionId, prodTime, null))
    val batch = productionBatchRepository.getBatch(batchId)!!
    val componentId = batch.components.first().id
    productionBatchRepository.updateComponent(UpdateProductionBatchComponentCommand(batchId, componentId, area.id, BigDecimal.ONE, chickenUnitOptionId, null))
    productionBatchRepository.post(batchId)
    
    val prodMovements = movementDao.getBySourceDocument(SourceDocumentType.PRODUCTION_BATCH.name, batchId.value)
    val productionConsumptionMovementId = prodMovements.first { it.movementType == InventoryMovementType.PRODUCTION_CONSUMPTION.name }.id.let { InventoryMovementId(it) }
    val productionOutputMovementId = prodMovements.first { it.movementType == InventoryMovementType.PRODUCTION_OUTPUT.name }.id.let { InventoryMovementId(it) }

    // 6. Voided Purchase with Reversal:
    // Original: base minus 2 hours
    // Void/Reversal: base minus 1 hour
    val originalPurchaseTime = baseTime.minusSeconds(2 * 3600)
    val voidedPId = purchaseRepository.createDraft(CreatePurchaseDraftCommand(restaurant.id, null, "VOID-ME", originalPurchaseTime, null))
    purchaseRepository.saveLine(SavePurchaseLineCommand(voidedPId, null, chickenId, area.id, chickenUnitOptionId, BigDecimal("5.0"), BigDecimal("25.0"), null))
    purchaseRepository.post(voidedPId)
    
    purchaseRepository.void(voidedPId)
    
    val voidMovements = movementDao.getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, voidedPId.value)
    val originalMovementId = voidMovements.first { it.reversalOfMovementId == null }.id.let { InventoryMovementId(it) }
    val reversalMovementId = voidMovements.first { it.reversalOfMovementId != null }.id.let { InventoryMovementId(it) }

    return CanonicalInventoryActivityFixture(
        restaurantId = restaurant.id,
        componentIngredientId = chickenId,
        outputIngredientId = preparedChickenId,
        areaId = area.id,
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
