package com.venkoi.cuentame.core.domain.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.IngredientEntity
import com.venkoi.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.venkoi.cuentame.core.database.entity.RestaurantEntity
import com.venkoi.cuentame.core.database.entity.UnitEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class ChickenIntegrationTest {

    private lateinit var db: RestaurantInventoryDatabase
    private val restId = "rest-1"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createChickenIngredientWithMultipleUnits() = runBlocking {
        db.restaurantDao().insert(RestaurantEntity(restId, "R", "USD", "en", 0L, 0L, null))
        db.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        
        val ing = IngredientEntity("i1", restId, "Chicken", "chicken", null, "u1", null, null, null, null, true, 0L, 0L, null)
        db.ingredientDao().insert(ing)
        
        val opt1 = IngredientUnitOptionEntity("o1", "i1", "lb", "lb", "u1", BigDecimal.ONE, true, true, true, true, 0L, 0L, null)
        val opt2 = IngredientUnitOptionEntity("o2", "i1", "case", "case", null, BigDecimal("40"), false, false, false, true, 0L, 0L, null)
        db.ingredientUnitOptionDao().insert(opt1)
        db.ingredientUnitOptionDao().insert(opt2)
        
        val loadedOptions = db.ingredientUnitOptionDao().getAllOptions("i1")
        assertThat(loadedOptions).hasSize(2)
        assertThat(loadedOptions.find { it.id == "o2" }?.factorToBase).isEqualTo(BigDecimal("40"))
    }
}
