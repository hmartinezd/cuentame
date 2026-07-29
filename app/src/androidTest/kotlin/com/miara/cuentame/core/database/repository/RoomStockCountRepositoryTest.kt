package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.StartStockCountCommand
import com.miara.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.miara.cuentame.core.model.inventory.StockCountStatus
import com.miara.cuentame.test.TestSeeder
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

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            TestSeeder.seedBaseline(database)
        }
    }

    @After
    fun tearDown() {
    }

    @Test
    fun fullLifecycle_start_save_complete_void() = runBlocking {
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val ingId = IngredientId(TestSeeder.ING_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

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
        
        // Verify adjustment created. Since seeded balance was 0, adjustment is 10.
        val movements = database.inventoryMovementDao().getBySourceDocument("STOCK_COUNT", countId.value)
        assertThat(movements).hasSize(1)
        assertThat(BigDecimal(movements[0].quantityBaseSigned).compareTo(BigDecimal("10"))).isEqualTo(0)

        // 4. Void
        repository.voidCount(countId)
        val voidedDetails = repository.observeCount(countId).first()!!
        assertThat(voidedDetails.count.status).isEqualTo(StockCountStatus.VOIDED)
        
        // Verify reversal
        val allMovements = database.inventoryMovementDao().getBySourceDocument("STOCK_COUNT", countId.value)
        assertThat(allMovements).hasSize(2)
        assertThat(allMovements.any { it.movementType == "REVERSAL" }).isTrue()
    }
}
