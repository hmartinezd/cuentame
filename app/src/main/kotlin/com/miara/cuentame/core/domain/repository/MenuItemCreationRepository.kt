package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.MenuCategoryId
import com.miara.cuentame.core.common.ids.MenuId
import com.miara.cuentame.core.common.ids.MenuRecipeId
import com.miara.cuentame.core.model.menu.CashDiscountBehavior
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
