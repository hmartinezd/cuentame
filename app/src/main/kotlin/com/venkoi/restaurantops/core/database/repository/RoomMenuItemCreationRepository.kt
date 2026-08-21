package com.venkoi.restaurantops.core.database.repository

import androidx.room.withTransaction
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.domain.repository.MenuCatalogPersistenceException
import com.venkoi.restaurantops.core.domain.repository.MenuCatalogRepository
import com.venkoi.restaurantops.core.domain.repository.MenuItemCreationRepository
import com.venkoi.restaurantops.core.domain.repository.MenuRecipeRepository
import com.venkoi.restaurantops.core.domain.repository.NewMenuItem
import com.venkoi.restaurantops.core.model.menu.CashDiscountBehavior
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMenuItemCreationRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val catalogs: MenuCatalogRepository,
    private val recipes: MenuRecipeRepository
) : MenuItemCreationRepository {
    override suspend fun create(request: NewMenuItem) = database.withTransaction {
        val menu = database.menuCatalogDao().getMenu(request.menuId.value)
            ?: throw MenuCatalogPersistenceException.NotFound()
        val category = database.menuCatalogDao().getCategory(request.categoryId.value)
            ?: throw MenuCatalogPersistenceException.NotFound()
        if (category.menuId != menu.id) throw MenuCatalogPersistenceException.OwnershipMismatch()

        val recipeId = recipes.create(
            com.venkoi.restaurantops.core.common.ids.RestaurantId(menu.restaurantId),
            request.name,
            request.sellingPrice,
            null
        )
        if (request.cashDiscountBehavior != CashDiscountBehavior.APPLY_DEFAULT) {
            recipes.setCashDiscountBehavior(recipeId, request.cashDiscountBehavior)
        }
        request.components.forEachIndexed { index, component ->
            recipes.saveComponent(
                recipeId,
                null,
                component.ingredientId,
                component.unitOptionId,
                component.quantity,
                index
            )
        }
        val sortOrder = (database.menuCatalogDao().getPlacements(request.menuId.value)
            .maxOfOrNull { it.sortOrder } ?: -10) + 10
        catalogs.savePlacement(request.menuId, null, request.categoryId, recipeId, sortOrder)
        recipeId
    }
}
