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

    @Test(expected = ValidationError.MissingBaseUnitOption::class)
    fun createIngredient_requiresOneBaseOption() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1").copy(isBase = false)

        repository.createIngredientWithBaseOption(ingredient, baseOption)
    }

    @Test(expected = ValidationError.InvalidDefaultUnitOption::class)
    fun createIngredient_requiresDefaults() = runBlocking {
        val ingredient = createIngredient("ing_1")
        val baseOption = createBaseOption("ing_1", "opt_1").copy(isDefaultCount = false)

        repository.createIngredientWithBaseOption(ingredient, baseOption)
    }

    @Test
    fun addStandardUnitOption_derivesCorrectFactor() = runBlocking {
        val ingId = IngredientId("ing_1")
        repository.createIngredientWithBaseOption(createIngredient("ing_1"), createBaseOption("ing_1", "opt_1"))

        // Pound is base. Add Ounce.
        repository.addStandardUnitOption(AddStandardUnitOptionCommand(ingId, UnitId("mass_oz")))

        val options = db.ingredientUnitOptionDao().getActiveOptions("ing_1")
        val ozOption = options.find { it.standardUnitId == "mass_oz" }
        assertThat(ozOption).isNotNull()
        // 1 oz = 0.0625 lb (approximately, depends on seed)
        // 1 oz = 28.349523125 g, 1 lb = 453.59237 g
        // 28.349523125 / 453.59237 = 0.0625
        assertThat(ozOption?.factorToBase?.compareTo(BigDecimal("0.0625"))).isEqualTo(0)
    }

    @Test(expected = ValidationError.IngredientBaseUnitImmutable::class)
    fun updateIngredient_preventsBaseUnitChange() = runBlocking {
        val ingredient = createIngredient("ing_1")
        repository.createIngredientWithBaseOption(ingredient, createBaseOption("ing_1", "opt_1"))

        val updated = ingredient.copy(baseUnitId = UnitId("mass_g"))
        repository.updateIngredient(updated)
    }

    @Test(expected = ValidationError.BaseUnitOptionCannotBeArchived::class)
    fun archiveUnitOption_preventsBaseArchive() = runBlocking {
        repository.createIngredientWithBaseOption(createIngredient("ing_1"), createBaseOption("ing_1", "opt_1"))
        repository.archiveUnitOption(IngredientUnitOptionId("opt_1"), timeProvider.now())
    }

    @Test
    fun setDefaultCountOption_isAtomic() = runBlocking {
        val ingId = IngredientId("ing_1")
        repository.createIngredientWithBaseOption(createIngredient("ing_1"), createBaseOption("ing_1", "opt_1"))
        
        repository.addPackageUnitOption(AddPackageUnitOptionCommand(ingId, "Case", BigDecimal("40")))
        val options = db.ingredientUnitOptionDao().getActiveOptions("ing_1")
        val caseOptionId = IngredientUnitOptionId(options.find { it.displayName == "Case" }!!.id)

        repository.setDefaultCountOption(ingId, caseOptionId)

        val countDefault = db.ingredientUnitOptionDao().getDefaultCountOption("ing_1")
        assertThat(countDefault?.id).isEqualTo(caseOptionId.value)
        
        val baseOption = db.ingredientUnitOptionDao().getBaseOption("ing_1")
        assertThat(baseOption?.isDefaultCount).isFalse()
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
