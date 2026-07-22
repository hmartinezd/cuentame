package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryMovementEntity
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class IngredientRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomIngredientRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomIngredientRepository(db, db.ingredientDao(), db.ingredientUnitOptionDao(), db.unitDao())
        
        runBlocking {
            db.restaurantDao().insert(TestFactories.createRestaurant())
            db.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
            db.inventoryAreaDao().upsert(TestFactories.createArea())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createIngredientWithBaseOption_isAtomic() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1")

        repository.createIngredientWithBaseOption(ingredient, baseOption)

        val savedIng = repository.getById(IngredientId("ing_1"))
        assertThat(savedIng).isNotNull()
        
        val options = db.ingredientUnitOptionDao().getBaseOption("ing_1")
        assertThat(options).isNotNull()
    }

    @Test(expected = ValidationError.IngredientBaseUnitImmutable::class)
    fun updateBaseUnit_alwaysFailsInMilestone4() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1")
        repository.createIngredientWithBaseOption(ingredient, baseOption)

        val updated = ingredient.copy(baseUnitId = UnitId("mass_g"))
        repository.updateIngredient(updated)
    }

    @Test(expected = ValidationError.BaseUnitOptionCannotBeArchived::class)
    fun archiveBaseOption_fails() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1")
        repository.createIngredientWithBaseOption(ingredient, baseOption)

        repository.archiveUnitOption(IngredientUnitOptionId("opt_1"), Instant.now())
    }

    @Test
    fun archiveIngredient_isSoftDelete() = runBlocking {
        val ingredient = createIngredient("ing_1")
        repository.createIngredientWithBaseOption(ingredient, createBaseOption("ing_1", "opt_1"))

        repository.archive(IngredientId("ing_1"), Instant.now())
        
        val savedIng = repository.getById(IngredientId("ing_1"))
        assertThat(savedIng?.isActive).isFalse()
    }

    private fun createIngredient(id: String) = Ingredient(
        id = IngredientId(id),
        restaurantId = RestaurantId("rest_1"),
        name = "Test Ingredient",
        normalizedName = "test ingredient",
        baseUnitId = UnitId("mass_lb"),
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun createBaseOption(ingId: String, optId: String) = IngredientUnitOption(
        id = IngredientUnitOptionId(optId),
        ingredientId = IngredientId(ingId),
        displayName = "Pound",
        shortLabel = "lb",
        standardUnitId = UnitId("mass_lb"),
        factorToBase = BigDecimal.ONE,
        isBase = true,
        isDefaultCount = true,
        isDefaultPurchase = true,
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
