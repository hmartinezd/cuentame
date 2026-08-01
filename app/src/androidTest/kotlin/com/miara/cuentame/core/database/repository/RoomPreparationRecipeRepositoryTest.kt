package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.PreparationRecipeDao
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.domain.repository.CreatePreparationRecipeCommand
import com.miara.cuentame.core.domain.repository.SavePreparationRecipeComponentCommand
import com.miara.cuentame.core.domain.repository.UpdatePreparationRecipeCommand
import com.miara.cuentame.core.domain.validation.PreparationRecipeValidationFailure
import com.miara.cuentame.core.domain.validation.PreparationRecipeValidator
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomPreparationRecipeRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomPreparationRecipeRepository
    private lateinit var validator: PreparationRecipeValidator
    private val restId = RestaurantId("rest-1")
    
    private var nextId = 0
    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "id-${nextId++}"
    }
    
    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.EPOCH
    }

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        validator = PreparationRecipeValidator()
        repository = RoomPreparationRecipeRepository(
            db,
            db.preparationRecipeDao(),
            db.ingredientDao(),
            db.ingredientUnitOptionDao(),
            validator,
            idGenerator,
            timeProvider
        )
        
        db.restaurantDao().insert(RestaurantEntity(restId.value, "R", "USD", "en-US", 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createDraft_succeeds() = runBlocking {
        val ingId = setupIngredient("Chicken Stock")
        val unitOptionId = setupUnitOption(ingId, "Gallon", BigDecimal("3785.41"))
        
        val command = CreatePreparationRecipeCommand(
            restaurantId = restId,
            outputIngredientId = ingId,
            name = "Basic Stock",
            standardYieldQuantity = BigDecimal("5.0"),
            yieldUnitOptionId = unitOptionId,
            notes = "Slow simmer"
        )
        
        val recipeId = repository.createDraft(command)
        val recipe = repository.getRecipe(recipeId)
        
        assertThat(recipe).isNotNull()
        assertThat(recipe!!.name).isEqualTo("Basic Stock")
        assertThat(recipe.status).isEqualTo(PreparationRecipeStatus.DRAFT)
        assertThat(recipe.standardYieldQuantity?.compareTo(BigDecimal("5.0"))).isEqualTo(0)
    }

    @Test
    fun saveComponent_succeeds() = runBlocking {
        val recipeId = setupDraftRecipe("Chicken Stock")
        val componentIngId = setupIngredient("Water")
        val unitOptionId = setupUnitOption(componentIngId, "Liter", BigDecimal("1000.0"))
        
        val command = SavePreparationRecipeComponentCommand(
            recipeId = recipeId,
            componentId = null,
            componentIngredientId = componentIngId,
            unitOptionId = unitOptionId,
            quantityEntered = BigDecimal("10.0"),
            sortOrder = 0,
            notes = "Cold"
        )
        
        val componentId = repository.saveComponent(command)
        val recipe = repository.getRecipe(recipeId)
        
        assertThat(recipe!!.components).hasSize(1)
        val component = recipe.components[0]
        assertThat(component.id).isEqualTo(componentId)
        assertThat(component.quantityEntered.compareTo(BigDecimal("10.0"))).isEqualTo(0)
        assertThat(component.quantityBase.compareTo(BigDecimal("10000.0"))).isEqualTo(0)
    }

    @Test
    fun activate_succeedsForValidRecipe() = runBlocking {
        val ingId = setupIngredient("Mojo")
        val unitOptionId = setupUnitOption(ingId, "L", BigDecimal.ONE)
        val recipeId = repository.createDraft(CreatePreparationRecipeCommand(restId, ingId, "Mojo", BigDecimal.ONE, unitOptionId, null))
        
        val componentId = setupIngredient("Garlic")
        val compUnitId = setupUnitOption(componentId, "g", BigDecimal.ONE)
        repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, componentId, compUnitId, BigDecimal("50.0"), 0, null))
        
        repository.activate(recipeId)
        
        val recipe = repository.getRecipe(recipeId)
        assertThat(recipe!!.status).isEqualTo(PreparationRecipeStatus.ACTIVE)
    }

    @Test
    fun activate_failsForMissingComponents() = runBlocking {
        val ingId = setupIngredient("Mojo")
        val unitOptionId = setupUnitOption(ingId, "L", BigDecimal.ONE)
        val recipeId = repository.createDraft(CreatePreparationRecipeCommand(restId, ingId, "Mojo", BigDecimal.ONE, unitOptionId, null))
        
        try {
            repository.activate(recipeId)
            assertThat(false).isTrue() // Should not reach here
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.AtLeastOneComponentRequired)
        }
    }

    @Test
    fun archive_preservesRecipe() = runBlocking {
        val recipeId = setupDraftRecipe("Stock")
        repository.archive(recipeId)
        
        val recipe = repository.getRecipe(recipeId)
        assertThat(recipe!!.status).isEqualTo(PreparationRecipeStatus.ARCHIVED)
        assertThat(recipe.archivedAt).isNotNull()
    }

    @Test
    fun observeRecipeSummaries_returnsCorrectData() = runBlocking {
        val ingId = setupIngredient("A")
        setupDraftRecipe("Recipe A", ingId)
        
        repository.observeRecipes(restId).test {
            val list = awaitItem()
            assertThat(list).hasSize(1)
            assertThat(list[0].outputIngredientName).isEqualTo("A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun setupIngredient(name: String): IngredientId {
        val id = IngredientId(idGenerator.newId())
        db.ingredientDao().insert(IngredientEntity(
            id = id.value,
            restaurantId = restId.value,
            name = name,
            normalizedName = name.lowercase(),
            categoryId = null,
            baseUnitId = "u1",
            defaultAreaId = null,
            sku = null,
            notes = null,
            reorderPointBase = null,
            isActive = true,
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null
        ))
        return id
    }

    private suspend fun setupUnitOption(ingId: IngredientId, name: String, factor: BigDecimal): IngredientUnitOptionId {
        val id = IngredientUnitOptionId(idGenerator.newId())
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(
            id = id.value,
            ingredientId = ingId.value,
            displayName = name,
            shortLabel = name,
            standardUnitId = null,
            factorToBase = factor,
            isBase = false,
            isDefaultCount = false,
            isDefaultPurchase = false,
            isActive = true,
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null
        ))
        return id
    }

    private suspend fun setupDraftRecipe(outputName: String, outputId: IngredientId? = null): PreparationRecipeId {
        val ingId = outputId ?: setupIngredient(outputName)
        val unitId = setupUnitOption(ingId, "Unit", BigDecimal.ONE)
        return repository.createDraft(CreatePreparationRecipeCommand(restId, ingId, outputName, BigDecimal.ONE, unitId, null))
    }
}
