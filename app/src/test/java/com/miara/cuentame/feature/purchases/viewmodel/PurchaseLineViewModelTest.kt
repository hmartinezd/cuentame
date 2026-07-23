package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.service.PurchaseLineCalculator
import com.miara.cuentame.core.domain.usecase.GetIngredientDetailUseCase
import com.miara.cuentame.core.domain.usecase.GetPurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientUnitOptionsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveIngredientsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveInventoryAreasUseCase
import com.miara.cuentame.core.domain.usecase.PreviewPurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.SavePurchaseLineUseCase
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import com.miara.cuentame.core.model.purchase.PurchaseLine
import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseLineViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeRestaurantRepository = object : RestaurantRepository {
        val restaurant = Restaurant(RestaurantId("rest_1"), "Test Rest", "USD", "en-US", Instant.now(), Instant.now())
        override fun observeRestaurant(): Flow<Restaurant?> = flowOf(restaurant)
        override suspend fun getRestaurant(): Restaurant? = restaurant
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val fakeIngredient = Ingredient(IngredientId("ing_1"), RestaurantId("rest_1"), "Chicken", "chicken", null, UnitId("mass_lb"), null, null, null, null, true, Instant.now(), Instant.now())
    private val fakeArea = InventoryArea(InventoryAreaId("area_1"), RestaurantId("rest_1"), "Dry", "dry", 0, true, Instant.now(), Instant.now())
    private val fakeOption = IngredientUnitOption(IngredientUnitOptionId("opt_1"), IngredientId("ing_1"), "Pound", "lb", UnitId("mass_lb"), BigDecimal.ONE, true, true, true, true, Instant.now(), Instant.now())

    private val fakePurchaseRepository = object : PurchaseRepository {
        override fun observePurchases(filter: PurchaseFilter): Flow<List<com.miara.cuentame.core.domain.repository.PurchaseSummary>> = flowOf(emptyList())
        override fun observePurchase(id: PurchaseReceiptId): Flow<com.miara.cuentame.core.domain.repository.PurchaseDetails?> = flowOf(null)
        override suspend fun getReceipt(id: PurchaseReceiptId): com.miara.cuentame.core.model.purchase.PurchaseReceipt? = null
        override suspend fun createDraft(command: com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand): PurchaseReceiptId = PurchaseReceiptId("")
        override suspend fun updateDraft(command: com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand) {}
        override suspend fun saveLine(command: com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand): PurchaseLineId = PurchaseLineId("")
        override suspend fun deleteLine(receiptId: PurchaseReceiptId, lineId: PurchaseLineId) {}
        override suspend fun deleteDraft(id: PurchaseReceiptId) {}
        override suspend fun post(id: PurchaseReceiptId) {}
        override suspend fun void(id: PurchaseReceiptId) {}
    }

    private val fakeIngredientRepository = object : com.miara.cuentame.core.domain.repository.IngredientRepository {
        override fun observeIngredients(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Ingredient>> = flowOf(listOf(fakeIngredient))
        override fun observeIngredient(id: IngredientId): Flow<Ingredient?> = flowOf(fakeIngredient)
        override suspend fun getById(id: IngredientId): Ingredient? = fakeIngredient
        override suspend fun updateIngredient(command: com.miara.cuentame.core.domain.repository.UpdateIngredientCommand) {}
        override suspend fun archive(id: IngredientId, at: Instant) {}
        override fun observeUnitOptions(ingredientId: IngredientId, includeArchived: Boolean): Flow<List<IngredientUnitOption>> = flowOf(listOf(fakeOption))
        override suspend fun addStandardUnitOption(command: com.miara.cuentame.core.domain.repository.AddStandardUnitOptionCommand) {}
        override suspend fun addPackageUnitOption(command: com.miara.cuentame.core.domain.repository.AddPackageUnitOptionCommand) {}
        override suspend fun updatePackageUnitOption(command: com.miara.cuentame.core.domain.repository.UpdatePackageUnitOptionCommand) {}
        override suspend fun setDefaultCountOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun setDefaultPurchaseOption(ingredientId: IngredientId, optionId: IngredientUnitOptionId) {}
        override suspend fun archiveUnitOption(id: IngredientUnitOptionId, at: Instant) {}
        override suspend fun createIngredientWithBaseOption(ingredient: Ingredient, baseOption: IngredientUnitOption, additionalOptions: List<IngredientUnitOption>) {}
    }

    private val fakeAreaRepository = object : com.miara.cuentame.core.domain.repository.InventoryAreaRepository {
        override fun observeActiveAreas(): Flow<List<InventoryArea>> = flowOf(listOf(fakeArea))
        override fun observeAllAreas(): Flow<List<InventoryArea>> = flowOf(listOf(fakeArea))
        override suspend fun getById(id: InventoryAreaId): InventoryArea? = fakeArea
        override suspend fun save(area: InventoryArea) {}
        override suspend fun archive(id: InventoryAreaId, at: Instant) {}
        override suspend fun reorder(ids: List<InventoryAreaId>) {}
    }

    private val fakeUnitRepository = object : UnitRepository {
        override suspend fun getById(id: UnitId): UnitOfMeasure? = UnitOfMeasure(UnitId("mass_lb"), "Pound", "lb", UnitDimension.MASS, BigDecimal.ONE, true, 0)
        override fun observeAll(): Flow<List<UnitOfMeasure>> = flowOf(emptyList())
        override fun observeByDimension(dimension: UnitDimension): Flow<List<UnitOfMeasure>> = flowOf(emptyList())
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading then ready for new line`() = runTest {
        val viewModel = createViewModel("receipt_1", null)
        runCurrent()
        
        assertEquals(PurchaseLineScreenState.Ready, viewModel.uiState.value.screenState)
        assertEquals(null, viewModel.uiState.value.lineId)
    }

    @Test
    fun `ingredient selection updates options and previews`() = runTest {
        val viewModel = createViewModel("receipt_1", null)
        runCurrent()

        viewModel.onIngredientSelected(IngredientId("ing_1"))
        runCurrent()
        
        viewModel.onQuantityChanged("2")
        viewModel.onTotalChanged("10")
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(IngredientId("ing_1"), state.selectedIngredientId)
        assertEquals(IngredientUnitOptionId("opt_1"), state.selectedUnitOptionId)
        assertEquals(0, BigDecimal("2").compareTo(state.baseQuantityPreview))
        assertEquals(0, BigDecimal("5").compareTo(state.unitCostPreview))
    }

    private fun createViewModel(receiptId: String?, lineId: String?): PurchaseLineViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            if (receiptId != null) set("purchaseId", receiptId)
            if (lineId != null) set("lineId", lineId)
        }
        return PurchaseLineViewModel(
            savedStateHandle,
            SavePurchaseLineUseCase(fakePurchaseRepository),
            GetPurchaseLineUseCase(fakePurchaseRepository),
            ObserveIngredientsUseCase(fakeIngredientRepository),
            ObserveInventoryAreasUseCase(fakeAreaRepository),
            ObserveIngredientUnitOptionsUseCase(fakeIngredientRepository),
            GetIngredientDetailUseCase(fakeIngredientRepository),
            PreviewPurchaseLineUseCase(PurchaseLineCalculator()),
            fakeRestaurantRepository,
            fakeUnitRepository
        )
    }
}
