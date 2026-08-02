package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.PreparationRecipeComponentEntity
import com.miara.cuentame.core.database.entity.PreparationRecipeEntity
import com.miara.cuentame.core.domain.repository.CreateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.repository.UpdateProductionBatchComponentCommand
import com.miara.cuentame.core.domain.repository.UpdateProductionBatchDraftCommand
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationFailure
import com.miara.cuentame.core.domain.validation.ProductionBatchValidationException
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
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
            com.miara.cuentame.core.database.entity.IngredientEntity(
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
            com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(
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

    private fun assertBigDecimalEquivalent(actual: BigDecimal, expected: String) {
        assertThat(actual.compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
