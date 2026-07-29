package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.CreateWasteDraftCommand
import com.miara.cuentame.core.domain.service.InventorySnapshot
import com.miara.cuentame.core.domain.service.InventorySnapshotService
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.WasteReason
import com.miara.cuentame.test.TestSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
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
        // Note: RoomWasteRepository depends on InventorySnapshotService.
        // In our TestStorageModule, we didn't override it, so it uses the production one.
        // We might need to seed a movement to have a cost, or just post and expect null cost.
        repository.post(eventId)
        val posted = repository.getById(eventId)
        assertThat(posted?.status).isEqualTo(DocumentStatus.POSTED)
        
        // Verify movement
        val movements = database.inventoryMovementDao().getBySourceDocument("WASTE_EVENT", eventId.value)
        assertThat(movements).hasSize(1)
        assertThat(BigDecimal(movements[0].quantityBaseSigned).compareTo(BigDecimal("-1"))).isEqualTo(0)

        // 3. Void
        repository.void(eventId)
        val voided = repository.getById(eventId)
        assertThat(voided?.status).isEqualTo(DocumentStatus.VOIDED)
        
        // Verify reversal
        val allMovements = database.inventoryMovementDao().getBySourceDocument("WASTE_EVENT", eventId.value)
        assertThat(allMovements).hasSize(2)
        assertThat(allMovements.any { it.movementType == "REVERSAL" }).isTrue()
    }
}
