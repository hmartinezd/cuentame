package com.venkoi.restaurantops.feature.menu.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.domain.repository.*
import com.venkoi.restaurantops.core.model.ingredient.Ingredient
import com.venkoi.restaurantops.core.model.menu.CashDiscountBehavior
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class DraftMenuComponent(
    val draftId: String,
    val ingredientId: IngredientId,
    val ingredientName: String,
    val unitOptionId: IngredientUnitOptionId,
    val unitLabel: String,
    val quantity: BigDecimal
)

data class CreateMenuItemState(
    val loading: Boolean = true,
    val menuName: String = "",
    val categoryName: String = "",
    val defaultDiscountPercent: BigDecimal = BigDecimal.ZERO,
    val ingredients: List<Ingredient> = emptyList(),
    val components: List<DraftMenuComponent> = emptyList(),
    val editor: ComponentEditorState = ComponentEditorState(),
    val editingComponentId: String? = null,
    val cashDiscountBehavior: CashDiscountBehavior = CashDiscountBehavior.APPLY_DEFAULT,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: MenuOperationError? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CreateMenuItemViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogs: MenuCatalogRepository,
    private val creation: MenuItemCreationRepository,
    private val ingredientsRepository: IngredientRepository
) : ViewModel() {
    private val menuId = MenuId(requireNotNull(savedStateHandle["menuId"]))
    private val categoryId = MenuCategoryId(requireNotNull(savedStateHandle["categoryId"]))
    private val draft = MutableStateFlow(CreateMenuItemState(loading = false))
    private val content = catalogs.observeMenu(menuId).flatMapLatest { menu ->
        if (menu == null) flowOf(CreateMenuItemState(loading = false, error = MenuOperationError.OWNERSHIP))
        else combine(catalogs.observeCategories(menuId), ingredientsRepository.observeIngredients(menu.restaurantId, false)) { categories, ingredients ->
            val category = categories.firstOrNull { it.id == categoryId }
            if (category == null) CreateMenuItemState(loading = false, error = MenuOperationError.OWNERSHIP)
            else CreateMenuItemState(false, menu.name, category.name, menu.defaultCashDiscountPercent, ingredients.filter { it.isActive && it.deletedAt == null })
        }
    }.catch { emit(CreateMenuItemState(loading = false, error = MenuOperationError.SAVE_FAILED)) }

    val state = combine(content, draft) { c, d -> c.copy(
        components = d.components, editor = d.editor, editingComponentId = d.editingComponentId, cashDiscountBehavior = d.cashDiscountBehavior,
        saving = d.saving, saved = d.saved, error = d.error ?: c.error,
    ) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CreateMenuItemState())

    fun setCashDiscountBehavior(value: CashDiscountBehavior) { draft.value = draft.value.copy(cashDiscountBehavior = value, error = null) }
    fun openComponent(component: DraftMenuComponent? = null) {
        draft.value = draft.value.copy(editingComponentId = component?.draftId, editor = ComponentEditorState(isOpen = true,
            existing = component?.let { com.venkoi.restaurantops.core.model.menu.MenuRecipeComponent(MenuRecipeComponentId(it.draftId), MenuRecipeId("draft"), it.ingredientId, it.unitOptionId, it.quantity, it.quantity, 0, java.time.Instant.EPOCH, java.time.Instant.EPOCH) },
            selectedIngredientId = component?.ingredientId, selectedUnitOptionId = component?.unitOptionId, quantity = component?.quantity?.toPlainString().orEmpty()))
        component?.let { loadUnits(it.ingredientId, it.unitOptionId) }
    }
    fun dismissComponent() { if (!draft.value.editor.isSaving) draft.value = draft.value.copy(editor = ComponentEditorState(), editingComponentId = null) }
    fun selectIngredient(id: IngredientId) {
        draft.value = draft.value.copy(editor = draft.value.editor.copy(selectedIngredientId = id, availableUnitOptions = emptyList(), selectedUnitOptionId = null, error = null))
        loadUnits(id, null)
    }
    private fun loadUnits(id: IngredientId, persisted: IngredientUnitOptionId?) = viewModelScope.launch {
        draft.value = draft.value.copy(editor = draft.value.editor.copy(isLoadingUnits = true))
        try {
            val options = ingredientsRepository.getUnitOptions(id, false).filter { it.isActive && it.deletedAt == null }
            if (draft.value.editor.selectedIngredientId != id) return@launch
            draft.value = draft.value.copy(editor = draft.value.editor.copy(availableUnitOptions = options, selectedUnitOptionId = persisted?.takeIf { p -> options.any { it.id == p } } ?: deterministicMenuUnitDefault(options), isLoadingUnits = false))
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            draft.value = draft.value.copy(editor = draft.value.editor.copy(isLoadingUnits = false, error = MenuOperationError.SAVE_FAILED))
        }
    }
    fun selectUnit(id: IngredientUnitOptionId) { if (draft.value.editor.availableUnitOptions.any { it.id == id }) draft.value = draft.value.copy(editor = draft.value.editor.copy(selectedUnitOptionId = id, error = null)) }
    fun updateQuantity(value: String) { draft.value = draft.value.copy(editor = draft.value.editor.copy(quantity = value, error = null)) }
    fun saveComponent() {
        val editor = draft.value.editor
        val ingredientId = editor.selectedIngredientId ?: return
        val optionId = editor.selectedUnitOptionId ?: run { draft.value = draft.value.copy(editor = editor.copy(error = MenuOperationError.UNIT_REQUIRED)); return }
        val quantity = editor.quantity.toBigDecimalOrNull()
        if (quantity == null || quantity <= BigDecimal.ZERO) { draft.value = draft.value.copy(editor = editor.copy(error = MenuOperationError.INVALID_QUANTITY)); return }
        val editingId = draft.value.editingComponentId
        if (draft.value.components.any { it.ingredientId == ingredientId && it.draftId != editingId }) { draft.value = draft.value.copy(editor = editor.copy(error = MenuOperationError.DUPLICATE_COMPONENT)); return }
        val ingredient = state.value.ingredients.firstOrNull { it.id == ingredientId } ?: return
        val option = editor.availableUnitOptions.firstOrNull { it.id == optionId } ?: return
        val component = DraftMenuComponent(editingId ?: java.util.UUID.randomUUID().toString(), ingredientId, ingredient.name, optionId, option.shortLabel, quantity)
        val updated = if (editingId == null) draft.value.components + component else draft.value.components.map { if (it.draftId == editingId) component else it }
        draft.value = draft.value.copy(components = updated, editor = ComponentEditorState(), editingComponentId = null)
    }
    fun removeComponent(component: DraftMenuComponent) { draft.value = draft.value.copy(components = draft.value.components - component) }

    fun save(name: String, priceText: String) {
        if (draft.value.saving) return
        val price = if (priceText.isBlank()) null else priceText.trim().toBigDecimalOrNull()
        val error = when { name.isBlank() -> MenuOperationError.NAME_REQUIRED; priceText.isNotBlank() && price == null -> MenuOperationError.PRICE_MALFORMED; price != null && price < BigDecimal.ZERO -> MenuOperationError.PRICE_NEGATIVE; else -> null }
        if (error != null) { draft.value = draft.value.copy(error = error); return }
        viewModelScope.launch {
            draft.value = draft.value.copy(saving = true, error = null)
            try {
                creation.create(NewMenuItem(menuId, categoryId, name, price, draft.value.cashDiscountBehavior,
                    draft.value.components.map { NewMenuItemComponent(it.ingredientId, it.unitOptionId, it.quantity) }))
                draft.value = draft.value.copy(saving = false, saved = true)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                draft.value = draft.value.copy(saving = false, error = e.presentationError())
            }
        }
    }
}
