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
import io.mockk.mockk
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
    private var voidCount = 0

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
             voidCount++
             val current = detailsFlow.value ?: return
             detailsFlow.value = current.copy(receipt = current.receipt.copy(status = DocumentStatus.VOIDED))
        }
        override suspend fun attachDocument(receiptId: PurchaseReceiptId, storedLocation: String, displayName: String) {}
        override suspend fun removeDocument(receiptId: PurchaseReceiptId) {}
    }

    private val fakeRestaurantRepository = object : RestaurantRepository {
        override fun observeRestaurant(): Flow<Restaurant?> = restaurantFlow
        override suspend fun getRestaurant(): Restaurant? = restaurantFlow.value
        override suspend fun save(restaurant: Restaurant) {}
    }

    private val fixedNow = Instant.parse("2026-08-05T10:00:00Z")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        restaurantFlow.value = Restaurant(RestaurantId("r1"), "R1", "USD", "en-US", fixedNow, fixedNow)
        voidCount = 0
        detailsFlow.value = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `void purchase success updates state to VOIDED and calls repository once`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, fixedNow, DocumentStatus.POSTED, null, null, null, fixedNow, fixedNow)
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())
        
        val viewModel = createViewModel("p1")
        
        app.cash.turbine.turbineScope {
            val uiStateTurbine = viewModel.uiState.testIn(this)
            val eventsTurbine = viewModel.events.testIn(this)
            
            // Wait for ready state
            while (uiStateTurbine.awaitItem().state !is PurchaseDetailState.Ready) {
                // skip loading
            }
            
            viewModel.onVoid()
            assertThat(eventsTurbine.awaitItem()).isEqualTo(PurchaseDetailEvent.Voided)
            
            // Wait for the state to reflect VOIDED
            var stateItem = uiStateTurbine.awaitItem()
            while ((stateItem.state as? PurchaseDetailState.Ready)?.details?.receipt?.status != DocumentStatus.VOIDED) {
                stateItem = uiStateTurbine.awaitItem()
            }
            
            assertThat(voidCount).isEqualTo(1)
            uiStateTurbine.cancel()
            eventsTurbine.cancel()
        }
    }

    @Test
    fun `onVoid is ignored when status is VOIDED`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, fixedNow, DocumentStatus.VOIDED, null, null, null, fixedNow, fixedNow)
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())

        val viewModel = createViewModel("p1")

        viewModel.events.test {
            // Wait for ready state
            viewModel.uiState.test {
                while (awaitItem().state !is PurchaseDetailState.Ready) { }
            }

            viewModel.onVoid()
            runCurrent()

            expectNoEvents()
            val finalState = viewModel.uiState.value
            assertThat((finalState.state as PurchaseDetailState.Ready).details.receipt.status).isEqualTo(DocumentStatus.VOIDED)
            assertThat(finalState.isVoiding).isFalse()
            assertThat(finalState.error).isNull()
            assertThat(voidCount).isEqualTo(0)
        }
    }

    @Test
    fun `onVoid is ignored when status is UNKNOWN`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, fixedNow, DocumentStatus.UNKNOWN, null, null, null, fixedNow, fixedNow)
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())

        val viewModel = createViewModel("p1")

        viewModel.events.test {
            viewModel.uiState.test {
                while (awaitItem().state !is PurchaseDetailState.Ready) { }
            }

            viewModel.onVoid()
            runCurrent()

            expectNoEvents()
            val finalState = viewModel.uiState.value
            assertThat((finalState.state as PurchaseDetailState.Ready).details.receipt.status).isEqualTo(DocumentStatus.UNKNOWN)
            assertThat(finalState.isVoiding).isFalse()
            assertThat(finalState.error).isNull()
            assertThat(voidCount).isEqualTo(0)
        }
    }

    @Test
    fun `onVoid is ignored when status is DRAFT`() = runTest {
        val receipt = PurchaseReceipt(PurchaseReceiptId("p1"), RestaurantId("r1"), null, null, fixedNow, DocumentStatus.DRAFT, null, null, null, fixedNow, fixedNow)
        detailsFlow.value = PurchaseDetails(receipt, null, emptyList())

        val viewModel = createViewModel("p1")

        viewModel.events.test {
            viewModel.uiState.test {
                while (awaitItem().state !is PurchaseDetailState.Ready) { }
            }

            viewModel.onVoid()
            runCurrent()

            expectNoEvents()
            val finalState = viewModel.uiState.value
            assertThat((finalState.state as PurchaseDetailState.Ready).details.receipt.status).isEqualTo(DocumentStatus.DRAFT)
            assertThat(finalState.isVoiding).isFalse()
            assertThat(finalState.error).isNull()
            assertThat(voidCount).isEqualTo(0)
        }
    }

    private fun createViewModel(receiptId: String?): PurchaseDetailViewModel {
        return PurchaseDetailViewModel(
            SavedStateHandle(if (receiptId != null) mapOf("receiptId" to receiptId) else emptyMap()),
            ObservePurchaseDetailsUseCase(fakePurchaseRepository),
            VoidPurchaseUseCase(fakePurchaseRepository),
            mockk(relaxed = true),
            fakeRestaurantRepository
        )
    }
}
