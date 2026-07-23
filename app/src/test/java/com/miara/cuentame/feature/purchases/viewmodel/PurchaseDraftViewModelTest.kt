package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.CreatePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseDraftUseCase
import com.miara.cuentame.core.domain.usecase.DeletePurchaseLineUseCase
import com.miara.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.miara.cuentame.core.domain.usecase.ObserveSuppliersUseCase
import com.miara.cuentame.core.domain.usecase.PostPurchaseUseCase
import com.miara.cuentame.core.domain.usecase.UpdatePurchaseDraftUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.model.supplier.Supplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseDraftViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val detailsFlow = MutableStateFlow<PurchaseDetails?>(null)
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)

    private val fakePurchaseRepository = object : PurchaseRepository {
        override fun observePurchases(filter: PurchaseFilter): Flow<List<PurchaseSummary>> = MutableStateFlow(emptyList())
        override fun observePurchase(id: PurchaseReceiptId): Flow<PurchaseDetails?> = detailsFlow
        override suspend fun getReceipt(id: PurchaseReceiptId): PurchaseReceipt? = detailsFlow.value?.receipt
        override suspend fun createDraft(command: CreatePurchaseDraftCommand): PurchaseReceiptId = PurchaseReceiptId("new_rid")
        override suspend fun updateDraft(command: UpdatePurchaseDraftCommand) {}
        override suspend fun saveLine(command: SavePurchaseLineCommand): PurchaseLineId = PurchaseLineId("new_lid")
        override suspend fun deleteLine(receiptId: PurchaseReceiptId, lineId: PurchaseLineId) {}
        override suspend fun deleteDraft(id: PurchaseReceiptId) {}
        override suspend fun post(id: PurchaseReceiptId) {}
        override suspend fun void(id: PurchaseReceiptId) {}
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant(): Restaurant? = restaurantFlow.value
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-01-01T00:00:00Z")
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
    fun `post purchase success emits event`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, Instant.now(), DocumentStatus.DRAFT, null, null, Instant.now(), Instant.now())
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())
        
        val viewModel = createViewModel("p1")
        runCurrent()
        
        viewModel.events.test {
            viewModel.onPost()
            assertThat(awaitItem()).isEqualTo(PurchaseDraftEvent.Posted)
        }
    }

    @Test
    fun `delete line success emits event`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, Instant.now(), DocumentStatus.DRAFT, null, null, Instant.now(), Instant.now())
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())
        
        val viewModel = createViewModel("p1")
        runCurrent()
        
        val lineId = PurchaseLineId("l1")
        viewModel.events.test {
            viewModel.onDeleteLine(lineId)
            assertThat(awaitItem()).isEqualTo(PurchaseDraftEvent.LineDeleted(lineId))
        }
    }

    private fun createViewModel(purchaseId: String?): PurchaseDraftViewModel {
        return PurchaseDraftViewModel(
            SavedStateHandle(if (purchaseId != null) mapOf("purchaseId" to purchaseId) else emptyMap()),
            CreatePurchaseDraftUseCase(fakePurchaseRepository),
            UpdatePurchaseDraftUseCase(fakePurchaseRepository),
            DeletePurchaseDraftUseCase(fakePurchaseRepository),
            PostPurchaseUseCase(fakePurchaseRepository),
            DeletePurchaseLineUseCase(fakePurchaseRepository),
            ObservePurchaseDetailsUseCase(fakePurchaseRepository),
            ObserveSuppliersUseCase(object : com.miara.cuentame.core.domain.repository.SupplierRepository {
                override fun observeSuppliers(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Supplier>> = MutableStateFlow(emptyList())
                override fun observeSupplier(id: SupplierId): Flow<Supplier?> = MutableStateFlow(null)
                override suspend fun getSupplier(id: SupplierId): Supplier? = null
                override suspend fun createSupplier(command: com.miara.cuentame.core.domain.repository.CreateSupplierCommand): SupplierId = SupplierId("")
                override suspend fun updateSupplier(command: com.miara.cuentame.core.domain.repository.UpdateSupplierCommand) {}
                override suspend fun archiveSupplier(id: SupplierId, at: Instant) {}
            }),
            fakeRestaurantRepository
        )
    }
}
