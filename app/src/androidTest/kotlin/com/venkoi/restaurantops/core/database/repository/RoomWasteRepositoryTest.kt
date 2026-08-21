package com.venkoi.restaurantops.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.domain.repository.CreateWasteDraftCommand
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.core.model.inventory.WasteReason
import com.venkoi.restaurantops.test.TestSeeder
import com.venkoi.restaurantops.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
class RoomWasteRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomWasteRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restId = RestaurantId(TestSeeder.RESTAURANT_ID)

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            testStateManager.seedBaseline()
        }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun fullLifecycle_create_post_void() = runBlocking {
        val ingId = IngredientId(TestSeeder.ING_ID)
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

        // 1. Create
        val command = CreateWasteDraftCommand(restId, ingId, areaId, optId, BigDecimal.ONE, WasteReason.SPOILED, Instant.now(), null, null)
        val eventId = repository.createDraft(command)
        
        val draft = repository.getById(eventId)
        assertThat(draft?.status).isEqualTo(DocumentStatus.DRAFT)
        
        // 2. Post
        repository.post(eventId)
        val posted = repository.getById(eventId)
        assertThat(posted?.status).isEqualTo(DocumentStatus.POSTED)
        
        // Verify movement
        val movements = database.inventoryMovementDao().getBySourceDocument("WASTE_EVENT", eventId.value)
        assertThat(movements).hasSize(1)
        assertBigDecimalEquivalent(movements[0].quantityBaseSigned, "-1")

        // 3. Void
        repository.void(eventId)
        val voided = repository.getById(eventId)
        assertThat(voided?.status).isEqualTo(DocumentStatus.VOIDED)
        
        // Verify reversal
        val allMovements = database.inventoryMovementDao().getBySourceDocument("WASTE_EVENT", eventId.value)
        assertThat(allMovements).hasSize(2)
        val reversal = allMovements.find { it.movementType == "REVERSAL" }!!
        assertBigDecimalEquivalent(reversal.quantityBaseSigned, "1")
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
