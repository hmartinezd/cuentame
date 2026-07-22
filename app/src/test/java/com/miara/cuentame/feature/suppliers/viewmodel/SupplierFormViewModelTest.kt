package com.miara.cuentame.feature.suppliers.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.CreateSupplierCommand
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UpdateSupplierCommand
import com.miara.cuentame.core.domain.usecase.ArchiveSupplierUseCase
import com.miara.cuentame.core.domain.usecase.CreateSupplierUseCase
import com.miara.cuentame.core.domain.usecase.GetSupplierUseCase
import com.miara.cuentame.core.domain.usecase.UpdateSupplierUseCase
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
class SupplierFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val supplierFlow = MutableStateFlow<Supplier?>(null)
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)

    private val fakeSupplierRepository = object : SupplierRepository {
        override fun observeSuppliers(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Supplier>> = MutableStateFlow(emptyList())
        override fun observeSupplier(id: SupplierId): Flow<Supplier?> = supplierFlow
        override suspend fun getSupplier(id: SupplierId): Supplier? = supplierFlow.value
        override suspend fun createSupplier(command: CreateSupplierCommand): SupplierId = SupplierId("new_id")
        override suspend fun updateSupplier(command: UpdateSupplierCommand) {}
        override suspend fun archiveSupplier(id: SupplierId, at: Instant) {}
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
    fun `create supplier success emits event`() = runTest {
        val viewModel = createViewModel(null)
        viewModel.onNameChanged("New Supplier")
        
        viewModel.events.test {
            viewModel.onSave()
            assertThat(awaitItem()).isInstanceOf(SupplierFormEvent.Success::class.java)
        }
    }

    @Test
    fun `load supplier sets initial state`() = runTest {
        val supplier = Supplier(SupplierId("s1"), RestaurantId("r1"), "Original", "original", null, null, null, true, Instant.now(), Instant.now())
        supplierFlow.value = supplier
        
        val viewModel = createViewModel("s1")
        runCurrent()
        
        assertThat(viewModel.uiState.value.name).isEqualTo("Original")
    }

    private fun createViewModel(supplierId: String?): SupplierFormViewModel {
        return SupplierFormViewModel(
            SavedStateHandle(if (supplierId != null) mapOf("supplierId" to supplierId) else emptyMap()),
            GetSupplierUseCase(fakeSupplierRepository),
            CreateSupplierUseCase(fakeSupplierRepository),
            UpdateSupplierUseCase(fakeSupplierRepository),
            ArchiveSupplierUseCase(fakeSupplierRepository),
            fakeRestaurantRepository,
            timeProvider
        )
    }
}
