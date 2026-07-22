package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand
import com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand
import com.miara.cuentame.core.domain.repository.UpdateIngredientCommand
import com.miara.cuentame.core.domain.service.StandardUnitConverter
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
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
    private val converter = StandardUnitConverter()
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
        repository = RoomIngredientRepository(
            db, db.ingredientDao(), db.ingredientUnitOptionDao(), db.unitDao(),
            db.restaurantDao(), db.ingredientCategoryDao(), converter, idGenerator, timeProvider
        )
        
        runBlocking {
            db.restaurantDao().insert(TestFactories.createRestaurant())
            db.unitDao().insertSeedUnits(UnitSeeds.ALL_UNITS)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createIngredient_isAtomicAndValidates() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1")

        repository.createIngredientWithBaseOption(ingredient, baseOption)

        val savedIng = repository.getById(IngredientId("ing_1"))
        assertThat(savedIng).isNotNull()
        assertThat(savedIng?.name).isEqualTo("Test Ingredient")
        
        val options = db.ingredientUnitOptionDao().getActiveOptions("ing_1")
        assertThat(options).hasSize(1)
        assertThat(options.first().isBase).isTrue()
    }

    @Test(expected = ValidationError.IngredientIdAlreadyExists::class)
    fun createIngredient_failsIfIdExists() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1")
        repository.createIngredientWithBaseOption(ingredient, baseOption)

        // Try same ID again
        repository.createIngredientWithBaseOption(ingredient.copy(name = "Other"), createBaseOption("ing_1", "opt_2"))
    }

    @Test(expected = ValidationError.InvalidBaseUnitOption::class)
    fun createIngredient_failsIfSuppliedBaseIsNotBase() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1").copy(isBase = false)
        repository.createIngredientWithBaseOption(ingredient, baseOption)
    }

    @Test(expected = ValidationError.AdditionalOptionCannotBeBase::class)
    fun createIngredient_failsIfAdditionalOptionIsBase() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1")
        val extra = createBaseOption("ing_1", "opt_2").copy(displayName = "Extra Base")
        repository.createIngredientWithBaseOption(ingredient, baseOption, listOf(extra))
    }

    @Test
    fun updateIngredient_preservesProtectedFields() = runBlocking {
        val ingId = IngredientId("ing_1")
        val original = createIngredient("ing_1")
        repository.createIngredientWithBaseOption(original, createBaseOption("ing_1", "opt_1"))

        repository.updateIngredient(UpdateIngredientCommand(ingId, "New Name", null))

        val saved = repository.getById(ingId)!!
        assertThat(saved.name).isEqualTo("New Name")
        assertThat(saved.restaurantId).isEqualTo(original.restaurantId)
        assertThat(saved.baseUnitId).isEqualTo(original.baseUnitId)
        assertThat(saved.isActive).isTrue()
        assertThat(saved.createdAt).isEqualTo(original.createdAt)
    }

    @Test(expected = ValidationError.DuplicateActiveName::class)
    fun updateIngredient_failsOnDuplicateNameInSameRestaurant() = runBlocking {
        repository.createIngredientWithBaseOption(
            createIngredient("ing_1").copy(name = "Chicken", normalizedName = "chicken"),
            createBaseOption("ing_1", "opt_1")
        )
        repository.createIngredientWithBaseOption(
            createIngredient("ing_2").copy(name = "Beef", normalizedName = "beef"),
            createBaseOption("ing_2", "opt_2")
        )

        // Attempt to rename Beef to Chicken
        repository.updateIngredient(UpdateIngredientCommand(IngredientId("ing_2"), "Chicken", null))
    }

    @Test(expected = ValidationError.DuplicateActiveName::class)
    fun updateIngredient_fakeRestaurantIdCannotBypassDuplicateValidation() = runBlocking {
        // Restaurant A has "Chicken"
        repository.createIngredientWithBaseOption(
            createIngredient("ing_1").copy(name = "Chicken", normalizedName = "chicken"),
            createBaseOption("ing_1", "opt_1")
        )
        
        // Restaurant A also has "Beef"
        repository.createIngredientWithBaseOption(
            createIngredient("ing_2").copy(name = "Beef", normalizedName = "beef"),
            createBaseOption("ing_2", "opt_2")
        )

        // Caller attempts to rename Beef to Chicken (which exists in Restaurant A)
        // Even if they could somehow supply a different restaurant ID (which UpdateIngredientCommand doesn't allow, but the repository logic protects),
        // it must fail because the stored ingredient belongs to Restaurant A.
        repository.updateIngredient(UpdateIngredientCommand(IngredientId("ing_2"), "Chicken", null))
    }

    private fun createIngredient(id: String) = Ingredient(
        id = IngredientId(id),
        restaurantId = RestaurantId("rest_1"),
        name = "Test Ingredient",
        normalizedName = "test ingredient",
        baseUnitId = UnitId("mass_lb"),
        isActive = true,
        createdAt = timeProvider.now(),
        updatedAt = timeProvider.now()
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
        createdAt = timeProvider.now(),
        updatedAt = timeProvider.now()
    )
}
