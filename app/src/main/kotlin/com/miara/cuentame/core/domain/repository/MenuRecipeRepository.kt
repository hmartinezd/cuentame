package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.model.menu.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

sealed class MenuRecipeValidationException(message: String) : IllegalArgumentException(message) {
    class InvalidName : MenuRecipeValidationException("Menu item name is required")
    class InvalidSellingPrice : MenuRecipeValidationException("Selling price cannot be negative")
    class DuplicateName : MenuRecipeValidationException("An active menu item already uses this name")
    class DuplicateComponent : MenuRecipeValidationException("This ingredient is already part of this menu item")
    class InvalidQuantity : MenuRecipeValidationException("Quantity must be greater than zero")
    class UnitOptionMismatch : MenuRecipeValidationException("Selected unit does not belong to this ingredient")
    class InactiveUnitOption : MenuRecipeValidationException("Selected unit is no longer active")
    class OwnershipMismatch : MenuRecipeValidationException("Menu item, ingredient, and unit option must belong together")
}
interface MenuRecipeRepository {
    fun observeRecipes(restaurantId: RestaurantId, includeArchived: Boolean = false): Flow<List<MenuRecipe>>
    fun observeRecipe(id: MenuRecipeId): Flow<MenuRecipe?>
    fun observeComponents(id: MenuRecipeId): Flow<List<MenuRecipeComponent>>
    suspend fun create(restaurantId: RestaurantId, name: String, sellingPrice: BigDecimal?, notes: String?): MenuRecipeId
    suspend fun update(id: MenuRecipeId, name: String, sellingPrice: BigDecimal?, notes: String?)
    suspend fun saveComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId?, ingredientId: IngredientId,
        optionId: IngredientUnitOptionId, quantityEntered: BigDecimal, sortOrder: Int): MenuRecipeComponentId
    suspend fun removeComponent(recipeId: MenuRecipeId, componentId: MenuRecipeComponentId)
    suspend fun setArchived(id: MenuRecipeId, archived: Boolean)
}

interface MenuCostRepository {
    fun observeCost(id: MenuRecipeId): Flow<MenuRecipeCost?>
    fun observeCosts(restaurantId: RestaurantId, includeArchived: Boolean = false): Flow<List<MenuRecipeCost>>
}
