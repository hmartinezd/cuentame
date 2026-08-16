package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.model.menu.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

sealed class MenuCatalogPersistenceException(message: String) : IllegalArgumentException(message) {
    class InvalidCatalog : MenuCatalogPersistenceException("Menu catalog values are invalid")
    class NotFound : MenuCatalogPersistenceException("Menu catalog object does not exist")
    class DuplicateName : MenuCatalogPersistenceException("An active menu already uses this name")
    class DuplicateCategoryName : MenuCatalogPersistenceException("This menu already contains a category with this name")
    class DuplicateMenuRecipePlacement : MenuCatalogPersistenceException("This menu already contains this menu item")
    class OwnershipMismatch : MenuCatalogPersistenceException("Menu catalog objects must belong to the same restaurant and menu")
}

interface MenuCatalogRepository {
    fun observeMenus(restaurantId: RestaurantId, includeArchived: Boolean = false): Flow<List<Menu>>
    fun observeMenu(id: MenuId): Flow<Menu?>
    fun observeCategories(menuId: MenuId): Flow<List<MenuCategory>>
    fun observePlacements(menuId: MenuId): Flow<List<MenuPlacement>>
    suspend fun createMenu(restaurantId: RestaurantId, name: String, description: String?, defaultCashDiscountPercent: BigDecimal): MenuId
    suspend fun updateMenu(id: MenuId, name: String, description: String?, defaultCashDiscountPercent: BigDecimal)
    suspend fun setArchived(id: MenuId, archived: Boolean)
    suspend fun saveCategory(menuId: MenuId, categoryId: MenuCategoryId?, name: String, sortOrder: Int): MenuCategoryId
    suspend fun removeCategory(menuId: MenuId, categoryId: MenuCategoryId)
    suspend fun savePlacement(menuId: MenuId, placementId: MenuPlacementId?, categoryId: MenuCategoryId, menuRecipeId: MenuRecipeId, sortOrder: Int): MenuPlacementId
    suspend fun removePlacement(menuId: MenuId, placementId: MenuPlacementId)
}
