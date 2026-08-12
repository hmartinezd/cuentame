package com.miara.cuentame.feature.reorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.repository.ActiveRestaurantProvider
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.service.ReorderCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class ReorderViewModel @Inject constructor(
    activeRestaurant: ActiveRestaurantProvider,
    ingredientDao: IngredientDao,
    optionDao: IngredientUnitOptionDao,
    projectionDao: InventoryProjectionDao,
    areaDao: InventoryAreaDao,
    mappingDao: SupplierItemMappingDao,
    supplierRepository: SupplierRepository,
    unitRepository: UnitRepository
) : ViewModel() {
    private data class RoomSources(
        val ingredients: List<com.miara.cuentame.core.database.entity.IngredientEntity>,
        val options: List<com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity>,
        val balances: List<com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity>,
        val areas: List<com.miara.cuentame.core.database.entity.InventoryAreaEntity>,
        val mappings: List<com.miara.cuentame.core.database.entity.SupplierItemMappingEntity>
    )
    private val filter = MutableStateFlow(ReorderFilter.NEEDS_REORDER)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val source = activeRestaurant.observeActiveRestaurant().filterNotNull().flatMapLatest { restaurant ->
        val id = restaurant.id
        val roomSources = combine(
            ingredientDao.observeActiveIngredients(id), optionDao.observeAllForRestaurant(id),
            projectionDao.observeBalancesForRestaurant(id), areaDao.observeActiveAreas(id),
            mappingDao.observeAllMappings(id)
        ) { ingredients, options, balances, areas, mappings -> RoomSources(ingredients, options, balances, areas, mappings) }
        combine(roomSources, supplierRepository.observeSuppliers(com.miara.cuentame.core.common.ids.RestaurantId(id), false), unitRepository.observeAll()) { room, suppliers, units ->
            val ingredients = room.ingredients
            val options = room.options
            val balances = room.balances
            val areas = room.areas
            val mappings = room.mappings
            val activeAreaIds = areas.map { it.id }.toSet()
            val supplierById = suppliers.associateBy { it.id.value }
            val unitById = units.associateBy { it.id.value }
            ingredients.map { ingredient ->
                val current = balances.asSequence().filter { it.ingredientId == ingredient.id && it.areaId in activeAreaIds }
                    .fold(BigDecimal.ZERO) { total, row -> total + BigDecimal(row.quantityBase) }
                val purchase = options.singleOrNull { it.ingredientId == ingredient.id && it.isDefaultPurchase && it.isActive && it.deletedAt == null }
                val ingredientMappings = mappings.filter { it.ingredientId == ingredient.id && supplierById.containsKey(it.supplierId) }
                val supplierIds = ingredientMappings.map { it.supplierId }.distinct()
                val ambiguous = supplierIds.size > 1
                val supplier = supplierIds.singleOrNull()?.let(supplierById::get)
                val mapping = supplier?.let { s -> ingredientMappings.filter { it.supplierId == s.id.value }.maxWithOrNull(compareBy({ it.lastConfirmedAt }, { it.id })) }
                val calculation = ReorderCalculator.calculate(current, ingredient.parLevelBase, ingredient.reorderPointBase, purchase?.factorToBase, supplier != null, ambiguous)
                ReorderItem(IngredientId(ingredient.id), ingredient.name, unitById[ingredient.baseUnitId]?.symbol ?: ingredient.baseUnitId,
                    current, ingredient.parLevelBase, ingredient.reorderPointBase, calculation.quantityNeededBase,
                    purchase?.displayName, purchase?.factorToBase, calculation.purchaseUnitsSuggested, calculation.suggestedPurchaseQuantityBase,
                    supplier?.name, mapping?.sourceDescription, mapping?.sourceVendorCode, calculation.needsReorder, calculation.status)
            }.sortedWith(compareBy({ it.supplierName ?: "" }, { it.ingredientName }, { it.ingredientId.value }))
        }
    }

    val uiState: StateFlow<ReorderUiState> = combine(source, filter) { items, selected -> ReorderUiState(false, items, selected) }
        .catch { emit(ReorderUiState(isLoading = false, error = it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReorderUiState())

    fun setFilter(value: ReorderFilter) { filter.value = value }
}
