package com.venkoi.restaurantops.core.model.menu

import com.venkoi.restaurantops.core.common.ids.MenuCategoryId
import com.venkoi.restaurantops.core.common.ids.MenuId
import com.venkoi.restaurantops.core.common.ids.MenuPlacementId
import com.venkoi.restaurantops.core.common.ids.MenuRecipeId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import java.math.BigDecimal
import java.time.Instant

data class Menu(
    val id: MenuId,
    val restaurantId: RestaurantId,
    val name: String,
    val normalizedName: String,
    val description: String?,
    val defaultCashDiscountPercent: BigDecimal,
    val publicationRevision: Long,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class MenuCategory(
    val id: MenuCategoryId,
    val menuId: MenuId,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int
)

data class MenuPlacement(
    val id: MenuPlacementId,
    val menuId: MenuId,
    val categoryId: MenuCategoryId,
    val menuRecipeId: MenuRecipeId,
    val sortOrder: Int
)

enum class CashDiscountBehavior {
    APPLY_DEFAULT,
    NONE
}
