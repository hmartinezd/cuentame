package com.miara.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
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
        
        // Verify movement created and values correct numerically
        val movements = database.inventoryMovementDao().getBySourceDocument("PURCHASE_RECEIPT", receiptId.value)
        assertThat(movements).hasSize(1)
        
        val movement = movements[0]
        assertBigDecimalEquivalent(movement.quantityBaseSigned, "10")
        assertBigDecimalEquivalent(movement.totalValueSnapshot!!, "100")
        
        // Verify balance projection updated
        val projection = database.inventoryProjectionDao().getBalance(ingId.value, areaId.value)
        assertBigDecimalEquivalent(projection!!.quantityBase, "10")
        
        // 4. Void
        repository.void(receiptId)
        
        val voided = repository.getReceipt(receiptId)
        assertThat(voided?.status).isEqualTo(DocumentStatus.VOIDED)
        
        // Verify reversal created
        val allMovements = database.inventoryMovementDao().getBySourceDocument("PURCHASE_RECEIPT", receiptId.value)
        assertThat(allMovements).hasSize(2)
        val reversal = allMovements.find { it.movementType == "REVERSAL" }!!
        assertBigDecimalEquivalent(reversal.quantityBaseSigned, "-10")
        
        // Verify balance projection restored to 0
        val finalProjection = database.inventoryProjectionDao().getBalance(ingId.value, areaId.value)
        val qty = finalProjection?.quantityBase ?: "0"
        assertBigDecimalEquivalent(qty, "0")
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
