package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.test.TestSeeder
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
    fun fullLifecycle_draft_to_posted_to_void() = runBlocking {
        // 1. Create Draft
        val command = CreatePurchaseDraftCommand(restId, null, "INV-1", Instant.now(), null)
        val receiptId = repository.createDraft(command)
        
        val ingId = IngredientId(TestSeeder.ING_ID)
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

        // 2. Add Line
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
        
        // Verify movement created and values correct
        val movements = database.inventoryMovementDao().getBySourceDocument("PURCHASE_RECEIPT", receiptId.value)
        assertThat(movements).hasSize(1)
        
        val movement = movements[0]
        assertThat(BigDecimal(movement.quantityBaseSigned).compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(BigDecimal(movement.totalValueSnapshot!!).compareTo(BigDecimal("100"))).isEqualTo(0)
        
        // Verify balance projection updated
        val projection = database.inventoryProjectionDao().getBalance(ingId.value, areaId.value)
        assertThat(BigDecimal(projection!!.quantityBase).compareTo(BigDecimal("10"))).isEqualTo(0)
        
        // 4. Void
        repository.void(receiptId)
        
        val voided = repository.getReceipt(receiptId)
        assertThat(voided?.status).isEqualTo(DocumentStatus.VOIDED)
        
        // Verify reversal created
        val allMovements = database.inventoryMovementDao().getBySourceDocument("PURCHASE_RECEIPT", receiptId.value)
        assertThat(allMovements).hasSize(2)
        val reversal = allMovements.find { it.movementType == "REVERSAL" }!!
        assertThat(BigDecimal(reversal.quantityBaseSigned).compareTo(BigDecimal("-10"))).isEqualTo(0)
        
        // Verify balance projection restored to 0
        val finalProjection = database.inventoryProjectionDao().getBalance(ingId.value, areaId.value)
        assertThat(BigDecimal(finalProjection!!.quantityBase).compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }
}
