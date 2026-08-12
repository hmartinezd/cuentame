package com.miara.cuentame.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.menu.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class MenuOperationError { NAME_REQUIRED, PRICE_MALFORMED, PRICE_NEGATIVE, DUPLICATE_NAME, DUPLICATE_COMPONENT, INVALID_QUANTITY, UNIT_REQUIRED, UNIT_MISMATCH, UNIT_INACTIVE, OWNERSHIP, SAVE_FAILED }

private fun Throwable.presentationError() = when (this) {
    is MenuRecipeValidationException.InvalidName -> MenuOperationError.NAME_REQUIRED
    is MenuRecipeValidationException.InvalidSellingPrice -> MenuOperationError.PRICE_NEGATIVE
    is MenuRecipeValidationException.DuplicateName -> MenuOperationError.DUPLICATE_NAME
    is MenuRecipeValidationException.DuplicateComponent -> MenuOperationError.DUPLICATE_COMPONENT
    is MenuRecipeValidationException.InvalidQuantity -> MenuOperationError.INVALID_QUANTITY
    is MenuRecipeValidationException.UnitOptionMismatch -> MenuOperationError.UNIT_MISMATCH
    is MenuRecipeValidationException.InactiveUnitOption -> MenuOperationError.UNIT_INACTIVE
    is MenuRecipeValidationException.OwnershipMismatch -> MenuOperationError.OWNERSHIP
    else -> MenuOperationError.SAVE_FAILED
}

private fun parsePrice(text: String): Pair<BigDecimal?, MenuOperationError?> {
    if (text.isBlank()) return null to null
    val value = text.trim().toBigDecimalOrNull() ?: return null to MenuOperationError.PRICE_MALFORMED
    return if (value < BigDecimal.ZERO) null to MenuOperationError.PRICE_NEGATIVE else value to null
}

internal fun deterministicMenuUnitDefault(options: List<IngredientUnitOption>): IngredientUnitOptionId? {
    val active = options.filter { it.isActive && it.deletedAt == null }
    return active.singleOrNull { it.isDefaultCount }?.id
        ?: active.singleOrNull { it.isBase }?.id
        ?: active.singleOrNull()?.id
}

data class MenuListState(
    val loading: Boolean = true,
    val rows: List<Pair<MenuRecipe, MenuRecipeCost?>> = emptyList(),
    val error: Boolean = false,
    val includeArchived: Boolean = false,
    val isSaving: Boolean = false,
    val operationError: MenuOperationError? = null,
    val createdId: MenuRecipeId? = null
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MenuListViewModel @Inject constructor(
    private val recipes: MenuRecipeRepository,
    private val costs: MenuCostRepository,
    private val restaurants: RestaurantRepository
) : ViewModel() {
    private val archived = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)
    private val operation = MutableStateFlow(MenuListState(loading = false))
    private val content = combine(restaurants.observeRestaurant(), archived, retry) { r, a, _ -> r to a }.flatMapLatest { (r, a) ->
        if (r == null) flowOf(MenuListState(false, error = true)) else combine(recipes.observeRecipes(r.id, a), costs.observeCosts(r.id, a)) { rs, cs ->
            MenuListState(false, rs.map { it to cs.firstOrNull { c -> c.menuRecipeId == it.id } }, includeArchived = a)
        }.catch { emit(MenuListState(false, error = true, includeArchived = a)) }.onStart { emit(MenuListState(includeArchived = a)) }
    }
    val state = combine(content, operation) { c, o -> c.copy(isSaving = o.isSaving, operationError = o.operationError, createdId = o.createdId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MenuListState())

    fun create(name: String, priceText: String) {
        if (operation.value.isSaving) return
        val (price, parseError) = parsePrice(priceText)
        if (parseError != null) { operation.value = operation.value.copy(operationError = parseError); return }
        viewModelScope.launch {
            operation.value = operation.value.copy(isSaving = true, operationError = null, createdId = null)
            try {
                val restaurant = restaurants.getRestaurant() ?: throw MenuRecipeValidationException.OwnershipMismatch()
                val created = recipes.create(restaurant.id, name, price, null)
                operation.value = operation.value.copy(isSaving = false, createdId = created)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { operation.value = operation.value.copy(isSaving = false, operationError = e.presentationError()) }
        }
    }
    fun consumeCreated() { operation.value = operation.value.copy(createdId = null) }
    fun clearOperationError() { operation.value = operation.value.copy(operationError = null) }
    fun toggleArchived() { archived.value = !archived.value }
    fun retry() { retry.value++ }
}

data class ComponentEditorState(
    val isOpen: Boolean = false,
    val existing: MenuRecipeComponent? = null,
    val selectedIngredientId: IngredientId? = null,
    val availableUnitOptions: List<IngredientUnitOption> = emptyList(),
    val selectedUnitOptionId: IngredientUnitOptionId? = null,
    val quantity: String = "",
    val isLoadingUnits: Boolean = false,
    val isSaving: Boolean = false,
    val error: MenuOperationError? = null
)

data class MenuDetailState(
    val loading: Boolean = true,
    val recipe: MenuRecipe? = null,
    val cost: MenuRecipeCost? = null,
    val components: List<MenuRecipeComponent> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val error: Boolean = false,
    val editor: ComponentEditorState = ComponentEditorState(),
    val isSavingInfo: Boolean = false,
    val isArchiving: Boolean = false,
    val isRemovingComponent: Boolean = false,
    val infoError: MenuOperationError? = null,
    val infoSaveSucceeded: Boolean = false
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MenuDetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val recipes: MenuRecipeRepository,
    private val costs: MenuCostRepository,
    private val ingredientRepository: IngredientRepository
) : ViewModel() {
    private val id = MenuRecipeId(requireNotNull(saved["menuRecipeId"]))
    private val editor = MutableStateFlow(ComponentEditorState())
    private val operations = MutableStateFlow(MenuDetailState(loading = false))
    private val content = recipes.observeRecipe(id).flatMapLatest { recipe ->
        if (recipe == null) flowOf(MenuDetailState(false, error = true)) else combine(costs.observeCost(id), recipes.observeComponents(id), ingredientRepository.observeIngredients(recipe.restaurantId, false)) { cost, components, ingredients ->
            MenuDetailState(false, recipe, cost, components, ingredients, cost == null)
        }
    }.catch { emit(MenuDetailState(false, error = true)) }
    val state = combine(content, editor, operations) { c, e, o -> c.copy(editor = e, isSavingInfo = o.isSavingInfo, isArchiving = o.isArchiving, isRemovingComponent = o.isRemovingComponent, infoError = o.infoError, infoSaveSucceeded = o.infoSaveSucceeded) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MenuDetailState())

    fun openComponent(existing: MenuRecipeComponent?) {
        val ingredientId = existing?.ingredientId
        editor.value = ComponentEditorState(isOpen = true, existing = existing, selectedIngredientId = ingredientId, selectedUnitOptionId = existing?.ingredientUnitOptionId, quantity = existing?.quantityEntered?.toPlainString().orEmpty())
        if (ingredientId != null) loadUnits(ingredientId, existing.ingredientUnitOptionId)
    }

    fun dismissComponent() { if (!editor.value.isSaving && !operations.value.isRemovingComponent) editor.value = ComponentEditorState() }

    fun selectIngredient(ingredientId: IngredientId) {
        if (editor.value.selectedIngredientId == ingredientId) return
        editor.value = editor.value.copy(selectedIngredientId = ingredientId, availableUnitOptions = emptyList(), selectedUnitOptionId = null, error = null)
        loadUnits(ingredientId, null)
    }

    private fun loadUnits(ingredientId: IngredientId, persistedSelection: IngredientUnitOptionId?) = viewModelScope.launch {
        editor.value = editor.value.copy(isLoadingUnits = true)
        try {
            val options = ingredientRepository.getUnitOptions(ingredientId, includeArchived = false).filter { it.isActive && it.deletedAt == null }
            if (editor.value.selectedIngredientId != ingredientId) return@launch
            val selected = persistedSelection?.takeIf { id -> options.any { it.id == id } } ?: deterministicMenuUnitDefault(options)
            editor.value = editor.value.copy(availableUnitOptions = options, selectedUnitOptionId = selected, isLoadingUnits = false)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { if (editor.value.selectedIngredientId == ingredientId) editor.value = editor.value.copy(isLoadingUnits = false, error = MenuOperationError.SAVE_FAILED) }
    }

    fun selectUnit(optionId: IngredientUnitOptionId) {
        if (editor.value.availableUnitOptions.any { it.id == optionId }) editor.value = editor.value.copy(selectedUnitOptionId = optionId, error = null)
    }
    fun updateComponentQuantity(value: String) { editor.value = editor.value.copy(quantity = value, error = null) }

    fun saveComponent() {
        val current = editor.value
        if (current.isSaving) return
        val ingredientId = current.selectedIngredientId ?: return
        val optionId = current.selectedUnitOptionId
        if (optionId == null) { editor.value = current.copy(error = MenuOperationError.UNIT_REQUIRED); return }
        val quantity = current.quantity.toBigDecimalOrNull()
        if (quantity == null || quantity <= BigDecimal.ZERO) { editor.value = current.copy(error = MenuOperationError.INVALID_QUANTITY); return }
        viewModelScope.launch {
            editor.value = editor.value.copy(isSaving = true, error = null)
            try {
                recipes.saveComponent(id, current.existing?.id, ingredientId, optionId, quantity, current.existing?.sortOrder ?: state.value.components.size)
                editor.value = ComponentEditorState()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { editor.value = editor.value.copy(isSaving = false, error = e.presentationError()) }
        }
    }

    fun save(name: String, priceText: String) {
        if (operations.value.isSavingInfo) return
        val (price, parseError) = parsePrice(priceText)
        if (parseError != null) { operations.value = operations.value.copy(infoError = parseError); return }
        val recipe = state.value.recipe ?: return
        viewModelScope.launch {
            operations.value = operations.value.copy(isSavingInfo = true, infoError = null, infoSaveSucceeded = false)
            try { recipes.update(id, name, price, recipe.notes); operations.value = operations.value.copy(isSavingInfo = false, infoSaveSucceeded = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { operations.value = operations.value.copy(isSavingInfo = false, infoError = e.presentationError()) }
        }
    }
    fun consumeInfoSaveSuccess() { operations.value = operations.value.copy(infoSaveSucceeded = false) }
    fun clearInfoError() { operations.value = operations.value.copy(infoError = null) }

    fun archive() {
        if (operations.value.isArchiving) return
        val recipe = state.value.recipe ?: return
        viewModelScope.launch {
            operations.value = operations.value.copy(isArchiving = true, infoError = null)
            try { recipes.setArchived(id, recipe.archivedAt == null); operations.value = operations.value.copy(isArchiving = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { operations.value = operations.value.copy(isArchiving = false, infoError = e.presentationError()) }
        }
    }

    fun removeComponent() {
        if (operations.value.isRemovingComponent) return
        val component = editor.value.existing ?: return
        viewModelScope.launch {
            operations.value = operations.value.copy(isRemovingComponent = true)
            try { recipes.removeComponent(id, component.id); operations.value = operations.value.copy(isRemovingComponent = false); editor.value = ComponentEditorState()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { operations.value = operations.value.copy(isRemovingComponent = false); editor.value = editor.value.copy(error = MenuOperationError.SAVE_FAILED) }
        }
    }
}
