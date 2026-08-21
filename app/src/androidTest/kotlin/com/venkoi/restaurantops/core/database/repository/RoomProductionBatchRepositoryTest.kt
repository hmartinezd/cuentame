package com.venkoi.restaurantops.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.PreparationRecipeComponentEntity
import com.venkoi.restaurantops.core.database.entity.PreparationRecipeEntity
import com.venkoi.restaurantops.core.domain.repository.CreateProductionBatchDraftCommand
import com.venkoi.restaurantops.core.domain.repository.UpdateProductionBatchComponentCommand
import com.venkoi.restaurantops.core.domain.repository.UpdateProductionBatchDraftCommand
import com.venkoi.restaurantops.core.domain.validation.ProductionBatchValidationFailure
import com.venkoi.restaurantops.core.domain.validation.ProductionBatchValidationException
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipeStatus
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.test.TestSeeder
import com.venkoi.restaurantops.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoomProductionBatchRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomProductionBatchRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)
    private val recipeId = PreparationRecipeId("recipe-1")
    private val componentIngredientId = IngredientId(TestSeeder.ING_ID)
    private val outputIngredientId = IngredientId("output-ing-1")
    private val areaId = InventoryAreaId(TestSeeder.AREA_ID)
    private val optionId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        testStateManager.resetAll()
        testStateManager.seedBaseline()
        seedOutputIngredient()
        seedRecipe()
    }

    private suspend fun seedOutputIngredient() {
        database.ingredientDao().insert(
            com.venkoi.restaurantops.core.database.entity.IngredientEntity(
                id = outputIngredientId.value,
                restaurantId = restId.value,
                name = "Prepared Salad",
                normalizedName = "prepared salad",
                categoryId = null,
                baseUnitId = TestSeeder.UNIT_ID,
                defaultAreaId = areaId.value,
                sku = null,
                notes = null,
                reorderPointBase = null,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
        database.ingredientUnitOptionDao().insert(
            com.venkoi.restaurantops.core.database.entity.IngredientUnitOptionEntity(
                id = "output-opt-1",
                ingredientId = outputIngredientId.value,
                displayName = "Container",
                shortLabel = "ct",
                standardUnitId = null,
                factorToBase = BigDecimal("2"),
                isBase = false,
                isDefaultCount = true,
                isDefaultPurchase = false,
                isActive = true,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null
            )
        )
    }

    private suspend fun seedRecipe() {
        database.preparationRecipeDao().insert(
            PreparationRecipeEntity(
                id = recipeId.value,
                restaurantId = restId.value,
                outputIngredientId = outputIngredientId.value,
                name = "Test Recipe",
                normalizedName = "test recipe",
                standardYieldQuantity = BigDecimal("1"),
                standardYieldQuantityBase = BigDecimal("2"),
                yieldUnitOptionId = "output-opt-1",
                status = PreparationRecipeStatus.ACTIVE.name,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L,
                archivedAt = null
            )
        )
        database.preparationRecipeDao().upsertComponent(
            PreparationRecipeComponentEntity(
                id = "comp-1",
                recipeId = recipeId.value,
                componentIngredientId = componentIngredientId.value,
                unitOptionId = optionId.value,
                quantityEntered = BigDecimal("0.5"),
                quantityBase = BigDecimal("0.5"),
                sortOrder = 0,
                notes = null,
                createdAt = 0L,
                updatedAt = 0L
            )
        )
    }

    @Test
    fun createDraft_success() = runBlocking {
        val command = CreateProductionBatchDraftCommand(
            restaurantId = restId,
            recipeId = recipeId,
            batchMultiplier = BigDecimal("2"),
            outputAreaId = areaId,
            actualOutputQuantityEntered = null,
            outputUnitOptionId = IngredientUnitOptionId("output-opt-1"),
            effectiveAt = Instant.now(),
            notes = "Test Note"
        )

        val batchId = repository.createDraft(command)
        val batch = repository.getBatch(batchId)

        assertThat(batch).isNotNull()
        assertThat(batch?.status).isEqualTo(DocumentStatus.DRAFT)
        assertBigDecimalEquivalent(batch?.batchMultiplier ?: BigDecimal.ZERO, "2")
        assertBigDecimalEquivalent(batch?.expectedOutputQuantityBase ?: BigDecimal.ZERO, "4") // 2 * 2
        assertBigDecimalEquivalent(batch?.actualOutputQuantityBase ?: BigDecimal.ZERO, "4")
        assertThat(batch?.notes).isEqualTo("Test Note")

        assertThat(batch?.components).hasSize(1)
        val component = batch?.components?.get(0)
        assertBigDecimalEquivalent(component?.expectedQuantityBase ?: BigDecimal.ZERO, "1") // 0.5 * 2
        assertBigDecimalEquivalent(component?.actualQuantityBase ?: BigDecimal.ZERO, "1")
    }

    @Test
    fun createDraft_fails_whenRecipeNotActive() = runBlocking {
        database.preparationRecipeDao().updateStatus(recipeId.value, PreparationRecipeStatus.DRAFT.name, 0L, null)

        val command = CreateProductionBatchDraftCommand(
            restaurantId = restId,
            recipeId = recipeId,
            batchMultiplier = BigDecimal("2"),
            outputAreaId = areaId,
            actualOutputQuantityEntered = null,
            outputUnitOptionId = null,
            effectiveAt = Instant.now(),
            notes = null
        )

        val exception = assertThrows(ProductionBatchValidationException::class.java) {
            runBlocking { repository.createDraft(command) }
        }
        assertThat(exception.failures).contains(ProductionBatchValidationFailure.RecipeNotActive)
    }

    @Test
    fun updateDraft_updatesFieldsAndComponents() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restaurantId = restId,
            recipeId = recipeId,
            batchMultiplier = BigDecimal("1"),
            outputAreaId = areaId,
            actualOutputQuantityEntered = null,
            outputUnitOptionId = null,
            effectiveAt = Instant.now(),
            notes = null
        ))

        repository.updateDraft(UpdateProductionBatchDraftCommand(
            batchId = batchId,
            batchMultiplier = BigDecimal("3"),
            outputAreaId = null,
            actualOutputQuantityEntered = null,
            outputUnitOptionId = null,
            effectiveAt = null,
            notes = "Updated Note"
        ))

        val updated = repository.getBatch(batchId)
        assertBigDecimalEquivalent(updated?.batchMultiplier ?: BigDecimal.ZERO, "3")
        assertThat(updated?.notes).isEqualTo("Updated Note")
        
        // Expected and Actual should be updated because no manual override yet
        assertBigDecimalEquivalent(updated?.expectedOutputQuantityBase ?: BigDecimal.ZERO, "6") // 2 * 3
        assertBigDecimalEquivalent(updated?.actualOutputQuantityBase ?: BigDecimal.ZERO, "6")

        val component = updated?.components?.get(0)
        assertBigDecimalEquivalent(component?.expectedQuantityBase ?: BigDecimal.ZERO, "1.5") // 0.5 * 3
        assertBigDecimalEquivalent(component?.actualQuantityBase ?: BigDecimal.ZERO, "1.5")
    }

    @Test
    fun updateComponent_success() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        val batch = repository.getBatch(batchId)!!
        val compId = batch.components[0].id

        repository.updateComponent(UpdateProductionBatchComponentCommand(
            batchId = batchId,
            componentId = compId,
            sourceAreaId = null,
            actualQuantityEntered = BigDecimal("0.7"),
            unitOptionId = null,
            notes = "Component Note"
        ))

        val updated = repository.getBatch(batchId)
        val updatedComp = updated?.components?.get(0)
        assertBigDecimalEquivalent(updatedComp?.actualQuantityEntered ?: BigDecimal.ZERO, "0.7")
        assertThat(updatedComp?.hasManualQuantityOverride).isTrue()
        assertThat(updatedComp?.notes).isEqualTo("Component Note")
    }

    @Test
    fun resetComponent_success() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        val batch = repository.getBatch(batchId)!!
        val compId = batch.components[0].id

        repository.updateComponent(UpdateProductionBatchComponentCommand(
            batchId = batchId,
            componentId = compId,
            sourceAreaId = null,
            actualQuantityEntered = BigDecimal("10"),
            unitOptionId = null,
            notes = null
        ))

        repository.resetComponentToExpected(batchId, compId)

        val reset = repository.getBatch(batchId)
        val resetComp = reset?.components?.get(0)
        assertBigDecimalEquivalent(resetComp?.actualQuantityEntered ?: BigDecimal.ZERO, "0.5")
        assertThat(resetComp?.hasManualQuantityOverride).isFalse()
    }

    @Test
    fun deleteDraft_success() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        
        repository.deleteDraft(batchId)
        assertThat(repository.getBatch(batchId)).isNull()
        
        val components = database.productionBatchDao().getComponents(batchId.value)
        assertThat(components).isEmpty()
    }

    @Test
    fun updateDraft_multiplierChange_withDifferentOutputUnit_convertsCorrectly() = runBlocking {
        // Recipe yield: 1 Container (2 base units)
        // Multiplier: 1 -> 3
        // Expected base: 6
        
        // 1. Same option
        val batchId1 = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        repository.updateDraft(UpdateProductionBatchDraftCommand(batchId1, BigDecimal("3"), null, null, null, null, null))
        val batch1 = repository.getBatch(batchId1)!!
        assertBigDecimalEquivalent(batch1.expectedOutputQuantityEntered, "3") // 1 * 3
        assertBigDecimalEquivalent(batch1.actualOutputQuantityEntered, "3")
        assertBigDecimalEquivalent(batch1.actualOutputQuantityBase, "6") // 3 * 2

        // 2. Different option (Base unit)
        // Seed base unit option for output ingredient
        database.ingredientUnitOptionDao().insert(
            com.venkoi.restaurantops.core.database.entity.IngredientUnitOptionEntity(
                id = "output-base-opt", ingredientId = outputIngredientId.value, displayName = "Lb", shortLabel = "lb",
                standardUnitId = null, factorToBase = BigDecimal.ONE, isBase = true, isDefaultCount = false,
                isDefaultPurchase = false, isActive = true, createdAt = 0L, updatedAt = 0L, deletedAt = null
            )
        )
        
        val batchId2 = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, IngredientUnitOptionId("output-base-opt"), Instant.now(), null
        ))
        // Expected base = 2. Option factor = 1. So expected entered = 2.
        val initial2 = repository.getBatch(batchId2)!!
        assertBigDecimalEquivalent(initial2.actualOutputQuantityEntered, "2") 
        
        repository.updateDraft(UpdateProductionBatchDraftCommand(batchId2, BigDecimal("3"), null, null, null, null, null))
        val batch2 = repository.getBatch(batchId2)!!
        assertBigDecimalEquivalent(batch2.expectedOutputQuantityBase, "6")
        assertBigDecimalEquivalent(batch2.actualOutputQuantityEntered, "6") // 6 / 1

        // 3. Different option (Another package - 4 base units)
        database.ingredientUnitOptionDao().insert(
            com.venkoi.restaurantops.core.database.entity.IngredientUnitOptionEntity(
                id = "output-pkg-4", ingredientId = outputIngredientId.value, displayName = "Case", shortLabel = "cs",
                standardUnitId = null, factorToBase = BigDecimal("4"), isBase = false, isDefaultCount = false,
                isDefaultPurchase = false, isActive = true, createdAt = 0L, updatedAt = 0L, deletedAt = null
            )
        )
        val batchId3 = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, IngredientUnitOptionId("output-pkg-4"), Instant.now(), null
        ))
        // Expected base = 2. Option factor = 4. So expected entered = 0.5.
        
        repository.updateDraft(UpdateProductionBatchDraftCommand(batchId3, BigDecimal("4"), null, null, null, null, null))
        // Expected base = 2 * 4 = 8. Option factor = 4. Expected entered = 2.
        val batch3 = repository.getBatch(batchId3)!!
        assertBigDecimalEquivalent(batch3.expectedOutputQuantityBase, "8")
        assertBigDecimalEquivalent(batch3.actualOutputQuantityEntered, "2") // 8 / 4
    }

    @Test
    fun updateDraft_multiplierChange_preservesManualOverrides() = runBlocking {
        val batchId = repository.createDraft(CreateProductionBatchDraftCommand(
            restId, recipeId, BigDecimal.ONE, areaId, null, null, Instant.now(), null
        ))
        
        // Manual output override
        repository.updateDraft(UpdateProductionBatchDraftCommand(
            batchId = batchId, batchMultiplier = null, outputAreaId = null,
            actualOutputQuantityEntered = BigDecimal("10"), outputUnitOptionId = null,
            effectiveAt = null, notes = null
        ))
        
        // Manual component override
        val compId = repository.getBatch(batchId)!!.components[0].id
        repository.updateComponent(UpdateProductionBatchComponentCommand(
            batchId, compId, null, BigDecimal("1.5"), null, null
        ))
        
        // Change multiplier
        repository.updateDraft(UpdateProductionBatchDraftCommand(
            batchId = batchId, batchMultiplier = BigDecimal("2"), outputAreaId = null,
            actualOutputQuantityEntered = null, outputUnitOptionId = null,
            effectiveAt = null, notes = null
        ))
        
        val updated = repository.getBatch(batchId)!!
        // Expected output should scale: 2 (base per recipe) * 2 (multiplier) = 4
        assertBigDecimalEquivalent(updated.expectedOutputQuantityBase, "4")
        // Actual output should remain 10 entered (20 base)
        assertBigDecimalEquivalent(updated.actualOutputQuantityEntered, "10")
        assertBigDecimalEquivalent(updated.actualOutputQuantityBase, "20")
        
        val comp = updated.components[0]
        // Expected component should scale: 0.5 (per recipe) * 2 = 1.0
        assertBigDecimalEquivalent(comp.expectedQuantityBase, "1.0")
        // Actual component should remain 1.5
        assertBigDecimalEquivalent(comp.actualQuantityEntered, "1.5")
    }

    private fun assertBigDecimalEquivalent(actual: BigDecimal, expected: String) {
        assertThat(actual.compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
