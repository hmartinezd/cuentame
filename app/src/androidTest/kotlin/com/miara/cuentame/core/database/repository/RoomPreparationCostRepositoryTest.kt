package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.domain.service.PreparationCostCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class RoomPreparationCostRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private val restaurantId = RestaurantId("restaurant-a")
    private lateinit var prices: FakePrices
    private lateinit var repository: RoomPreparationCostRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), RestaurantInventoryDatabase::class.java
        ).allowMainThreadQueries().build()
        db.restaurantDao().insert(RestaurantEntity(restaurantId.value, "A", "USD", "en-US", 0, 0, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("lb", "Pound", "lb", "MASS", BigDecimal.ONE, true, 0)))
        prices = FakePrices()
        repository = RoomPreparationCostRepository(
            db.preparationRecipeDao(), db.ingredientDao(), db.ingredientUnitOptionDao(),
            db.ingredientCostProjectionDao(), db.unitDao(), db.productionBatchDao(), db.restaurantDao(),
            prices, PreparationCostCalculator()
        )
        ingredient("flour", "Flour")
        ingredient("dough", "Dough")
        db.preparationRecipeDao().insert(PreparationRecipeEntity(
            "recipe", restaurantId.value, "dough", "Dough", "dough", BigDecimal.TEN,
            BigDecimal.TEN, "flour-option", "DRAFT", null, 0, 0, null
        ))
        db.preparationRecipeDao().upsertComponent(PreparationRecipeComponentEntity(
            "component", "recipe", "flour", "flour-option", BigDecimal.TEN,
            BigDecimal.TEN, 0, null, 0, 0
        ))
    }

    @After fun tearDown() = db.close()

    @Test fun rawProjectionAndPersistedBaseQuantityAreReactiveAndRestaurantScoped() = runBlocking {
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restaurantId.value, "flour", "1", 0))
        repository.observeRecipeCost(PreparationRecipeId("recipe")).test {
            val initial = awaitNonNull()
            assertDecimal(initial.totalBatchCost, "10")
            assertThat(prices.requestedRestaurant).isEqualTo(restaurantId)

            // Mutable option metadata must not rewrite persisted recipe economics.
            db.ingredientUnitOptionDao().upsert(option("flour", BigDecimal("99")))
            cancelAndIgnoreRemainingEvents()
        }

        repository.observeRecipeCost(PreparationRecipeId("recipe")).test {
            assertDecimal(awaitNonNull().totalBatchCost, "10")
            db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restaurantId.value, "flour", "1.5", 1))
            assertDecimal(awaitNonNull().totalBatchCost, "15")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun malformedProjectionIsInvalidWhileZeroIsAvailable() = runBlocking {
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restaurantId.value, "flour", "not-a-decimal", 0))
        repository.observeRecipeCost(PreparationRecipeId("recipe")).test {
            val invalid = awaitNonNull()
            assertThat(invalid.components.single().missingReason)
                .isEqualTo(com.miara.cuentame.core.model.ingredient.PreparationCostMissingReason.INGREDIENT_COST_INVALID)
            db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restaurantId.value, "flour", "0.00", 1))
            val zero = awaitNonNull()
            assertThat(zero.status).isEqualTo(com.miara.cuentame.core.model.ingredient.PreparationCostStatus.FULLY_COSTED)
            assertDecimal(zero.totalBatchCost, "0")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun priceFailureDoesNotTakeDownCurrentCost() = runBlocking {
        prices.fail = true
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restaurantId.value, "flour", "1", 0))
        repository.observeRecipeCost(PreparationRecipeId("recipe")).test {
            val cost = awaitNonNull()
            assertDecimal(cost.totalBatchCost, "10")
            assertThat(cost.priceImpact.isComplete).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun missingProjectionStillUsesKnownVendorPriceImpact() = runBlocking {
        prices.comparisons = mapOf(
            IngredientId("flour") to VendorPriceComparison(
                null, null, BigDecimal("0.50"), null, PriceDirection.INCREASED, emptySet()
            )
        )
        repository.observeRecipeCost(PreparationRecipeId("recipe")).test {
            val cost = awaitNonNull()
            assertThat(cost.status).isEqualTo(com.miara.cuentame.core.model.ingredient.PreparationCostStatus.UNCOSTED)
            assertThat(cost.totalBatchCost).isNull()
            assertThat(cost.components.single().missingReason)
                .isEqualTo(com.miara.cuentame.core.model.ingredient.PreparationCostMissingReason.INGREDIENT_COST_MISSING)
            assertDecimal(cost.components.single().vendorPriceImpact, "5")
            assertThat(cost.priceImpact.coveredLeafCount).isEqualTo(1)
            assertThat(cost.priceImpact.totalLeafCount).isEqualTo(1)
            assertThat(cost.priceImpact.isComplete).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ingredient(id: String, name: String) {
        db.ingredientDao().insert(IngredientEntity(id, restaurantId.value, name, name.lowercase(), null, "lb", null, null, null, null, true, 0, 0, null))
        db.ingredientUnitOptionDao().insert(option(id, BigDecimal.ONE))
    }

    private fun option(ingredientId: String, factor: BigDecimal) = IngredientUnitOptionEntity(
        "$ingredientId-option", ingredientId, "lb", "lb", "lb", factor,
        true, true, true, true, 0, 0, null
    )

    private suspend fun app.cash.turbine.ReceiveTurbine<com.miara.cuentame.core.model.ingredient.PreparationRecipeCost?>.awaitNonNull(): com.miara.cuentame.core.model.ingredient.PreparationRecipeCost {
        while (true) awaitItem()?.let { return it }
    }

    private fun assertDecimal(actual: BigDecimal?, expected: String) =
        assertThat(actual?.compareTo(BigDecimal(expected))).isEqualTo(0)

    private class FakePrices : PriceIntelligenceRepository {
        var requestedRestaurant: RestaurantId? = null
        var fail = false
        var comparisons: Map<IngredientId, VendorPriceComparison> = emptyMap()
        override fun observeIngredientPriceHistory(ingredientId: IngredientId): Flow<IngredientPriceHistory> = flow { error("unused") }
        override fun observeLargePriceIncreases(): Flow<List<PriceIncreaseAlert>> = flowOf(emptyList())
        override fun observePriceComparisons(restaurantId: RestaurantId, ingredientIds: Set<IngredientId>): Flow<Map<IngredientId, VendorPriceComparison>> {
            requestedRestaurant = restaurantId
            return if (fail) flow { throw IllegalStateException("price source") } else flowOf(comparisons)
        }
    }
}
