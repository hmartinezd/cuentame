package com.miara.cuentame.core.database.repository

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
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PurchaseRepositoryTest {
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
            val ingId = IngredientId("ing_1")
            db.ingredientDao().insert(
                Ingredient(ingId, RestaurantId("rest_1"), "Chicken", "chicken", null, UnitId("mass_lb"), null, null, null, null, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
            db.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_lb"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createDraft_succeeds() {
        runBlocking {
            val command = CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, "INV-123", timeProvider.now(), "Notes")
            val id = repository.createDraft(command)

            val saved = repository.getReceipt(id)
            assertThat(saved).isNotNull()
            assertThat(saved?.invoiceNumber).isEqualTo("INV-123")
            assertThat(saved?.status).isEqualTo(DocumentStatus.DRAFT)
        }
    }

    @Test
    fun saveLine_calculatesBaseValues() {
        runBlocking {
            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            
            val command = SavePurchaseLineCommand(
                receiptId = receiptId,
                lineId = null,
                ingredientId = IngredientId("ing_1"),
                areaId = InventoryAreaId("area_1"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("10"),
                lineTotal = BigDecimal("20"),
                notes = null
            )
            repository.saveLine(command)

            val details = repository.observePurchase(receiptId).first()
            val line = details?.lines?.first()?.line
            assertThat(line).isNotNull()
            assertThat(line?.quantityBase?.compareTo(BigDecimal("10"))).isEqualTo(0)
            assertThat(line?.unitCostBase?.compareTo(BigDecimal("2"))).isEqualTo(0)
        }
    }

    @Test
    fun post_failsOnIngredientOwnershipMismatch() {
        runBlocking {
            val rest2 = RestaurantId("rest_2")
            db.restaurantDao().insert(Restaurant(rest2, "Rest 2", "USD", "en-US", timeProvider.now(), timeProvider.now()).toEntity())
            val ing2 = IngredientId("ing_2")
            db.ingredientDao().insert(
                Ingredient(ing2, rest2, "Wrong Chick", "wrong chicken", null, UnitId("mass_lb"), null, null, null, null, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
            db.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_2"), ing2, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, timeProvider.now(), timeProvider.now()).toEntity()
            )

            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            
            db.purchaseDao().insertLine(com.miara.cuentame.core.database.entity.PurchaseLineEntity(
                "bad_line", receiptId.value, ing2.value, "area_1", "opt_2", "1", "1", "1", "1", null, 0, 0
            ))

            assertThrows(ValidationError.IngredientOwnershipMismatch::class.java) {
                runBlocking {
                    repository.post(receiptId)
                }
            }
        }
    }

    @Test
    fun post_failsOnArchivedIngredient() {
        runBlocking {
            val ingId = IngredientId("ing_archived")
            db.ingredientDao().insert(
                Ingredient(ingId, RestaurantId("rest_1"), "Old Chicken", "old chicken", null, UnitId("mass_lb"), null, null, null, null, false, timeProvider.now(), timeProvider.now()).toEntity()
            )
            db.ingredientUnitOptionDao().insert(
                IngredientUnitOption(IngredientUnitOptionId("opt_archived"), ingId, "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, timeProvider.now(), timeProvider.now()).toEntity()
            )

            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            
            db.purchaseDao().insertLine(com.miara.cuentame.core.database.entity.PurchaseLineEntity(
                "archived_line", receiptId.value, ingId.value, "area_1", "opt_archived", "1", "1", "1", "1", null, 0, 0
            ))

            assertThrows(ValidationError.ArchivedReference::class.java) {
                runBlocking {
                    repository.post(receiptId)
                }
            }
        }
    }

    @Test
    fun post_failsOnMalformedHistory() {
        runBlocking {
            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            repository.saveLine(SavePurchaseLineCommand(
                receiptId = receiptId,
                lineId = null,
                ingredientId = IngredientId("ing_1"),
                areaId = InventoryAreaId("area_1"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("10"),
                lineTotal = BigDecimal("20"),
                notes = null
            ))

            // Inject a movement manually while still DRAFT
            db.inventoryMovementDao().insert(com.miara.cuentame.core.database.entity.InventoryMovementEntity(
                "mov_1", "rest_1", "ing_1", "area_1", com.miara.cuentame.core.model.inventory.InventoryMovementType.PURCHASE.name,
                "10", "2", "20", timeProvider.now().toEpochMilli(), com.miara.cuentame.core.model.inventory.SourceDocumentType.PURCHASE_RECEIPT.name,
                receiptId.value, "purchase-post:${receiptId.value}:some_line", "some_line", null, 0
            ))

            assertThrows(ValidationError.MalformedPurchaseMovementHistory::class.java) {
                runBlocking {
                    repository.post(receiptId)
                }
            }
        }
    }

    @Test
    fun post_retry_isIdempotent() {
        runBlocking {
            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            repository.saveLine(SavePurchaseLineCommand(
                receiptId = receiptId,
                lineId = null,
                ingredientId = IngredientId("ing_1"),
                areaId = InventoryAreaId("area_1"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("10"),
                lineTotal = BigDecimal("20"),
                notes = null
            ))

            // First post
            repository.post(receiptId)
            val movementsAfterFirst = db.inventoryMovementDao().getBySourceDocument(com.miara.cuentame.core.model.inventory.SourceDocumentType.PURCHASE_RECEIPT.name, receiptId.value)
            assertThat(movementsAfterFirst).hasSize(1)

            // Second post (retry)
            repository.post(receiptId)
            val movementsAfterSecond = db.inventoryMovementDao().getBySourceDocument(com.miara.cuentame.core.model.inventory.SourceDocumentType.PURCHASE_RECEIPT.name, receiptId.value)
            assertThat(movementsAfterSecond).hasSize(1) // No duplicate
        }
    }

    @Test
    fun void_failsIfAlreadyVoided_ButStillReturnsSuccessIdempotently() {
        runBlocking {
            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            repository.saveLine(SavePurchaseLineCommand(
                receiptId = receiptId,
                lineId = null,
                ingredientId = IngredientId("ing_1"),
                areaId = InventoryAreaId("area_1"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("10"),
                lineTotal = BigDecimal("20"),
                notes = null
            ))

            repository.post(receiptId)
            repository.void(receiptId)
            
            val movementsAfterFirstVoid = db.inventoryMovementDao().getBySourceDocument(com.miara.cuentame.core.model.inventory.SourceDocumentType.PURCHASE_RECEIPT.name, receiptId.value)
            assertThat(movementsAfterFirstVoid).hasSize(2) // 1 PURCHASE + 1 REVERSAL

            // Second void (retry)
            repository.void(receiptId)
            val movementsAfterSecondVoid = db.inventoryMovementDao().getBySourceDocument(com.miara.cuentame.core.model.inventory.SourceDocumentType.PURCHASE_RECEIPT.name, receiptId.value)
            assertThat(movementsAfterSecondVoid).hasSize(2) // No duplicate reversal
        }
    }

    @Test
    fun postPurchase_createsMovementsAndUpdatesProjections() {
        runBlocking {
            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            repository.saveLine(SavePurchaseLineCommand(
                receiptId = receiptId,
                lineId = null,
                ingredientId = IngredientId("ing_1"),
                areaId = InventoryAreaId("area_1"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("10"),
                lineTotal = BigDecimal("20"),
                notes = null
            ))

            repository.post(receiptId)

            val saved = repository.getReceipt(receiptId)
            assertThat(saved?.status).isEqualTo(DocumentStatus.POSTED)
            assertThat(saved?.postedAt).isNotNull()

            val balance = db.inventoryProjectionDao().getBalance("ing_1", "area_1")
            assertThat(BigDecimal(balance?.quantityBase ?: "0").compareTo(BigDecimal("10"))).isEqualTo(0)

            val cost = db.ingredientCostProjectionDao().observeCostForIngredient("ing_1").first()
            assertThat(BigDecimal(cost?.averageUnitCostBase ?: "0").compareTo(BigDecimal("2"))).isEqualTo(0)
        }
    }

    @Test
    fun voidPurchase_createsReversalsAndRebuildsProjections() {
        runBlocking {
            val receiptId = repository.createDraft(CreatePurchaseDraftCommand(RestaurantId("rest_1"), null, null, timeProvider.now(), null))
            repository.saveLine(SavePurchaseLineCommand(
                receiptId = receiptId,
                lineId = null,
                ingredientId = IngredientId("ing_1"),
                areaId = InventoryAreaId("area_1"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("10"),
                lineTotal = BigDecimal("20"),
                notes = null
            ))

            repository.post(receiptId)
            repository.void(receiptId)

            val saved = repository.getReceipt(receiptId)
            assertThat(saved?.status).isEqualTo(DocumentStatus.VOIDED)
            assertThat(saved?.voidedAt).isNotNull()

            val balance = db.inventoryProjectionDao().getBalance("ing_1", "area_1")
            assertThat(BigDecimal(balance?.quantityBase ?: "0").compareTo(BigDecimal.ZERO)).isEqualTo(0)
        }
    }
}
