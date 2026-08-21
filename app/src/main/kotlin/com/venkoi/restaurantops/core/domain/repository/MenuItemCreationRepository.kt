package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.IngredientId
import com.venkoi.restaurantops.core.common.ids.IngredientUnitOptionId
import com.venkoi.restaurantops.core.common.ids.MenuCategoryId
import com.venkoi.restaurantops.core.common.ids.MenuId
import com.venkoi.restaurantops.core.common.ids.MenuRecipeId
import com.venkoi.restaurantops.core.model.menu.CashDiscountBehavior
import java.math.BigDecimal

data class NewMenuItemComponent(
    val ingredientId: IngredientId,
    val unitOptionId: IngredientUnitOptionId,
    val quantity: BigDecimal
)

data class NewMenuItem(
    val menuId: MenuId,
    val categoryId: MenuCategoryId,
    val name: String,
    val sellingPrice: BigDecimal?,
    val cashDiscountBehavior: CashDiscountBehavior,
    val components: List<NewMenuItemComponent>
)

/** The single persistence boundary for creating and placing a menu item. */
interface MenuItemCreationRepository {
    suspend fun create(request: NewMenuItem): MenuRecipeId
}
