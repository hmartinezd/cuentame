package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.service.StandardUnitConverter
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
class RoomIngredientRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomIngredientRepository
    private val restId = RestaurantId("rest-1")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        repository = RoomIngredientRepository(
            db,
            db.ingredientDao(),
            db.ingredientUnitOptionDao(),
            db.unitDao(),
            db.restaurantDao(),
            db.ingredientCategoryDao(),
            StandardUnitConverter(),
            object : IdGenerator { override fun newId(): String = "id" },
            object : TimeProvider { override fun now(): Instant = Instant.EPOCH }
        )
        runBlocking {
            db.restaurantDao().insert(com.miara.cuentame.core.database.entity.RestaurantEntity(restId.value, "R", "USD", "en-US", 0L, 0L, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createIngredientWithBaseOption_succeeds() = runBlocking {
        val unitId = UnitId("u1")
        db.unitDao().insertSeedUnits(listOf(com.miara.cuentame.core.database.entity.UnitEntity(unitId.value, "U", "u", "MASS", BigDecimal.ONE, true, 0)))

        val ingId = IngredientId("i1")
        val ing = Ingredient(
            id = ingId,
            restaurantId = restId,
            name = "Test",
            normalizedName = "test",
            categoryId = null,
            baseUnitId = unitId,
            defaultAreaId = null,
            reorderPointBase = null,
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        
        val baseOption = IngredientUnitOption(
            id = IngredientUnitOptionId("opt1"),
            ingredientId = ingId,
            displayName = "u",
            shortLabel = "u",
            standardUnitId = unitId,
            factorToBase = BigDecimal.ONE,
            isBase = true,
            isDefaultCount = true,
            isDefaultPurchase = true,
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        repository.createIngredientWithBaseOption(ing, baseOption, emptyList())
        
        val loaded = repository.getById(ingId)
        assertThat(loaded?.name).isEqualTo("Test")
    }
}
