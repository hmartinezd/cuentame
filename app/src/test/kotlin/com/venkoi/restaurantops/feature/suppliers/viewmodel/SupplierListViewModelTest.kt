package com.venkoi.restaurantops.feature.suppliers.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.common.ids.SupplierId
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.domain.usecase.ObserveSuppliersUseCase
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import com.venkoi.restaurantops.core.model.supplier.Supplier
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
class SupplierListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val suppliersFlow = MutableStateFlow<List<Supplier>>(emptyList())
    private val restaurantFlow = MutableStateFlow<Restaurant?>(null)

    private val fakeSupplierRepository = object : com.venkoi.restaurantops.core.domain.repository.SupplierRepository {
        override fun observeSuppliers(restaurantId: RestaurantId, includeArchived: Boolean): Flow<List<Supplier>> = suppliersFlow
        override fun observeSupplier(id: SupplierId): Flow<Supplier?> = flowOf(null)
        override suspend fun getSupplier(id: SupplierId): Supplier? = null
        override suspend fun createSupplier(command: com.venkoi.restaurantops.core.domain.repository.CreateSupplierCommand): SupplierId = SupplierId("")
        override suspend fun updateSupplier(command: com.venkoi.restaurantops.core.domain.repository.UpdateSupplierCommand) {}
        override suspend fun archiveSupplier(id: SupplierId, at: Instant) {}
        override suspend fun searchSuppliers(restaurantId: RestaurantId, query: String): List<Supplier> = emptyList()
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
    fun `list updates when repository emits new suppliers`() = runTest {
        val viewModel = SupplierListViewModel(
            ObserveSuppliersUseCase(fakeSupplierRepository),
            fakeRestaurantRepository
        )
        
        val supplier = Supplier(SupplierId("s1"), RestaurantId("r1"), "Supplier 1", "supplier 1", null, null, null, true, Instant.now(), Instant.now())
        
        viewModel.uiState.test {
            suppliersFlow.value = listOf(supplier)
            
            var latest = awaitItem()
            while (latest.suppliers.isEmpty()) {
                latest = awaitItem()
            }
            assertThat(latest.suppliers).containsExactly(supplier)
        }
    }
}
