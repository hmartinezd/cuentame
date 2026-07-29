package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.model.inventory.DocumentStatus
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
class RoomPurchaseRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var repository: RoomPurchaseRepository

    private val restId = RestaurantId("rest-1")

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            database.restaurantDao().insert(RestaurantEntity(restId.value, "R", "USD", "en", 0L, 0L, null))
            
            // Seed a unit
            database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fullLifecycle_draft_to_posted_to_void() = runBlocking {
        // 1. Create Draft
        val command = CreatePurchaseDraftCommand(restId, null, "INV-1", Instant.now(), null)
        val receiptId = repository.createDraft(command)
        
        // 2. Add Line
        val ingId = IngredientId("i1")
        val areaId = InventoryAreaId("a1")
        val optId = IngredientUnitOptionId("o1")
        
        database.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(areaId.value, restId.value, "A", "a", 0, true, 0, 0, null))
        database.ingredientDao().insert(com.miara.cuentame.core.database.entity.IngredientEntity(ingId.value, restId.value, "I", "i", null, "u1", areaId.value, null, null, null, true, 0, 0, null))
        database.ingredientUnitOptionDao().insert(com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity(optId.value, ingId.value, "O", "o", null, BigDecimal.ONE, true, true, true, true, 0, 0, null))

        repository.saveLine(SavePurchaseLineCommand(
            receiptId = receiptId,
            lineId = null,
            ingredientId = ingId,
            areaId = areaId,
            ingredientUnitOptionId = optId,
            quantityEntered = BigDecimal("10"),
            lineTotal = BigDecimal("100"),
            notes = null
        ))
        
        // 3. Post
        repository.post(receiptId)
        
        val posted = repository.getReceipt(receiptId)
        assertThat(posted?.status).isEqualTo(DocumentStatus.POSTED)
        
        // Verify movement created
        val movements = database.inventoryMovementDao().getBySourceDocument("PURCHASE_RECEIPT", receiptId.value)
        assertThat(movements).hasSize(1)
        assertThat(movements[0].quantityBaseSigned).isEqualTo("10.0")
        
        // 4. Void
        repository.void(receiptId)
        
        val voided = repository.getReceipt(receiptId)
        assertThat(voided?.status).isEqualTo(DocumentStatus.VOIDED)
        
        // Verify reversal created
        val allMovements = database.inventoryMovementDao().getBySourceDocument("PURCHASE_RECEIPT", receiptId.value)
        assertThat(allMovements).hasSize(2)
        assertThat(allMovements.any { it.movementType == "REVERSAL" }).isTrue()
    }
}
