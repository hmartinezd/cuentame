package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.CreatePurchaseDraftCommand
import com.miara.cuentame.core.domain.repository.PurchaseDetails
import com.miara.cuentame.core.domain.repository.PurchaseFilter
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.domain.repository.PurchaseSummary
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.SavePurchaseLineCommand
import com.miara.cuentame.core.domain.repository.UpdatePurchaseDraftCommand
import com.miara.cuentame.core.domain.usecase.ObservePurchaseDetailsUseCase
import com.miara.cuentame.core.domain.usecase.VoidPurchaseUseCase
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.purchase.PurchaseReceipt
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
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val detailsFlow = MutableStateFlow<PurchaseDetails?>(null)
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)

    private val fakePurchaseRepository = object : PurchaseRepository {
        override fun observePurchases(filter: PurchaseFilter): Flow<List<PurchaseSummary>> = flowOf(emptyList())
        override fun observePurchase(id: PurchaseReceiptId): Flow<PurchaseDetails?> = detailsFlow
        override suspend fun getReceipt(id: PurchaseReceiptId): PurchaseReceipt? = detailsFlow.value?.receipt
        override suspend fun createDraft(command: CreatePurchaseDraftCommand): PurchaseReceiptId = PurchaseReceiptId("")
        override suspend fun updateDraft(command: UpdatePurchaseDraftCommand) {}
        override suspend fun saveLine(command: SavePurchaseLineCommand): com.miara.cuentame.core.common.ids.PurchaseLineId = com.miara.cuentame.core.common.ids.PurchaseLineId("")
        override suspend fun deleteLine(receiptId: PurchaseReceiptId, lineId: com.miara.cuentame.core.common.ids.PurchaseLineId) {}
        override suspend fun deleteDraft(id: PurchaseReceiptId) {}
        override suspend fun post(id: PurchaseReceiptId) {}
        override suspend fun void(id: PurchaseReceiptId) {
             val current = detailsFlow.value ?: return
             detailsFlow.value = current.copy(receipt = current.receipt.copy(status = DocumentStatus.VOIDED))
        }
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant(): Restaurant? = restaurantFlow.value
        override suspend fun save(restaurant: Restaurant) {}
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
    fun `void purchase success updates state to VOIDED`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, Instant.now(), DocumentStatus.POSTED, null, null, Instant.now(), Instant.now())
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())
        
        val viewModel = createViewModel("p1")
        
        viewModel.uiState.test {
            // Loading or initial emission
            var latest = awaitItem()
            while (latest.state !is PurchaseDetailState.Ready) {
                latest = awaitItem()
            }
            
            viewModel.onVoid()
            
            // Wait for VOIDED
            while ((latest.state as? PurchaseDetailState.Ready)?.details?.receipt?.status != DocumentStatus.VOIDED) {
                latest = awaitItem()
            }
            assertThat((latest.state as PurchaseDetailState.Ready).details.receipt.status).isEqualTo(DocumentStatus.VOIDED)
        }
    }

    private fun createViewModel(purchaseId: String?): PurchaseDetailViewModel {
        return PurchaseDetailViewModel(
            SavedStateHandle(if (purchaseId != null) mapOf("purchaseId" to purchaseId) else emptyMap()),
            ObservePurchaseDetailsUseCase(fakePurchaseRepository),
            VoidPurchaseUseCase(fakePurchaseRepository),
            fakeRestaurantRepository
        )
    }
}
