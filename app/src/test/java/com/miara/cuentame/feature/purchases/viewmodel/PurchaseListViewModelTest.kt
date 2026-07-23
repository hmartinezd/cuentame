package com.miara.cuentame.feature.purchases.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.usecase.ObservePurchasesUseCase
import com.miara.cuentame.core.domain.usecase.ObserveSuppliersUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.model.supplier.Supplier
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
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)
    private val purchasesFlow = MutableStateFlow<List<PurchaseSummary>>(emptyList())

    private val fakePurchaseRepository = object : PurchaseRepository {
        override fun observePurchases(filter: PurchaseFilter): Flow<List<PurchaseSummary>> = purchasesFlow
        override fun observePurchase(id: PurchaseReceiptId): Flow<com.miara.cuentame.core.domain.repository.PurchaseDetails?> = flowOf(null)
        override suspend fun getReceipt(id: PurchaseReceiptId): PurchaseReceipt? = null
        override suspend fun createDraft(command: com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand): PurchaseReceiptId = PurchaseReceiptId("")
        override suspend fun updateDraft(command: com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand) {}
        override suspend fun saveLine(command: com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand): com.miara.cuentame.core.common.ids.PurchaseLineId = com.miara.cuentame.core.common.ids.PurchaseLineId("")
        override suspend fun deleteLine(receiptId: PurchaseReceiptId, lineId: com.miara.cuentame.core.common.ids.PurchaseLineId) {}
        override suspend fun deleteDraft(id: PurchaseReceiptId) {}
        override suspend fun post(id: PurchaseReceiptId) {}
        override suspend fun void(id: PurchaseReceiptId) {}
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant(): Restaurant? = restaurantFlow.value
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val fakeSupplierRepository = object : com.miara.cuentame.core.domain.repository.SupplierRepository {
        override fun observeSuppliers(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Supplier>> = flowOf(emptyList())
        override fun observeSupplier(id: SupplierId): Flow<Supplier?> = flowOf(null)
        override suspend fun getSupplier(id: SupplierId): Supplier? = null
        override suspend fun createSupplier(command: com.miara.cuentame.core.domain.repository.CreateSupplierCommand): SupplierId = SupplierId("")
        override suspend fun updateSupplier(command: com.miara.cuentame.core.domain.repository.UpdateSupplierCommand) {}
        override suspend fun archiveSupplier(id: SupplierId, at: Instant) {}
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        restaurantFlow.value = Restaurant(RestaurantId("r1"), "R1", "USD", "en-US", Instant.now(), Instant.now())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `list updates when repository emits new purchases`() = runTest {
        val viewModel = PurchaseListViewModel(
            ObservePurchasesUseCase(fakePurchaseRepository),
            ObserveSuppliersUseCase(fakeSupplierRepository),
            fakeRestaurantRepository
        )
        
        val summary = PurchaseSummary(
            receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, "INV-1", Instant.now(), DocumentStatus.DRAFT, null, null, Instant.now(), Instant.now()),
            supplierName = "Supplier A",
            lineCount = 2,
            totalAmount = BigDecimal("100")
        )
        
        viewModel.uiState.test {
            // Initial empty list (might need a few emissions depending on combine)
            purchasesFlow.value = listOf(summary)
            
            // Skip initial loading/empty state if necessary or check it
            var latest = awaitItem()
            while (latest.purchases.isEmpty()) {
                latest = awaitItem()
            }
            assertThat(latest.purchases).containsExactly(summary)
        }
    }

    @Test
    fun `search updates filter`() = runTest {
        val viewModel = PurchaseListViewModel(
            ObservePurchasesUseCase(fakePurchaseRepository),
            ObserveSuppliersUseCase(fakeSupplierRepository),
            fakeRestaurantRepository
        )

        viewModel.uiState.test {
            awaitItem() // Initial
            viewModel.onSearchQueryChanged("INV-2")
            assertThat(awaitItem().searchQuery).isEqualTo("INV-2")
        }
    }
}
