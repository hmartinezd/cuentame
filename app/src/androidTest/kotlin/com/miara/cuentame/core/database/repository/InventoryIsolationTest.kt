package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.domain.repository.CreatePreparationRecipeCommand
import com.miara.cuentame.core.domain.repository.SavePreparationRecipeComponentCommand
import com.miara.cuentame.core.domain.validation.PreparationRecipeValidator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class InventoryIsolationTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomPreparationRecipeRepository
    private val restId = RestaurantId("rest-1")

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        val validator = PreparationRecipeValidator()
        repository = RoomPreparationRecipeRepository(
            db,
            db.preparationRecipeDao(),
            db.ingredientDao(),
            db.ingredientUnitOptionDao(),
            validator,
            object : IdGenerator { override fun newId(): String = "id" },
            object : TimeProvider { override fun now(): Instant = Instant.EPOCH }
        )
        
        db.restaurantDao().insert(RestaurantEntity(restId.value, "R", "USD", "en-US", 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recipeOperations_doNotModifyInventory() = runBlocking {
        // 1. Initial counts
        assertThat(db.inventoryMovementDao().getAll().size).isEqualTo(0)
        assertThat(db.inventoryProjectionDao().getAll().size).isEqualTo(0)
        
        // 2. Setup recipe
        val ingId = setupIngredient("A")
        val unitId = setupUnitOption(ingId, "U")
        val compId = setupIngredient("B")
        val compUnitId = setupUnitOption(compId, "U")
        
        // 3. Create Draft
        val recipeId = repository.createDraft(CreatePreparationRecipeCommand(restId, ingId, "Rec", BigDecimal.ONE, unitId, null))
        assertThat(db.inventoryMovementDao().getAll().size).isEqualTo(0)
        
        // 4. Save Component
        repository.saveComponent(SavePreparationRecipeComponentCommand(recipeId, null, compId, compUnitId, BigDecimal.TEN, 0, null))
        assertThat(db.inventoryMovementDao().getAll().size).isEqualTo(0)
        
        // 5. Activate
        repository.activate(recipeId)
        assertThat(db.inventoryMovementDao().getAll().size).isEqualTo(0)
        
        // 6. Final projections check
        assertThat(db.inventoryProjectionDao().getAll().size).isEqualTo(0)
        assertThat(db.ingredientCostProjectionDao().getAll().size).isEqualTo(0)
    }

    private suspend fun setupIngredient(name: String): IngredientId {
        val id = IngredientId(name)
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

    private suspend fun setupUnitOption(ingId: IngredientId, name: String): IngredientUnitOptionId {
        val id = IngredientUnitOptionId(ingId.value + name)
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(
            id = id.value,
            ingredientId = ingId.value,
            displayName = name,
            shortLabel = name,
            standardUnitId = null,
            factorToBase = BigDecimal.ONE,
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
}
