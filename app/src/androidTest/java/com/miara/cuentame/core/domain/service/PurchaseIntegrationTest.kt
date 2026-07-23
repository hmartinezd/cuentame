package com.miara.cuentame.core.domain.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.database.repository.PurchaseMovementHistoryValidator
import com.miara.cuentame.core.database.repository.PurchaseReferenceValidator
import com.miara.cuentame.core.database.repository.RoomInventoryProjectionRebuilder
import com.miara.cuentame.core.database.repository.RoomPurchaseRepository
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PurchaseIntegrationTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomPurchaseRepository
    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-01-01T00:00:00Z")
    }
    private var idCounter = 0
    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "id_${++idCounter}"
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        val projectionRebuilder = RoomInventoryProjectionRebuilder(
            db, db.ingredientDao(), db.inventoryMovementDao(), db.inventoryProjectionDao(),
            db.ingredientCostProjectionDao(), WeightedAverageCostCalculator(), timeProvider
        )

        val referenceValidator = PurchaseReferenceValidator(
            db.purchaseDao(), db.supplierDao(), db.ingredientDao(), db.inventoryAreaDao(), db.ingredientUnitOptionDao()
        )

        repository = RoomPurchaseRepository(
            db, db.purchaseDao(), db.supplierDao(), db.ingredientDao(),
            db.ingredientUnitOptionDao(), db.inventoryAreaDao(), db.inventoryMovementDao(),
            db.restaurantDao(), projectionRebuilder, referenceValidator, PurchaseLineCalculator(),
            PurchaseMovementHistoryValidator(), idGenerator, timeProvider
        )
        
        runBlocking {
            db.restaurantDao().insert(TestFactories.createRestaurant())
            db.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            db.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_1"), RestaurantId("rest_1"), "Dry Storage", "dry storage", 0, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
            val ingId = IngredientId("chicken_1")
            db.ingredientDao().insert(
                Ingredient(ingId, RestaurantId("rest_1"), "Chicken Breast", "chicken breast", null, UnitId("mass_lb"), null, null, null, null, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
            db.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_lb"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
            db.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_case"), ingId, "Case", "case", null, BigDecimal("40"), false, false, true, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun complete_purchase_lifecycle_fixture() {
        runBlocking {
            val restId = RestaurantId("rest_1")
            val ingId = IngredientId("chicken_1")
            val areaId = InventoryAreaId("area_1")

            // 1. Initial Receipt A: 2 cases @ $160
            val receiptAId = repository.createDraft(CreatePurchaseDraftCommand(restId, null, "INV-A", timeProvider.now(), null))
            repository.saveLine(SavePurchaseLineCommand(
                receiptId = receiptAId,
                lineId = null,
                ingredientId = ingId,
                areaId = areaId,
                ingredientUnitOptionId = IngredientUnitOptionId("opt_case"),
                quantityEntered = BigDecimal("2"),
                lineTotal = BigDecimal("160"),
                notes = null
            ))

            repository.post(receiptAId)

            // Assert A
            val balanceA = db.inventoryProjectionDao().getBalance(ingId.value, areaId.value)
            assertThat(BigDecimal(balanceA!!.quantityBase).compareTo(BigDecimal("80"))).isEqualTo(0)
            
            val costA = db.ingredientCostProjectionDao().observeCostForIngredient(ingId.value).first()
            assertThat(BigDecimal(costA!!.averageUnitCostBase).compareTo(BigDecimal("2"))).isEqualTo(0)

            // 2. Second Receipt B: 20 lb @ $60 ($3/lb)
            val receiptBId = repository.createDraft(CreatePurchaseDraftCommand(restId, null, "INV-B", timeProvider.now(), null))
            repository.saveLine(SavePurchaseLineCommand(
                receiptId = receiptBId,
                lineId = null,
                ingredientId = ingId,
                areaId = areaId,
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("20"),
                lineTotal = BigDecimal("60"),
                notes = null
            ))

            repository.post(receiptBId)

            // Assert B: ((80 * 2) + (20 * 3)) / 100 = 220 / 100 = 2.20
            val balanceB = db.inventoryProjectionDao().getBalance(ingId.value, areaId.value)
            assertThat(BigDecimal(balanceB!!.quantityBase).compareTo(BigDecimal("100"))).isEqualTo(0)

            val costB = db.ingredientCostProjectionDao().observeCostForIngredient(ingId.value).first()
            assertThat(BigDecimal(costB!!.averageUnitCostBase).compareTo(BigDecimal("2.2"))).isEqualTo(0)

            // 3. Void Receipt B
            repository.void(receiptBId)

            // Assert after Void B
            val balanceAfterVoidB = db.inventoryProjectionDao().getBalance(ingId.value, areaId.value)
            assertThat(BigDecimal(balanceAfterVoidB!!.quantityBase).compareTo(BigDecimal("80"))).isEqualTo(0)

            val costAfterVoidB = db.ingredientCostProjectionDao().observeCostForIngredient(ingId.value).first()
            assertThat(BigDecimal(costAfterVoidB!!.averageUnitCostBase).compareTo(BigDecimal("2"))).isEqualTo(0)

            val movements = db.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receiptBId.value)
            assertThat(movements.any { it.movementType == InventoryMovementType.REVERSAL.name }).isTrue()
        }
    }
}
