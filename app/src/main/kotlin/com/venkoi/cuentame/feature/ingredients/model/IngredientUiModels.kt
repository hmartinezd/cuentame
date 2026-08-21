package com.venkoi.cuentame.feature.ingredients.model

import com.venkoi.cuentame.core.common.ids.IngredientCategoryId
import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.UnitId
import com.venkoi.cuentame.core.model.inventory.UnitDimension

data class EditableUnitOptionUiModel(
    val id: String, // UI temporary ID or real ID
    val name: String,
    val standardUnitId: UnitId? = null,
    val factorToBase: String = "1",
    val isBase: Boolean = false,
    val isDefaultCount: Boolean = false,
    val isDefaultPurchase: Boolean = false,
    val isSuggested: Boolean = false
)

data class UnitConversionChoiceUiModel(
    val sourceSymbol: String,
    val factor: String,
    val baseSymbol: String
)

data class IngredientFormUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val ingredientId: IngredientId? = null, // null for create
    val name: String = "",
    val parLevel: String = "",
    val reorderPoint: String = "",
    val selectedCategoryId: IngredientCategoryId? = null,
    val selectedDefaultAreaId: InventoryAreaId? = null,
    val selectedDimension: UnitDimension? = null,
    val selectedBaseUnitId: UnitId? = null,
    val unitOptions: List<EditableUnitOptionUiModel> = emptyList(),
    val fieldErrors: Map<String, Int> = emptyMap(),
    val error: Throwable? = null
) {
    val isEditMode: Boolean get() = ingredientId != null
}
