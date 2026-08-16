package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuCatalogDao {
    @Query("SELECT * FROM menus WHERE restaurantId=:restaurantId AND (:includeArchived OR archivedAt IS NULL) ORDER BY normalizedName, id")
    fun observeMenus(restaurantId: String, includeArchived: Boolean): Flow<List<MenuEntity>>
    @Query("SELECT * FROM menus WHERE id=:id") fun observeMenu(id: String): Flow<MenuEntity?>
    @Query("SELECT * FROM menus WHERE id=:id") suspend fun getMenu(id: String): MenuEntity?
    @Query("SELECT * FROM menu_categories WHERE menuId=:menuId ORDER BY sortOrder, id") fun observeCategories(menuId: String): Flow<List<MenuCategoryEntity>>
    @Query("SELECT * FROM menu_categories WHERE id=:id") suspend fun getCategory(id: String): MenuCategoryEntity?
    @Query("SELECT * FROM menu_categories WHERE menuId=:menuId ORDER BY sortOrder, id") suspend fun getCategories(menuId: String): List<MenuCategoryEntity>
    @Query("SELECT * FROM menu_placements WHERE menuId=:menuId ORDER BY sortOrder, id") fun observePlacements(menuId: String): Flow<List<MenuPlacementEntity>>
    @Query("SELECT * FROM menu_placements WHERE id=:id") suspend fun getPlacement(id: String): MenuPlacementEntity?
    @Query("SELECT * FROM menu_placements WHERE menuId=:menuId ORDER BY sortOrder, id") suspend fun getPlacements(menuId: String): List<MenuPlacementEntity>
    @Query("SELECT COUNT(*) FROM menus WHERE restaurantId=:restaurantId AND normalizedName=:normalizedName AND archivedAt IS NULL AND id!=:excludeId")
    suspend fun activeNameCount(restaurantId: String, normalizedName: String, excludeId: String): Int
    @Query("SELECT COUNT(*) FROM menu_categories WHERE menuId=:menuId AND normalizedName=:normalizedName AND id!=:excludeId")
    suspend fun categoryNameCount(menuId: String, normalizedName: String, excludeId: String): Int
    @Query("SELECT COUNT(*) FROM menu_placements WHERE menuId=:menuId AND menuRecipeId=:menuRecipeId AND id!=:excludeId")
    suspend fun menuRecipePlacementCount(menuId: String, menuRecipeId: String, excludeId: String): Int
    @Insert suspend fun insertMenu(entity: MenuEntity)
    @Update suspend fun updateMenu(entity: MenuEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategory(entity: MenuCategoryEntity)
    @Update suspend fun updateCategory(entity: MenuCategoryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlacement(entity: MenuPlacementEntity)
    @Update suspend fun updatePlacement(entity: MenuPlacementEntity)
    @Query("DELETE FROM menu_categories WHERE id=:id AND menuId=:menuId") suspend fun deleteCategory(menuId: String, id: String): Int
    @Query("DELETE FROM menu_placements WHERE id=:id AND menuId=:menuId") suspend fun deletePlacement(menuId: String, id: String): Int
    @Query("UPDATE menu_categories SET sortOrder=:sortOrder WHERE id=:id AND menuId=:menuId") suspend fun updateCategoryOrder(menuId: String, id: String, sortOrder: Int): Int
    @Query("UPDATE menu_placements SET sortOrder=:sortOrder WHERE id=:id AND menuId=:menuId") suspend fun updatePlacementOrder(menuId: String, id: String, sortOrder: Int): Int
}
