package com.miara.cuentame.feature.activity.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.InventoryMovementId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.InventoryActivityRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.inventory.*
import com.miara.cuentame.core.model.restaurant.Restaurant
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryActivityDetailViewModelTest {

    private val activityRepository = mockk<InventoryActivityRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val restaurant = Restaurant(
        id = RestaurantId("res1"),
        name = "Test",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(movementId: String?): InventoryActivityDetailViewModel {
        val handle = SavedStateHandle().apply {
            if (movementId != null) set("movementId", movementId)
        }
        return InventoryActivityDetailViewModel(activityRepository, restaurantRepository, handle)
    }

    @Test
    fun `loads item successfully`() = runTest {
        val movementId = "m1"
        val item = createItem(movementId)
        coEvery { activityRepository.getActivityItem(InventoryMovementId(movementId)) } returns item
        every { activityRepository.resolveSourceTarget(item) } returns InventoryActivitySourceTarget.Unavailable

        val viewModel = createViewModel(movementId)

        val state = viewModel.uiState.value as InventoryActivityDetailScreenState.Ready
        assertThat(state.item).isEqualTo(item)
        assertThat(state.currencyCode).isEqualTo("USD")
    }

    @Test
    fun `emits MovementNotFound when item does not exist`() = runTest {
        val movementId = "m1"
        coEvery { activityRepository.getActivityItem(InventoryMovementId(movementId)) } returns null

        val viewModel = createViewModel(movementId)

        assertThat(viewModel.uiState.value).isEqualTo(InventoryActivityDetailScreenState.MovementNotFound)
    }

    private fun createItem(id: String) = InventoryActivityItem(
        movement = InventoryMovement(
            id = InventoryMovementId(id),
            restaurantId = restaurant.id,
            ingredientId = com.miara.cuentame.core.common.ids.IngredientId("ing1"),
            areaId = InventoryAreaId("area1"),
            movementType = InventoryMovementType.PURCHASE,
            quantityBaseSigned = BigDecimal.ONE,
            unitCostBaseSnapshot = null,
            totalValueSnapshot = null,
            effectiveAt = Instant.EPOCH,
            sourceDocumentType = SourceDocumentType.PURCHASE_RECEIPT,
            sourceDocumentId = "doc1",
            sourceOperationId = "op1",
            sourceLineId = null,
            reversalOfMovementId = null,
            createdAt = Instant.EPOCH
        ),
        ingredientName = "Ing",
        areaName = "Area",
        baseUnitSymbol = "lb",
        sourceDisplay = InventoryActivitySourceDisplay("Title", null, null),
        reversedByMovementId = null,
        reversalOfDisplay = null,
        reversedByDisplay = null
    )
}
