package com.miara.cuentame.test

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import java.math.BigDecimal
import java.time.Instant

object TestSeeder {
    const val RESTAURANT_ID = "restaurant-test-1"
    const val AREA_ID = "area-test-1"
    const val UNIT_ID = "unit-test-1"
    const val ING_ID = "ing-test-1"
    const val OPTION_ID = "opt-test-1"
    const val SUPPLIER_ID = "supplier-test-1"

    suspend fun seedBaseline(db: RestaurantInventoryDatabase) {
        db.restaurantDao().insert(
            RestaurantEntity(
                id = RESTAURANT_ID,
                name = "Test Restaurant",
                currencyCode = "USD",
                localeTag = "en-US",
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )

        db.supplierDao().insert(
            SupplierEntity(
                id = SUPPLIER_ID,
                restaurantId = RESTAURANT_ID,
                name = "Test Supplier",
                normalizedName = "test supplier",
                phone = null,
                email = null,
                notes = null,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
        
        db.unitDao().insertSeedUnits(listOf(
            UnitEntity(UNIT_ID, "Pound", "lb", "MASS", BigDecimal.ONE, true, 0)
        ))
        
        db.inventoryAreaDao().upsert(
            InventoryAreaEntity(AREA_ID, RESTAURANT_ID, "Storage", "storage", 0, true, 0L, 0L, null)
        )
        
        db.ingredientDao().insert(
            IngredientEntity(ING_ID, RESTAURANT_ID, "Chicken", "chicken", null, UNIT_ID, AREA_ID, null, null, null, true, 0L, 0L, null)
        )
        
        db.ingredientUnitOptionDao().insert(
            IngredientUnitOptionEntity(OPTION_ID, ING_ID, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null)
        )
    }

    suspend fun seedPostedPurchase(
        db: RestaurantInventoryDatabase,
        repo: PurchaseRepository,
        restaurantId: RestaurantId,
        ingredientId: IngredientId,
        areaId: InventoryAreaId,
        unitOptionId: IngredientUnitOptionId,
        quantityEntered: BigDecimal,
        unitCostBase: BigDecimal,
        effectiveAt: Instant
    ): PostedPurchaseFixture {
        val receiptId = repo.createDraft(
            CreatePurchaseDraftCommand(
                restaurantId = restaurantId,
                supplierId = null,
                invoiceNumber = "FIXTURE-INV",
                purchaseDate = effectiveAt,
                notes = "Fixture seeded purchase"
            )
        )

        val totalAmount = quantityEntered.multiply(unitCostBase)
        val lineId = repo.saveLine(
            SavePurchaseLineCommand(
                receiptId = receiptId,
                lineId = null,
                ingredientId = ingredientId,
                areaId = areaId,
                ingredientUnitOptionId = unitOptionId,
                quantityEntered = quantityEntered,
                lineTotal = totalAmount,
                notes = null
            )
        )

        repo.post(receiptId)

        val movement = db.inventoryMovementDao().getBySourceDocument(
            SourceDocumentType.PURCHASE_RECEIPT.name,
            receiptId.value
        ).first { it.sourceLineId == lineId.value }

        return PostedPurchaseFixture(
            receiptId = receiptId,
            lineId = lineId,
            movementId = InventoryMovementId(movement.id),
            ingredientId = ingredientId,
            areaId = areaId,
            optionId = unitOptionId
        )
    }
}

data class PostedPurchaseFixture(
    val receiptId: PurchaseReceiptId,
    val lineId: PurchaseLineId,
    val movementId: InventoryMovementId,
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val optionId: IngredientUnitOptionId
)
