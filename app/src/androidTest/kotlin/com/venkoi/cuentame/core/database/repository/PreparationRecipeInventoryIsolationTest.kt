package com.venkoi.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.common.time.SystemTimeProvider
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.*
import com.venkoi.cuentame.core.domain.repository.CreatePreparationRecipeCommand
import com.venkoi.cuentame.core.domain.repository.SavePreparationRecipeComponentCommand
import com.venkoi.cuentame.core.domain.validation.PreparationRecipeGraphValidator
import com.venkoi.cuentame.core.domain.validation.PreparationRecipeValidator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class PreparationRecipeInventoryIsolationTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomPreparationRecipeRepository
    
    private val restId = RestaurantId("rest-1")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        
        val graphValidator = PreparationRecipeGraphValidator()
        val validator = PreparationRecipeValidator(graphValidator)
        
        repository = RoomPreparationRecipeRepository(
            database = db,
            recipeDao = db.preparationRecipeDao(),
            ingredientDao = db.ingredientDao(),
            unitOptionDao = db.ingredientUnitOptionDao(),
            validator = validator,
            graphValidator = graphValidator,
            idGenerator = UuidIdGenerator(),
            timeProvider = SystemTimeProvider()
        )
        
        seedBaseData()
    }

    private fun seedBaseData() = runBlocking {
        db.restaurantDao().insert(RestaurantEntity(restId.value, "Rest", "USD", "en", 0, 0, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "u", "u", "MASS", BigDecimal.ONE, true, 0)))
        db.ingredientDao().insert(IngredientEntity("i1", restId.value, "I1", "i1", null, "u1", null, null, null, null, true, 0, 0, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("o1", "i1", "O1", "o1", null, BigDecimal.ONE, true, true, true, true, 0, 0, null))
        db.ingredientDao().insert(IngredientEntity("i2", restId.value, "I2", "i2", null, "u1", null, null, null, null, true, 0, 0, null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("o2", "i2", "O2", "o2", null, BigDecimal.ONE, true, true, true, true, 0, 0, null))
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun recipeOperations_createNoInventorySideEffects() = runBlocking {
        // Initial counts
        assertThat(getInventoryCount()).isEqualTo(0)
        
        // 1. Create Draft
        val recipeId = repository.createDraft(CreatePreparationRecipeCommand(
            restaurantId = restId,
            outputIngredientId = IngredientId("i1"),
            name = "Recipe",
            standardYieldQuantity = BigDecimal.TEN,
            yieldUnitOptionId = IngredientUnitOptionId("o1"),
            notes = null
        ))
        assertThat(getInventoryCount()).isEqualTo(0)
        
        // 2. Save Component
        val compId = repository.saveComponent(SavePreparationRecipeComponentCommand(
            recipeId = recipeId,
            componentId = null,
            componentIngredientId = IngredientId("i2"),
            unitOptionId = IngredientUnitOptionId("o2"),
            quantityEntered = BigDecimal.ONE,
            sortOrder = 0,
            notes = null
        ))
        assertThat(getInventoryCount()).isEqualTo(0)
        
        // 3. Activate
        repository.activate(recipeId)
        assertThat(getInventoryCount()).isEqualTo(0)
        
        // 4. Archive
        repository.archive(recipeId)
        assertThat(getInventoryCount()).isEqualTo(0)
        
        // 5. Restore
        repository.restoreToDraft(recipeId)
        assertThat(getInventoryCount()).isEqualTo(0)
    }

    private suspend fun getInventoryCount(): Int {
        var count = 0
        count += db.backupDao().getAllInventoryMovements().size
        count += db.backupDao().getAllPurchaseReceipts().size
        count += db.backupDao().getAllPurchaseLines().size
        count += db.backupDao().getAllWasteEvents().size
        count += db.backupDao().getAllStockCounts().size
        count += db.backupDao().getAllStockCountAreas().size
        count += db.backupDao().getAllStockCountLines().size
        count += db.backupDao().getAllInventoryBalanceProjections().size
        count += db.backupDao().getAllIngredientCostProjections().size
        return count
    }
}
