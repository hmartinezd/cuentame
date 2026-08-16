package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.MenuCatalogDao
import com.miara.cuentame.core.database.dao.MenuRecipeDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.MenuCatalogPersistenceException
import com.miara.cuentame.core.domain.repository.MenuCatalogRepository
import com.miara.cuentame.core.domain.validation.MenuCatalogValidator
import com.miara.cuentame.core.model.menu.*
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMenuCatalogRepository @Inject constructor(
    private val database: RestaurantInventoryDatabase,
    private val dao: MenuCatalogDao,
    private val recipeDao: MenuRecipeDao,
    private val restaurantDao: RestaurantDao
) : MenuCatalogRepository {
    override fun observeMenus(restaurantId: RestaurantId, includeArchived: Boolean) =
        dao.observeMenus(restaurantId.value, includeArchived).map { rows -> rows.map(MenuEntity::domain) }

    override fun observeMenu(id: MenuId) = dao.observeMenu(id.value).map { it?.domain() }
    override fun observeCategories(menuId: MenuId) = dao.observeCategories(menuId.value).map { rows -> rows.map(MenuCategoryEntity::domain) }
    override fun observePlacements(menuId: MenuId) = dao.observePlacements(menuId.value).map { rows -> rows.map(MenuPlacementEntity::domain) }

    override suspend fun createMenu(restaurantId: RestaurantId, name: String, description: String?, defaultCashDiscountPercent: BigDecimal): MenuId = database.withTransaction {
        if (restaurantDao.getById(restaurantId.value) == null) throw MenuCatalogPersistenceException.NotFound()
        val now = System.currentTimeMillis()
        val candidate = Menu(MenuId(UUID.randomUUID().toString()), restaurantId, name, "", description,
            defaultCashDiscountPercent, 0, null, Instant.ofEpochMilli(now), Instant.ofEpochMilli(now))
        val canonical = validateMenu(candidate)
        if (dao.activeNameCount(restaurantId.value, canonical.normalizedName, "") > 0) throw MenuCatalogPersistenceException.DuplicateName()
        dao.insertMenu(canonical.entity())
        canonical.id
    }

    override suspend fun updateMenu(id: MenuId, name: String, description: String?, defaultCashDiscountPercent: BigDecimal) = database.withTransaction {
        val old = dao.getMenu(id.value) ?: throw MenuCatalogPersistenceException.NotFound()
        val candidate = validateMenu(old.domain().copy(name = name, description = description,
            defaultCashDiscountPercent = defaultCashDiscountPercent, updatedAt = Instant.ofEpochMilli(System.currentTimeMillis())))
        if (old.archivedAt == null && dao.activeNameCount(old.restaurantId, candidate.normalizedName, id.value) > 0) throw MenuCatalogPersistenceException.DuplicateName()
        dao.updateMenu(candidate.entity())
    }

    override suspend fun setArchived(id: MenuId, archived: Boolean) = database.withTransaction {
        val old = dao.getMenu(id.value) ?: throw MenuCatalogPersistenceException.NotFound()
        if (!archived && dao.activeNameCount(old.restaurantId, old.normalizedName, id.value) > 0) throw MenuCatalogPersistenceException.DuplicateName()
        val now = System.currentTimeMillis()
        dao.updateMenu(old.copy(archivedAt = if (archived) now else null, updatedAt = now))
    }

    override suspend fun saveCategory(menuId: MenuId, categoryId: MenuCategoryId?, name: String, sortOrder: Int): MenuCategoryId = database.withTransaction {
        val menu = dao.getMenu(menuId.value) ?: throw MenuCatalogPersistenceException.NotFound()
        val id = categoryId ?: MenuCategoryId(UUID.randomUUID().toString())
        val old = categoryId?.let { dao.getCategory(it.value) }
        if (categoryId != null && old == null) throw MenuCatalogPersistenceException.NotFound()
        if (old != null && old.menuId != menuId.value) throw MenuCatalogPersistenceException.OwnershipMismatch()
        val candidate = MenuCatalogValidator.canonicalize(MenuCategory(id, menuId, name, "", sortOrder))
        if (MenuCatalogValidator.validateCategories(menu.domain(), listOf(candidate)).isNotEmpty()) throw MenuCatalogPersistenceException.InvalidCatalog()
        val entity = candidate.entity()
        if (old == null) dao.insertCategory(entity) else dao.updateCategory(entity)
        id
    }

    override suspend fun removeCategory(menuId: MenuId, categoryId: MenuCategoryId) {
        if (dao.deleteCategory(menuId.value, categoryId.value) == 0 && dao.getCategory(categoryId.value) != null) {
            throw MenuCatalogPersistenceException.OwnershipMismatch()
        }
    }

    override suspend fun savePlacement(menuId: MenuId, placementId: MenuPlacementId?, categoryId: MenuCategoryId,
        menuRecipeId: MenuRecipeId, sortOrder: Int): MenuPlacementId = database.withTransaction {
        val menu = dao.getMenu(menuId.value) ?: throw MenuCatalogPersistenceException.NotFound()
        val category = dao.getCategory(categoryId.value) ?: throw MenuCatalogPersistenceException.NotFound()
        val recipe = recipeDao.getRecipe(menuRecipeId.value) ?: throw MenuCatalogPersistenceException.NotFound()
        if (category.menuId != menuId.value || recipe.restaurantId != menu.restaurantId) throw MenuCatalogPersistenceException.OwnershipMismatch()
        val id = placementId ?: MenuPlacementId(UUID.randomUUID().toString())
        val old = placementId?.let { dao.getPlacement(it.value) }
        if (placementId != null && old == null) throw MenuCatalogPersistenceException.NotFound()
        if (old != null && old.menuId != menuId.value) throw MenuCatalogPersistenceException.OwnershipMismatch()
        val candidate = MenuPlacement(id, menuId, categoryId, menuRecipeId, sortOrder)
        if (MenuCatalogValidator.validatePlacements(menu.domain(), listOf(category.domain()), listOf(candidate)).isNotEmpty()) throw MenuCatalogPersistenceException.InvalidCatalog()
        val entity = candidate.entity()
        if (old == null) dao.insertPlacement(entity) else dao.updatePlacement(entity)
        id
    }

    override suspend fun removePlacement(menuId: MenuId, placementId: MenuPlacementId) {
        if (dao.deletePlacement(menuId.value, placementId.value) == 0 && dao.getPlacement(placementId.value) != null) {
            throw MenuCatalogPersistenceException.OwnershipMismatch()
        }
    }

    private fun validateMenu(menu: Menu): Menu {
        val canonical = MenuCatalogValidator.canonicalize(menu)
        if (MenuCatalogValidator.validateMenu(canonical).isNotEmpty()) throw MenuCatalogPersistenceException.InvalidCatalog()
        return canonical
    }
}

private fun MenuEntity.domain() = Menu(MenuId(id), RestaurantId(restaurantId), name, normalizedName, description,
    defaultCashDiscountPercent, publicationRevision, archivedAt?.let(Instant::ofEpochMilli), Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt))
private fun Menu.entity() = MenuEntity(id.value, restaurantId.value, name, normalizedName, description,
    defaultCashDiscountPercent, publicationRevision, archivedAt?.toEpochMilli(), createdAt.toEpochMilli(), updatedAt.toEpochMilli())
private fun MenuCategoryEntity.domain() = MenuCategory(MenuCategoryId(id), MenuId(menuId), name, normalizedName, sortOrder)
private fun MenuCategory.entity() = MenuCategoryEntity(id.value, menuId.value, name, normalizedName, sortOrder)
private fun MenuPlacementEntity.domain() = MenuPlacement(MenuPlacementId(id), MenuId(menuId), MenuCategoryId(categoryId), MenuRecipeId(menuRecipeId), sortOrder)
private fun MenuPlacement.entity() = MenuPlacementEntity(id.value, menuId.value, categoryId.value, menuRecipeId.value, sortOrder)
