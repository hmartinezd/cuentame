package com.venkoi.cuentame.feature.reorder

import app.cash.turbine.test
import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.SupplierId
import com.venkoi.cuentame.core.common.ids.UnitId
import com.venkoi.cuentame.core.database.dao.IngredientDao
import com.venkoi.cuentame.core.database.dao.IngredientUnitOptionDao
import com.venkoi.cuentame.core.database.dao.InventoryAreaDao
import com.venkoi.cuentame.core.database.dao.InventoryProjectionDao
import com.venkoi.cuentame.core.database.dao.SupplierItemMappingDao
import com.venkoi.cuentame.core.database.entity.IngredientEntity
import com.venkoi.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.venkoi.cuentame.core.database.entity.InventoryAreaEntity
import com.venkoi.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.venkoi.cuentame.core.database.entity.RestaurantEntity
import com.venkoi.cuentame.core.database.entity.SupplierItemMappingEntity
import com.venkoi.cuentame.core.database.repository.ActiveRestaurantProvider
import com.venkoi.cuentame.core.domain.repository.SupplierRepository
import com.venkoi.cuentame.core.domain.repository.UnitRepository
import com.venkoi.cuentame.core.domain.service.ReorderConfigurationStatus
import com.venkoi.cuentame.core.model.inventory.UnitDimension
import com.venkoi.cuentame.core.model.inventory.UnitOfMeasure
import com.venkoi.cuentame.core.model.supplier.Supplier
import com.venkoi.cuentame.core.model.supplier.SupplierItemMappingKeyType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class ReorderViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val ingredients = MutableStateFlow<List<IngredientEntity>>(emptyList())
    private val options = MutableStateFlow<List<IngredientUnitOptionEntity>>(emptyList())
    private val balances = MutableStateFlow<List<InventoryBalanceProjectionEntity>>(emptyList())
    private val areas = MutableStateFlow<List<InventoryAreaEntity>>(emptyList())
    private val mappings = MutableStateFlow<List<SupplierItemMappingEntity>>(emptyList())
    private val suppliers = MutableStateFlow<List<Supplier>>(emptyList())

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `reactive recommendations aggregate active areas and preserve restaurant supplier ownership`() = runTest {
        val viewModel = viewModel()
        val chicken = ingredient("chicken-a", "restaurant-a", "Chicken", par = "20")
        ingredients.value = listOf(chicken)
        options.value = listOf(option("purchase-a", chicken.id, "5"))
        areas.value = listOf(
            area("walk-in", "restaurant-a", active = true),
            area("prep", "restaurant-a", active = true),
            area("bar", "restaurant-a", active = true)
        )
        balances.value = listOf(
            balance("restaurant-a", chicken.id, "walk-in", "8"),
            balance("restaurant-a", chicken.id, "prep", "3"),
            balance("restaurant-a", chicken.id, "bar", "1"),
            balance("restaurant-a", chicken.id, "archived", "100")
        )
        suppliers.value = listOf(supplier("supplier-a", "restaurant-a", "Sysco"))
        mappings.value = listOf(mapping("mapping-a", "restaurant-a", "supplier-a", chicken.id, "CH-1"))

        viewModel.uiState.test {
            val state = awaitItemMatching { !it.isLoading && it.items.isNotEmpty() }
            val item = state.items.single()
            assertThat(item.currentBase.compareTo(BigDecimal("12"))).isEqualTo(0)
            assertThat(item.neededBase?.compareTo(BigDecimal("8"))).isEqualTo(0)
            assertThat(item.purchaseUnits?.compareTo(BigDecimal("2"))).isEqualTo(0)
            assertThat(item.supplierName).isEqualTo("Sysco")
            assertThat(item.supplierSku).isEqualTo("CH-1")

            balances.value = balances.value.map { if (it.areaId == "walk-in") it.copy(quantityBase = "18") else it }
            val replenished = awaitItemMatching { !it.isLoading && it.items.single().currentBase.compareTo(BigDecimal("22")) == 0 }
            assertThat(replenished.visibleItems).isEmpty()

            ingredients.value = listOf(chicken.copy(parLevelBase = BigDecimal("30")))
            val reconfigured = awaitItemMatching { it.items.singleOrNull()?.parBase?.compareTo(BigDecimal("30")) == 0 }
            assertThat(reconfigured.visibleItems.single().neededBase?.compareTo(BigDecimal("8"))).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters expose missing setup independently and supplier ambiguity is not preferred`() = runTest {
        val viewModel = viewModel()
        val basil = ingredient("basil", "restaurant-a", "Basil", par = null)
        val chicken = ingredient("chicken", "restaurant-a", "Chicken", par = "10")
        ingredients.value = listOf(basil, chicken)
        areas.value = listOf(area("walk-in", "restaurant-a", active = true))
        balances.value = listOf(balance("restaurant-a", chicken.id, "walk-in", "5"))
        suppliers.value = listOf(
            supplier("supplier-1", "restaurant-a", "Supplier One"),
            supplier("supplier-2", "restaurant-a", "Supplier Two")
        )
        mappings.value = listOf(
            mapping("mapping-1", "restaurant-a", "supplier-1", chicken.id, "ONE"),
            mapping("mapping-2", "restaurant-a", "supplier-2", chicken.id, "TWO")
        )

        viewModel.uiState.test {
            val initial = awaitItemMatching { !it.isLoading && it.items.size == 2 }
            val ambiguous = initial.items.single { it.ingredientName == "Chicken" }
            assertThat(ambiguous.supplierName).isNull()
            assertThat(ambiguous.configurationIssues).containsExactly(
                ReorderConfigurationStatus.MISSING_PURCHASE_UNIT,
                ReorderConfigurationStatus.AMBIGUOUS_SUPPLIER
            )

            viewModel.setFilter(ReorderFilter.MISSING_SETUP)
            val missing = awaitItemMatching { it.filter == ReorderFilter.MISSING_SETUP }
            assertThat(missing.visibleItems.map { it.ingredientName }).containsExactly("Basil", "Chicken")

            viewModel.setFilter(ReorderFilter.ALL_CONFIGURED)
            val configured = awaitItemMatching { it.filter == ReorderFilter.ALL_CONFIGURED }
            assertThat(configured.visibleItems.map { it.ingredientName }).containsExactly("Chicken")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(): ReorderViewModel {
        val activeRestaurant = mockk<ActiveRestaurantProvider>()
        val ingredientDao = mockk<IngredientDao>()
        val optionDao = mockk<IngredientUnitOptionDao>()
        val projectionDao = mockk<InventoryProjectionDao>()
        val areaDao = mockk<InventoryAreaDao>()
        val mappingDao = mockk<SupplierItemMappingDao>()
        val supplierRepository = mockk<SupplierRepository>()
        val unitRepository = mockk<UnitRepository>()
        every { activeRestaurant.observeActiveRestaurant() } returns MutableStateFlow(RestaurantEntity("restaurant-a", "A", "USD", "en-US", 0, 0, null))
        every { ingredientDao.observeActiveIngredients("restaurant-a") } returns ingredients
        every { optionDao.observeAllForRestaurant("restaurant-a") } returns options
        every { projectionDao.observeBalancesForRestaurant("restaurant-a") } returns balances
        every { areaDao.observeActiveAreas("restaurant-a") } returns areas
        every { mappingDao.observeAllMappings("restaurant-a") } returns mappings
        every { supplierRepository.observeSuppliers(RestaurantId("restaurant-a"), false) } returns suppliers
        every { unitRepository.observeAll() } returns MutableStateFlow(listOf(UnitOfMeasure(UnitId("lb"), "Pound", "lb", UnitDimension.MASS, BigDecimal.ONE, true, 0)))
        return ReorderViewModel(activeRestaurant, ingredientDao, optionDao, projectionDao, areaDao, mappingDao, supplierRepository, unitRepository)
    }

    private suspend fun ReceiveTurbine<ReorderUiState>.awaitItemMatching(
        predicate: (ReorderUiState) -> Boolean
    ): ReorderUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun ingredient(id: String, restaurantId: String, name: String, par: String?) = IngredientEntity(
        id, restaurantId, name, name.lowercase(), null, "lb", null, null, null, null,
        true, 0, 0, null, par?.let(::BigDecimal)
    )

    private fun option(id: String, ingredientId: String, factor: String) = IngredientUnitOptionEntity(
        id, ingredientId, "case", "case", null, BigDecimal(factor), false, false, true, true, 0, 0, null
    )

    private fun area(id: String, restaurantId: String, active: Boolean) = InventoryAreaEntity(
        id, restaurantId, id, id, 0, active, 0, 0, if (active) null else 1
    )

    private fun balance(restaurantId: String, ingredientId: String, areaId: String, quantity: String) =
        InventoryBalanceProjectionEntity(restaurantId, ingredientId, areaId, quantity, 0)

    private fun supplier(id: String, restaurantId: String, name: String) = Supplier(
        SupplierId(id), RestaurantId(restaurantId), name, name.lowercase(), isActive = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )

    private fun mapping(id: String, restaurantId: String, supplierId: String, ingredientId: String, sku: String) =
        SupplierItemMappingEntity(
            id, restaurantId, supplierId, SupplierItemMappingKeyType.VENDOR_CODE, sku.lowercase(), sku,
            "Item", null, ingredientId, null, null, 0, 0, 0
        )
}
