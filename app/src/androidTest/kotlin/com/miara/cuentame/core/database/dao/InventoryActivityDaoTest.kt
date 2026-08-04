package com.miara.cuentame.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.StockCountStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class InventoryActivityDaoTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var movementDao: InventoryMovementDao
    private lateinit var restaurantDao: RestaurantDao
    private lateinit var areaDao: InventoryAreaDao
    private lateinit var unitDao: UnitDao
    private lateinit var ingredientDao: IngredientDao
    private lateinit var purchaseDao: PurchaseDao
    private lateinit var wasteDao: WasteDao
    private lateinit var stockCountDao: StockCountDao
    private lateinit var productionBatchDao: ProductionBatchDao

    private val restId = "rest-1"
    private val area1 = "area-1"
    private val ing1 = "ing-1"
    private val u1 = "u-1"

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        movementDao = db.inventoryMovementDao()
        restaurantDao = db.restaurantDao()
        areaDao = db.inventoryAreaDao()
        unitDao = db.unitDao()
        ingredientDao = db.ingredientDao()
        purchaseDao = db.purchaseDao()
        wasteDao = db.wasteDao()
        stockCountDao = db.stockCountDao()
        productionBatchDao = db.productionBatchDao()

        runBlocking {
            restaurantDao.insert(RestaurantEntity(restId, "Rest", "USD", "en", 0L, 0L, null))
            areaDao.upsert(InventoryAreaEntity(area1, restId, "Area 1", "area 1", 1, true, 0L, 0L, null))
            unitDao.insertSeedUnits(listOf(UnitEntity(u1, "lb", "lb", "Mass", BigDecimal.ONE, true, 1)))
            ingredientDao.insert(IngredientEntity(ing1, restId, "Ingredient 1", "ing 1", null, u1, null, null, null, null, true, 0L, 0L, null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt1", ing1, "lb", "lb", null, BigDecimal.ONE, true, true, true, true, 0L, 0L, null))
        }
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun observeInventoryActivityRows_joinsAllSourceTypes() = runBlocking {
        // 1. Purchase
        purchaseDao.insertReceipt(PurchaseReceiptEntity("p1", restId, null, "INV-123", 1000L, DocumentStatus.POSTED.name, null, null, 0L, 0L, 1000L, null))
        movementDao.insert(createMovement("m1", InventoryMovementType.PURCHASE, SourceDocumentType.PURCHASE_RECEIPT, "p1", 1000L))

        // 2. Waste
        wasteDao.insert(WasteEventEntity("w1", restId, ing1, area1, "opt1", "1", "1", "SPOILED", 1100L, null, null, DocumentStatus.POSTED.name, 0L, 0L, 1100L, null))
        movementDao.insert(createMovement("m2", InventoryMovementType.WASTE, SourceDocumentType.WASTE_EVENT, "w1", 1100L))

        // 3. Stock Count
        stockCountDao.insertCount(StockCountEntity("c1", restId, "Monthly Count", 1200L, 1200L, 1200L, StockCountStatus.COMPLETED.name, null, 0L, 0L, null))
        movementDao.insert(createMovement("m3", InventoryMovementType.COUNT_ADJUSTMENT, SourceDocumentType.STOCK_COUNT, "c1", 1200L))

        // 4. Production
        db.preparationRecipeDao().insert(PreparationRecipeEntity("r1", restId, ing1, "Recipe 1", "recipe 1", BigDecimal.ONE, BigDecimal.ONE, "opt1", "ACTIVE", null, 0L, 0L, null))
        productionBatchDao.insert(ProductionBatchEntity("b1", restId, "r1", "Recipe 1", ing1, "1", "1", "1", "opt1", "1", "1", "1", "1", "opt1", area1, false, "10.0", "10.0", 1300L, DocumentStatus.POSTED.name, null, 0L, 0L, 1300L, null))
        movementDao.insert(createMovement("m4", InventoryMovementType.PRODUCTION_OUTPUT, SourceDocumentType.PRODUCTION_BATCH, "b1", 1300L))

        val rows = movementDao.observeInventoryActivityRows(restId, 0L, 2000L).first()

        assertThat(rows).hasSize(4)
        
        // Ordered DESC by effectiveAt
        assertThat(rows[0].movement.id).isEqualTo("m4")
        assertThat(rows[0].sourceProductionRecipeName).isEqualTo("Recipe 1")
        assertThat(rows[0].sourceProductionResolvedId).isEqualTo("b1")
        
        assertThat(rows[1].movement.id).isEqualTo("m3")
        assertThat(rows[1].sourceStockCountName).isEqualTo("Monthly Count")
        assertThat(rows[1].sourceStockCountResolvedId).isEqualTo("c1")
        
        assertThat(rows[2].movement.id).isEqualTo("m2")
        assertThat(rows[2].sourceWasteReason).isEqualTo("SPOILED")
        assertThat(rows[2].sourceWasteResolvedId).isEqualTo("w1")
        
        assertThat(rows[3].movement.id).isEqualTo("m1")
        assertThat(rows[3].sourcePurchaseInvoiceNumber).isEqualTo("INV-123")
        assertThat(rows[3].sourcePurchaseResolvedId).isEqualTo("p1")
        
        // General enrichments
        rows.forEach {
            assertThat(it.ingredientName).isEqualTo("Ingredient 1")
            assertThat(it.areaName).isEqualTo("Area 1")
            assertThat(it.baseUnitSymbol).isEqualTo("lb")
        }
    }

    @Test
    fun observeInventoryActivityRows_resolvesReversals() = runBlocking {
        // Original
        movementDao.insert(createMovement("m1", InventoryMovementType.PURCHASE, SourceDocumentType.PURCHASE_RECEIPT, "p1", 1000L))
        
        // Reversal
        movementDao.insert(createMovement("m2", InventoryMovementType.REVERSAL, SourceDocumentType.PURCHASE_RECEIPT, "p1", 1100L).copy(reversalOfMovementId = "m1"))

        val rows = movementDao.observeInventoryActivityRows(restId, 0L, 2000L).first()

        assertThat(rows).hasSize(2)
        
        // m2 reverses m1
        val m2Row = rows.find { it.movement.id == "m2" }!!
        assertThat(m2Row.reversalOfMovementType).isEqualTo(InventoryMovementType.PURCHASE)
        assertThat(m2Row.reversalOfMovementEffectiveAt).isEqualTo(java.time.Instant.ofEpochMilli(1000L))
        
        // m1 is reversed by m2
        val m1Row = rows.find { it.movement.id == "m1" }!!
        assertThat(m1Row.reversedByMovementId).isEqualTo("m2")
        assertThat(m1Row.reversedByMovementType).isEqualTo(InventoryMovementType.REVERSAL)
        assertThat(m1Row.reversedByMovementEffectiveAt).isEqualTo(java.time.Instant.ofEpochMilli(1100L))
    }

    private fun createMovement(id: String, type: InventoryMovementType, srcType: SourceDocumentType, srcId: String, effectiveAt: Long) = InventoryMovementEntity(
        id = id,
        restaurantId = restId,
        ingredientId = ing1,
        areaId = area1,
        movementType = type.name,
        quantityBaseSigned = "1.0",
        unitCostBaseSnapshot = "10.0",
        totalValueSnapshot = "10.0",
        effectiveAt = effectiveAt,
        sourceDocumentType = srcType.name,
        sourceDocumentId = srcId,
        sourceOperationId = "op-$id",
        sourceLineId = null,
        reversalOfMovementId = null,
        createdAt = effectiveAt
    )
}
