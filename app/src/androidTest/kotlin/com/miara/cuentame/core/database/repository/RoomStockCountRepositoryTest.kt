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
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.miara.cuentame.core.domain.repository.StartStockCountCommand
import com.miara.cuentame.core.domain.service.WeightedAverageCostCalculator
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.StockCountStatus
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
class RoomStockCountRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomStockCountRepository
    private val timeProvider = object : TimeProvider {
        var now: Instant = Instant.parse("2024-01-01T12:00:00Z")
        override fun now(): Instant = now
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

        val snapshotService = RoomInventorySnapshotService(
            db.inventoryMovementDao(),
            WeightedAverageCostCalculator(),
            InventoryMovementValidator()
        )

        repository = RoomStockCountRepository(
            db, db.stockCountDao(), db.inventoryMovementDao(), db.ingredientDao(),
            db.inventoryAreaDao(), db.ingredientUnitOptionDao(), db.restaurantDao(),
            snapshotService, StockCountMovementHistoryValidator(), projectionRebuilder,
            idGenerator, timeProvider
        )
        
        runBlocking {
            db.restaurantDao().insert(TestFactories.createRestaurant())
            db.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            
            db.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_dry"), RestaurantId("rest_1"), "Dry Storage", "dry storage", 0, true, timeProvider.now(), timeProvider.now()).toEntity()
            )
            db.inventoryAreaDao().upsert(
                InventoryArea(InventoryAreaId("area_kitchen"), RestaurantId("rest_1"), "Main Kitchen", "main kitchen", 1, true, timeProvider.now(), timeProvider.now()).toEntity()
            )

            val ingId = IngredientId("ing_chicken")
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
    fun countIntegrationFixture() {
        runBlocking {
            val purchaseTime = Instant.parse("2024-01-01T10:00:00Z")
            db.inventoryMovementDao().insert(com.miara.cuentame.core.database.entity.InventoryMovementEntity(
                id = "mov_purchase_a",
                restaurantId = "rest_1",
                ingredientId = "ing_chicken",
                areaId = "area_dry",
                movementType = InventoryMovementType.PURCHASE.name,
                quantityBaseSigned = "80",
                unitCostBaseSnapshot = "2",
                totalValueSnapshot = "160",
                effectiveAt = purchaseTime.toEpochMilli(),
                sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT.name,
                sourceDocumentId = "purchase_a",
                sourceOperationId = "op_purchase_a",
                sourceLineId = "line_a",
                reversalOfMovementId = null,
                createdAt = purchaseTime.toEpochMilli()
            ))
            
            RoomInventoryProjectionRebuilder(
                db, db.ingredientDao(), db.inventoryMovementDao(), db.inventoryProjectionDao(),
                db.ingredientCostProjectionDao(), WeightedAverageCostCalculator(), timeProvider
            ).rebuildForIngredient(IngredientId("ing_chicken"))

            val initialBalanceDry = db.inventoryProjectionDao().getBalance("ing_chicken", "area_dry")
            assertThat(BigDecimal(initialBalanceDry?.quantityBase).compareTo(BigDecimal("80"))).isEqualTo(0)
            
            val initialCost = db.ingredientCostProjectionDao().getCost("ing_chicken")
            assertThat(BigDecimal(initialCost?.averageUnitCostBase).compareTo(BigDecimal("2"))).isEqualTo(0)

            val countId = repository.start(StartStockCountCommand(
                restaurantId = RestaurantId("rest_1"),
                name = "Monthly Count",
                effectiveAt = Instant.parse("2024-01-01T11:00:00Z"),
                areaIds = listOf(InventoryAreaId("area_dry"), InventoryAreaId("area_kitchen")),
                notes = null
            ))

            val details = repository.observeCount(countId).first()
            val dryArea = details?.areas?.find { it.area.areaId == InventoryAreaId("area_dry") }!!
            val kitchenArea = details.areas.find { it.area.areaId == InventoryAreaId("area_kitchen") }!!

            repository.saveLine(SaveStockCountLineCommand(
                countId = countId,
                countAreaId = dryArea.area.id,
                lineId = null,
                ingredientId = IngredientId("ing_chicken"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("75"),
                notes = null
            ))
            repository.completeArea(countId, dryArea.area.id)

            repository.saveLine(SaveStockCountLineCommand(
                countId = countId,
                countAreaId = kitchenArea.area.id,
                lineId = null,
                ingredientId = IngredientId("ing_chicken"),
                ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                quantityEntered = BigDecimal("10"),
                notes = null
            ))
            repository.completeArea(countId, kitchenArea.area.id)

            repository.completeCount(countId)

            val finalDetails = repository.observeCount(countId).first()!!
            assertThat(finalDetails.count.status).isEqualTo(StockCountStatus.COMPLETED)

            val dryLine = finalDetails.areas.find { it.area.areaId == InventoryAreaId("area_dry") }!!.lines.first()
            assertThat(dryLine.expectedQuantityBaseSnapshot?.compareTo(BigDecimal("80"))).isEqualTo(0)
            assertThat(dryLine.adjustmentQuantityBase?.compareTo(BigDecimal("-5"))).isEqualTo(0)

            val kitchenLine = finalDetails.areas.find { it.area.areaId == InventoryAreaId("area_kitchen") }!!.lines.first()
            assertThat(kitchenLine.expectedQuantityBaseSnapshot).isNull()
            assertThat(kitchenLine.adjustmentQuantityBase?.compareTo(BigDecimal("10"))).isEqualTo(0)

            val balanceDry = db.inventoryProjectionDao().getBalance("ing_chicken", "area_dry")
            assertThat(BigDecimal(balanceDry?.quantityBase).compareTo(BigDecimal("75"))).isEqualTo(0)
            
            val balanceKitchen = db.inventoryProjectionDao().getBalance("ing_chicken", "area_kitchen")
            assertThat(BigDecimal(balanceKitchen?.quantityBase).compareTo(BigDecimal("10"))).isEqualTo(0)

            val cost = db.ingredientCostProjectionDao().getCost("ing_chicken")
            assertThat(BigDecimal(cost?.averageUnitCostBase).compareTo(BigDecimal("2"))).isEqualTo(0)

            timeProvider.now = Instant.parse("2024-01-01T13:00:00Z")
            repository.voidCount(countId)

            val voidedDetails = repository.observeCount(countId).first()!!
            assertThat(voidedDetails.count.status).isEqualTo(StockCountStatus.VOIDED)

            val balanceDryAfterVoid = db.inventoryProjectionDao().getBalance("ing_chicken", "area_dry")
            assertThat(BigDecimal(balanceDryAfterVoid?.quantityBase).compareTo(BigDecimal("80"))).isEqualTo(0)

            val balanceKitchenAfterVoid = db.inventoryProjectionDao().getBalance("ing_chicken", "area_kitchen")
            val balanceKitchenQty = balanceKitchenAfterVoid?.quantityBase?.let { BigDecimal(it) } ?: BigDecimal.ZERO
            assertThat(balanceKitchenQty.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        }
    }

    @Test
    fun startCount_validations() {
        runBlocking {
            // Blank name
            assertThrows(ValidationError.InvalidName::class.java) {
                runBlocking {
                    repository.start(StartStockCountCommand(
                        RestaurantId("rest_1"), "", timeProvider.now(), listOf(InventoryAreaId("area_dry")), null
                    ))
                }
            }

            // Future effective timestamp
            assertThrows(ValidationError.InvalidCountEffectiveTime::class.java) {
                runBlocking {
                    repository.start(StartStockCountCommand(
                        RestaurantId("rest_1"), "Count", timeProvider.now().plusSeconds(3600), listOf(InventoryAreaId("area_dry")), null
                    ))
                }
            }

            // No areas
            assertThrows(ValidationError.StockCountHasNoAreas::class.java) {
                runBlocking {
                    repository.start(StartStockCountCommand(
                        RestaurantId("rest_1"), "Count", timeProvider.now(), emptyList(), null
                    ))
                }
            }
        }
    }

    @Test
    fun saveLine_validations() {
        runBlocking {
            val countId = repository.start(StartStockCountCommand(
                RestaurantId("rest_1"), "Count", timeProvider.now(), listOf(InventoryAreaId("area_dry")), null
            ))
            val details = repository.observeCount(countId).first()!!
            val areaId = details.areas.first().area.id

            // Negative quantity
            assertThrows(ValidationError.InvalidCountQuantity::class.java) {
                runBlocking {
                    repository.saveLine(SaveStockCountLineCommand(
                        countId, areaId, null, IngredientId("ing_chicken"), IngredientUnitOptionId("opt_lb"), BigDecimal("-1"), null
                    ))
                }
            }

            // Duplicate ingredient in one area
            repository.saveLine(SaveStockCountLineCommand(
                countId, areaId, null, IngredientId("ing_chicken"), IngredientUnitOptionId("opt_lb"), BigDecimal("10"), null
            ))
            assertThrows(ValidationError.DuplicateIngredientInCountArea::class.java) {
                runBlocking {
                    repository.saveLine(SaveStockCountLineCommand(
                        countId, areaId, null, IngredientId("ing_chicken"), IngredientUnitOptionId("opt_lb"), BigDecimal("20"), null
                    ))
                }
            }
        }
    }

    @Test
    fun startCount_failsOnOverlappingDraftArea() {
        runBlocking {
            repository.start(StartStockCountCommand(
                restaurantId = RestaurantId("rest_1"),
                name = "Count 1",
                effectiveAt = timeProvider.now(),
                areaIds = listOf(InventoryAreaId("area_dry")),
                notes = null
            ))

            assertThrows(ValidationError.StockCountAreaAlreadyInDraft::class.java) {
                runBlocking {
                    repository.start(StartStockCountCommand(
                        restaurantId = RestaurantId("rest_1"),
                        name = "Count 2",
                        effectiveAt = timeProvider.now(),
                        areaIds = listOf(InventoryAreaId("area_dry")),
                        notes = null
                    ))
                }
            }
        }
    }

    @Test
    fun saveLine_failsOnCompletedArea() {
        runBlocking {
            val countId = repository.start(StartStockCountCommand(
                restaurantId = RestaurantId("rest_1"),
                name = "Count 1",
                effectiveAt = timeProvider.now(),
                areaIds = listOf(InventoryAreaId("area_dry")),
                notes = null
            ))
            val areaId = repository.observeCount(countId).first()?.areas?.first()?.area?.id!!

            repository.completeArea(countId, areaId)

            assertThrows(ValidationError.StockCountAlreadyCompleted::class.java) {
                runBlocking {
                    repository.saveLine(SaveStockCountLineCommand(
                        countId = countId,
                        countAreaId = areaId,
                        lineId = null,
                        ingredientId = IngredientId("ing_chicken"),
                        ingredientUnitOptionId = IngredientUnitOptionId("opt_lb"),
                        quantityEntered = BigDecimal("10"),
                        notes = null
                    ))
                }
            }
        }
    }
}
