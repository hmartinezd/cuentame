package com.venkoi.cuentame.core.database.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.domain.repository.StartStockCountCommand
import com.venkoi.cuentame.core.domain.repository.SaveStockCountLineCommand
import com.venkoi.cuentame.core.domain.validation.ValidationError
import com.venkoi.cuentame.core.model.inventory.InventoryMovementType
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import com.venkoi.cuentame.core.model.inventory.StockCountStatus
import com.venkoi.cuentame.test.TestSeeder
import com.venkoi.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertThrows
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
        
        // Verify adjustment numerically
        val movements = database.inventoryMovementDao().getBySourceDocument("STOCK_COUNT", countId.value)
        assertThat(movements).hasSize(1)
        assertBigDecimalEquivalent(movements[0].quantityBaseSigned, "10")

        // 4. Void
        repository.voidCount(countId)
        val voidedDetails = repository.observeCount(countId).first()!!
        assertThat(voidedDetails.count.status).isEqualTo(StockCountStatus.VOIDED)
        
        // Verify reversal
        val allMovements = database.inventoryMovementDao().getBySourceDocument("STOCK_COUNT", countId.value)
        assertThat(allMovements).hasSize(2)
        val reversal = allMovements.find { it.movementType == "REVERSAL" }!!
        assertBigDecimalEquivalent(reversal.quantityBaseSigned, "-10")
    }

    @Test
    fun start_whenDraftExists_resumesSameCount() = runBlocking {
        val command = StartStockCountCommand(
            restId,
            "First count",
            Instant.now(),
            listOf(InventoryAreaId(TestSeeder.AREA_ID)),
            null
        )

        val first = repository.start(command)
        val resumed = repository.start(command.copy(name = "Ignored retry"))

        assertThat(resumed).isEqualTo(first)
        assertThat(database.stockCountDao().getCountsByStatus(restId.value, StockCountStatus.DRAFT.name)).hasSize(1)
    }

    @Test
    fun savedBaseQuantity_survivesLaterUnitFactorEdit() = runBlocking {
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val countId = repository.start(StartStockCountCommand(restId, "Factor snapshot", Instant.now(), listOf(areaId), null))
        val countAreaId = repository.observeCount(countId).first()!!.areas.single().area.id

        repository.saveLine(
            SaveStockCountLineCommand(
                countId,
                countAreaId,
                null,
                IngredientId(TestSeeder.ING_ID),
                IngredientUnitOptionId(TestSeeder.OPTION_ID),
                BigDecimal("8"),
                null
            )
        )
        val optionDao = database.ingredientUnitOptionDao()
        val option = optionDao.getById(TestSeeder.OPTION_ID)!!
        optionDao.update(option.copy(factorToBase = BigDecimal("2")))

        repository.completeArea(countId, countAreaId)
        repository.completeCount(countId)

        val line = repository.observeCount(countId).first()!!.areas.single().lines.single()
        assertThat(line.quantityBase.compareTo(BigDecimal("8"))).isEqualTo(0)
        val movement = database.inventoryMovementDao()
            .getBySourceDocument(SourceDocumentType.STOCK_COUNT.name, countId.value)
            .single()
        assertBigDecimalEquivalent(movement.quantityBaseSigned, "8")
    }

    @Test
    fun posting_rejectsInventoryDriftUntilLineIsRecounted() = runBlocking {
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val countId = repository.start(StartStockCountCommand(restId, "Drift", Instant.now(), listOf(areaId), null))
        val countAreaId = repository.observeCount(countId).first()!!.areas.single().area.id
        repository.saveLine(
            SaveStockCountLineCommand(
                countId,
                countAreaId,
                null,
                IngredientId(TestSeeder.ING_ID),
                IngredientUnitOptionId(TestSeeder.OPTION_ID),
                BigDecimal("7"),
                null
            )
        )
        repository.completeArea(countId, countAreaId)

        val now = Instant.now().toEpochMilli()
        database.inventoryMovementDao().insert(
            InventoryMovementEntity(
                id = "movement-after-count",
                restaurantId = restId.value,
                ingredientId = TestSeeder.ING_ID,
                areaId = TestSeeder.AREA_ID,
                movementType = InventoryMovementType.MANUAL_ADJUSTMENT.name,
                quantityBaseSigned = "1",
                unitCostBaseSnapshot = null,
                totalValueSnapshot = null,
                effectiveAt = now,
                sourceDocumentType = SourceDocumentType.MANUAL.name,
                sourceDocumentId = "manual-after-count",
                sourceOperationId = "manual-after-count",
                sourceLineId = null,
                reversalOfMovementId = null,
                createdAt = now
            )
        )

        assertThrows(ValidationError.StockCountInventoryChanged::class.java) {
            runBlocking { repository.completeCount(countId) }
        }
        assertThat(repository.observeCount(countId).first()!!.count.status).isEqualTo(StockCountStatus.DRAFT)
        assertThat(database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.STOCK_COUNT.name, countId.value)).isEmpty()

        val lineBefore = repository.observeCount(countId).first()!!.areas.single().lines.single()
        repository.reconfirmLine(countId, lineBefore.id)
        val lineAfter = repository.observeCount(countId).first()!!.areas.single().lines.single()
        assertThat(lineAfter.quantityEntered).isEqualTo(lineBefore.quantityEntered)
        assertThat(lineAfter.quantityBase).isEqualTo(lineBefore.quantityBase)
        assertThat(lineAfter.expectedQuantityBaseSnapshot?.compareTo(BigDecimal.ONE)).isEqualTo(0)
        assertThat(lineAfter.adjustmentQuantityBase?.compareTo(BigDecimal("6"))).isEqualTo(0)

        repository.completeCount(countId)
        assertThat(repository.observeCount(countId).first()!!.count.status).isEqualTo(StockCountStatus.COMPLETED)
    }

    @Test
    fun start_usesLiveTimeInsteadOfRequestedHistoricalTime() = runBlocking {
        val before = Instant.now().minusSeconds(1)
        val countId = repository.start(
            StartStockCountCommand(restId, "Live", Instant.parse("2000-01-01T00:00:00Z"), listOf(InventoryAreaId(TestSeeder.AREA_ID)), null)
        )
        val count = repository.observeCount(countId).first()!!.count
        assertThat(count.effectiveAt).isAtLeast(before)
        assertThat(count.effectiveAt).isEqualTo(count.startedAt)
    }

    @Test
    fun shelfOrder_persistsAndCanBeReordered() = runBlocking {
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val ingredientId = IngredientId(TestSeeder.ING_ID)

        repository.saveItemOrder(areaId, listOf(ingredientId))
        assertThat(repository.getItemOrder(areaId)).containsExactly(ingredientId).inOrder()

        repository.saveItemOrder(areaId, emptyList())
        assertThat(repository.getItemOrder(areaId)).isEmpty()
    }

    @Test
    fun observeHasCompletedCount_correctlyIdentifiesStatusAndIsolation() = runBlocking {
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val secondRestId = RestaurantId("second-rest")
        
        // 1. Initially false
        assertThat(repository.observeHasCompletedCount(restId).first()).isFalse()

        // 2. Draft count -> Still false
        val draftId = repository.start(StartStockCountCommand(restId, "Draft", Instant.now(), listOf(areaId), null))
        assertThat(repository.observeHasCompletedCount(restId).first()).isFalse()

        // 3. Completed count -> True
        val details = repository.observeCount(draftId).first()!!
        val countAreaId = details.areas.first().area.id
        repository.saveLine(SaveStockCountLineCommand(draftId, countAreaId, null, IngredientId(TestSeeder.ING_ID), IngredientUnitOptionId(TestSeeder.OPTION_ID), BigDecimal("10"), null))
        repository.completeArea(draftId, countAreaId)
        repository.completeCount(draftId)
        assertThat(repository.observeHasCompletedCount(restId).first()).isTrue()

        // 4. Voided count only? 
        // If we void the ONLY completed count, it should ideally still be false for "initial count completed" 
        // if we define it as "has at least one COMPLETED count". 
        // The requirement said: "A COMPLETED stock count is sufficient. DRAFT and VOIDED counts do not qualify."
        repository.voidCount(draftId)
        assertThat(repository.observeHasCompletedCount(restId).first()).isFalse()

        // 5. Old completed count -> True
        // Re-start and complete another one.
        val secondCountId = repository.start(StartStockCountCommand(restId, "Second", Instant.now(), listOf(areaId), null))
        val secondDetails = repository.observeCount(secondCountId).first()!!
        val secondCountAreaId = secondDetails.areas.first().area.id
        repository.saveLine(SaveStockCountLineCommand(secondCountId, secondCountAreaId, null, IngredientId(TestSeeder.ING_ID), IngredientUnitOptionId(TestSeeder.OPTION_ID), BigDecimal("20"), null))
        repository.completeArea(secondCountId, secondCountAreaId)
        repository.completeCount(secondCountId)
        assertThat(repository.observeHasCompletedCount(restId).first()).isTrue()

        // 6. Isolation -> Second restaurant still false
        assertThat(repository.observeHasCompletedCount(secondRestId).first()).isFalse()
    }

    @Test
    fun getExportRows_ownershipAndStatusGuards() = runBlocking {
        val areaId = InventoryAreaId(TestSeeder.AREA_ID)
        val ingId = IngredientId(TestSeeder.ING_ID)
        val optId = IngredientUnitOptionId(TestSeeder.OPTION_ID)

        // 1. Create a count for Restaurant A
        val countId = repository.start(StartStockCountCommand(restId, "C1", Instant.now(), listOf(areaId), null))
        val countAreaId = repository.observeCount(countId).first()!!.areas.first().area.id
        repository.saveLine(SaveStockCountLineCommand(countId, countAreaId, null, ingId, optId, BigDecimal("10"), null))
        repository.completeArea(countId, countAreaId)

        // 2. Programmatic export for DRAFT should fail
        assertThrows(ValidationError.RecordNotFound::class.java) {
            runBlocking { repository.getExportRows(countId) }
        }

        // 3. Complete the count
        repository.completeCount(countId)

        // 4. Export for COMPLETED should succeed
        val rows = repository.getExportRows(countId)
        assertThat(rows).isNotEmpty()
        assertThat(rows.first().ingredientName).isEqualTo("Beef")

        // 5. Switch active restaurant to Restaurant B by replacing the restaurant record
        val secondRestId = RestaurantId("rest-b")
        database.restaurantDao().softDelete(restId.value, System.currentTimeMillis())
        database.restaurantDao().insert(
            com.venkoi.cuentame.core.database.entity.RestaurantEntity(
                id = secondRestId.value,
                name = "Rest B",
                currencyCode = "USD",
                localeTag = "en-US",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                deletedAt = null
            )
        )

        // 6. Export for Restaurant A's count under Restaurant B should fail
        assertThrows(ValidationError.StockCountOwnershipMismatch::class.java) {
            runBlocking { repository.getExportRows(countId) }
        }
    }

    private fun assertBigDecimalEquivalent(actual: String, expected: String) {
        assertThat(BigDecimal(actual).compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
