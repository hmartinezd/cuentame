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
    fun crossRestaurantOutput_isRejected() = runBlocking {
        val otherRestId = "rest-2"
        db.restaurantDao().insert(RestaurantEntity(otherRestId, "R2", "USD", "en-US", 0L, 0L, null))
        
        val ingId = IngredientId(idGenerator.newId())
        db.ingredientDao().insert(IngredientEntity(ingId.value, otherRestId, "Other", "other", null, "u1", null, null, null, null, true, 0L, 0L, null))
        
        val command = CreatePreparationRecipeCommand(restId, ingId, "Fail", BigDecimal.ONE, null, null)
        
        try {
            repository.createDraft(command)
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.OutputIngredientMustBelongToRestaurant)
        }
    }

    @Test
    fun deletedOutput_isRejected() = runBlocking {
        val ingId = setupIngredient("Deleted")
        db.ingredientDao().softArchive(ingId.value, 1000L)
        
        val command = CreatePreparationRecipeCommand(restId, ingId, "Fail", BigDecimal.ONE, null, null)
        
        try {
            repository.createDraft(command)
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.OutputIngredientDeleted)
        }
    }

    @Test
    fun activeRecipe_isImmutable() = runBlocking {
        val ingId = setupIngredient("Active")
        val unitId = setupUnitOption(ingId, "U", BigDecimal.ONE)
        val recipeId = repository.createDraft(CreatePreparationRecipeCommand(restId, ingId, "Active", BigDecimal.ONE, unitId, null))
        
        val compId = setupIngredient("Comp")
        val compUnitId = setupUnitOption(compId, "U", BigDecimal.ONE)
        repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, compId, compUnitId, BigDecimal.ONE, 0, null))
        
        repository.activate(recipeId)
        
        // Attempt update
        try {
            repository.updateDraft(UpdatePreparationRecipeCommand(recipeId, "New Name", BigDecimal.TEN, unitId, null))
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.InvalidStatusTransition)
        }
        
        // Attempt add component
        try {
            repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, compId, compUnitId, BigDecimal.TEN, 1, null))
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.InvalidStatusTransition)
        }
    }

    @Test
    fun oneNonArchivedRecipePerOutput_isEnforced() = runBlocking {
        val ingId = setupIngredient("Output")
        setupDraftRecipe("Recipe 1", ingId)
        
        // Attempt to create another draft for same output
        try {
            setupDraftRecipe("Recipe 2", ingId)
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.RecipeAlreadyExistsForOutput)
        }
    }

    @Test
    fun archiveAndNewRecipe_succeeds() = runBlocking {
        val ingId = setupIngredient("Output")
        val r1 = setupDraftRecipe("Recipe 1", ingId)
        repository.archive(r1)
        
        // Now creating a new one should succeed
        val r2 = setupDraftRecipe("Recipe 2", ingId)
        assertThat(r2).isNotEqualTo(r1)
    }

    @Test
    fun restoreConflict_isRejected() = runBlocking {
        val ingId = setupIngredient("Output")
        val r1 = setupDraftRecipe("Recipe 1", ingId)
        repository.archive(r1)
        
        val r2 = setupDraftRecipe("Recipe 2", ingId)
        
        // Attempt to restore r1 while r2 is active/draft
        try {
            repository.restoreToDraft(r1)
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.RecipeAlreadyExistsForOutput)
        }
    }

    @Test
    fun cycleDetection_worksForDirectCycle() = runBlocking {
        val ingA = setupIngredient("A")
        val unitA = setupUnitOption(ingA, "U", BigDecimal.ONE)
        val recipeA = repository.createDraft(CreatePreparationRecipeCommand(restId, ingA, "A", BigDecimal.ONE, unitA, null))
        
        val ingB = setupIngredient("B")
        val unitB = setupUnitOption(ingB, "U", BigDecimal.ONE)
        val recipeB = repository.createDraft(CreatePreparationRecipeCommand(restId, ingB, "B", BigDecimal.ONE, unitB, null))
        
        // A uses B
        repository.saveComponent(SavePreparationRecipeComponentCommand(recipeA, null, ingB, unitB, BigDecimal.ONE, 0, null))
        repository.activate(recipeA)
        
        // B attempts to use A
        repository.saveComponent(SavePreparationRecipeComponentCommand(recipeB, null, ingA, unitA, BigDecimal.ONE, 0, null))
        
        try {
            repository.activate(recipeB)
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.RecipeWouldCreateCycle)
        }
    }

    @Test
    fun reactiveObservation_emitsOnComponentChange() = runBlocking {
        val recipeId = setupDraftRecipe("Observed")
        val compId = setupIngredient("C")
        val unitId = setupUnitOption(compId, "U", BigDecimal.ONE)
        
        repository.observeRecipe(recipeId).test {
            val initial = awaitItem()
            assertThat(initial!!.components).isEmpty()
            
            repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, compId, unitId, BigDecimal.ONE, 0, null))
            val updated = awaitItem()
            assertThat(updated!!.components).hasSize(1)
            
            repository.removeComponent(recipeId, updated.components[0].id)
            val final = awaitItem()
            assertThat(final!!.components).isEmpty()
        }
    }

    @Test
    fun saveComponent_duplicateIngredient_preservesIdentityAndUpdateSortOrder() = runBlocking {
        val recipeId = setupDraftRecipe("Stock")
        val compId = setupIngredient("Water")
        val unitId = setupUnitOption(compId, "L", BigDecimal.ONE)
        
        val c1 = repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, compId, unitId, BigDecimal.ONE, 0, null))
        
        // Save again with same ingredient but different sort order
        val c2 = repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, compId, unitId, BigDecimal.TEN, 5, "More water"))
        
        assertThat(c2).isEqualTo(c1)
        
        val recipe = repository.getRecipe(recipeId)
        assertThat(recipe!!.components).hasSize(1)
        val comp = recipe.components[0]
        assertThat(comp.quantityEntered.compareTo(BigDecimal.TEN)).isEqualTo(0)
        assertThat(comp.sortOrder).isEqualTo(5)
        assertThat(comp.notes).isEqualTo("More water")
    }

    @Test
    fun reorderComponents_atomicUpdate() = runBlocking {
        val recipeId = setupDraftRecipe("Stock")
        val c1Id = setupIngredient("C1")
        val u1Id = setupUnitOption(c1Id, "U", BigDecimal.ONE)
        val comp1Id = repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, c1Id, u1Id, BigDecimal.ONE, 0, null))
        
        val c2Id = setupIngredient("C2")
        val u2Id = setupUnitOption(c2Id, "U", BigDecimal.ONE)
        val comp2Id = repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, c2Id, u2Id, BigDecimal.ONE, 1, null))
        
        // Swap
        repository.reorderComponents(recipeId, listOf(comp2Id, comp1Id))
        
        val recipe = repository.getRecipe(recipeId)
        assertThat(recipe!!.components[0].id).isEqualTo(comp2Id)
        assertThat(recipe.components[0].sortOrder).isEqualTo(0)
        assertThat(recipe.components[1].id).isEqualTo(comp1Id)
        assertThat(recipe.components[1].sortOrder).isEqualTo(1)
    }

    @Test
    fun reorderComponents_invalidIds_rejectedAtomics() = runBlocking {
        val recipeId = setupDraftRecipe("Stock")
        val c1Id = setupIngredient("C1")
        val u1Id = setupUnitOption(c1Id, "U", BigDecimal.ONE)
        val comp1Id = repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, c1Id, u1Id, BigDecimal.ONE, 0, null))
        
        try {
            repository.reorderComponents(recipeId, listOf(comp1Id, PreparationRecipeComponentId("fake")))
            assertThat(false).isTrue()
        } catch (e: RecipeValidationException) {
            assertThat(e.failures).contains(PreparationRecipeValidationFailure.InvalidComponentOrder)
        }
        
        // Verify sort order remains unchanged
        val recipe = repository.getRecipe(recipeId)
        assertThat(recipe!!.components[0].sortOrder).isEqualTo(0)
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
