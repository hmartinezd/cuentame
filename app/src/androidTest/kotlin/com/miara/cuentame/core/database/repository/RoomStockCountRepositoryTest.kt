package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.domain.repository.StartStockCountCommand
import com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.miara.cuentame.core.model.inventory.StockCountStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoomStockCountRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomStockCountRepository

    private val restId = RestaurantId("rest-1")

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            database.restaurantDao().insert(RestaurantEntity(restId.value, "R", "USD", "en", 0L, 0L, null))
            database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fullLifecycle_start_save_complete_void() = runBlocking {
        val areaId = InventoryAreaId("a1")
        val ingId = IngredientId("i1")
        val optId = IngredientUnitOptionId("o1")
        
        database.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId.value, restId.value, "A", "a", 0, true, 0, 0, null))
        database.ingredientDao().insert(com.miara.cuentame.core.database.entity.IngredientEntity(ingId.value, restId.value, "I", "i", null, "u1", areaId.value, null, null, null, true, 0, 0, null))
        database.ingredientUnitOptionDao().insert(com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(optId.value, ingId.value, "O", "o", null, BigDecimal.ONE, true, true, true, true, 0, 0, null))

        // 1. Start
        val countId = repository.start(StartStockCountCommand(restId, "C1", Instant.now(), listOf(areaId), null))
        
        val details = repository.observeCount(countId).first()!!
        val countAreaId = details.areas.first().area.id
        
        // 2. Save Line
        repository.saveLine(SaveStockCountLineCommand(countId, countAreaId, null, ingId, optId, BigDecimal("10"), null))
        repository.completeArea(countId, countAreaId)
        
        // 3. Complete
        repository.completeCount(countId)
        
        val finalDetails = repository.observeCount(countId).first()!!
        assertThat(finalDetails.count.status).isEqualTo(StockCountStatus.COMPLETED)
        
        // 4. Void
        repository.voidCount(countId)
        val voidedDetails = repository.observeCount(countId).first()!!
        assertThat(voidedDetails.count.status).isEqualTo(StockCountStatus.VOIDED)
    }
}
