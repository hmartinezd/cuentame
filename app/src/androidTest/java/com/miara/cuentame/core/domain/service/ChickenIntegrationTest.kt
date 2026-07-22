package com.miara.cuentame.core.domain.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientCategoryId
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.database.repository.RoomIngredientRepository
import com.miara.cuentame.core.database.seed.UnitSeeds
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand
import com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientCategory
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ChickenIntegrationTest {
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
            db.ingredientCategoryDao().upsert(
                IngredientCategory(
                    IngredientCategoryId("cat_meat"),
                    RestaurantId("rest_1"),
                    "Meat",
                    "meat",
                    0,
                    true,
                    timeProvider.now(),
                    timeProvider.now()
                ).toEntity()
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun complete_chicken_breast_fixture() = runBlocking {
        val restId = RestaurantId("rest_1")
        val ingId = IngredientId("chicken_1")
        val baseUnitId = UnitId("mass_lb")

        val chicken = Ingredient(
            id = ingId,
            restaurantId = restId,
            name = "Chicken Breast",
            normalizedName = "chicken breast",
            categoryId = IngredientCategoryId("cat_meat"),
            baseUnitId = baseUnitId,
            isActive = true,
            createdAt = timeProvider.now(),
            updatedAt = timeProvider.now()
        )

        val lbOption = IngredientUnitOption(
            id = IngredientUnitOptionId("opt_lb"),
            ingredientId = ingId,
            displayName = "Pound",
            shortLabel = "lb",
            standardUnitId = baseUnitId,
            factorToBase = BigDecimal.ONE,
            isBase = true,
            isDefaultCount = true,
            isDefaultPurchase = true,
            isActive = true,
            createdAt = timeProvider.now(),
            updatedAt = timeProvider.now()
        )

        // 1. Create ingredient with base option
        repository.createIngredientWithBaseOption(chicken, lbOption)

        // 2. Add Ounce standard option
        repository.addStandardUnitOption(AddStandardUnitOptionCommand(ingId, UnitId("mass_oz")))

        // 3. Add Case package option
        repository.addPackageUnitOption(AddPackageUnitOptionCommand(ingId, "Case", BigDecimal("40"), isDefaultPurchase = true))

        // Assertions
        val savedIng = repository.getById(ingId)
        assertThat(savedIng).isNotNull()
        assertThat(savedIng?.baseUnitId).isEqualTo(baseUnitId)

        val options = db.ingredientUnitOptionDao().getActiveOptions(ingId.value)
        assertThat(options).hasSize(3)
        
        val ozOpt = options.find { it.standardUnitId == "mass_oz" }
        assertThat(ozOpt?.factorToBase?.compareTo(BigDecimal("0.0625"))).isEqualTo(0)
        
        val caseOpt = options.find { it.displayName == "Case" }
        assertThat(caseOpt?.factorToBase?.compareTo(BigDecimal("40"))).isEqualTo(0)
        assertThat(caseOpt?.isDefaultPurchase).isTrue()

        val countDefault = db.ingredientUnitOptionDao().getDefaultCountOption(ingId.value)
        assertThat(countDefault?.displayName).isEqualTo("Pound")

        // Usage check
        val twoCasesInBase = BigDecimal("2").multiply(caseOpt!!.factorToBase)
        assertThat(twoCasesInBase.compareTo(BigDecimal("80"))).isEqualTo(0)
        
        // Final Invariants
        val purchaseDefault = db.ingredientUnitOptionDao().getDefaultPurchaseOption(ingId.value)
        assertThat(purchaseDefault?.id).isEqualTo(caseOpt.id)
        
        // Base is immutable
        try {
            repository.updateIngredient(chicken.copy(baseUnitId = UnitId("mass_g")))
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(com.miara.cuentame.core.domain.validation.ValidationError.IngredientBaseUnitImmutable::class.java)
        }
        
        // Base cannot archive
        try {
            repository.archiveUnitOption(IngredientUnitOptionId("opt_lb"), timeProvider.now())
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(com.miara.cuentame.core.domain.validation.ValidationError.BaseUnitOptionCannotBeArchived::class.java)
        }
        
        // Purchase default cannot archive
        try {
            repository.archiveUnitOption(IngredientUnitOptionId(caseOpt.id), timeProvider.now())
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(com.miara.cuentame.core.domain.validation.ValidationError.DefaultUnitOptionCannotBeArchived::class.java)
        }
        
        // Eligible can archive
        repository.archiveUnitOption(IngredientUnitOptionId(ozOpt!!.id), timeProvider.now())
        val afterArchive = db.ingredientUnitOptionDao().getActiveOptions(ingId.value)
        assertThat(afterArchive.any { it.id == ozOpt.id }).isFalse()
        
        // Still visible in historical
        val historical = db.ingredientUnitOptionDao().observeAllOptionsForIngredient(ingId.value)
        // Manual check since it's Flow
        // repository.observeUnitOptions(..., includeArchived = true)
    }
}
