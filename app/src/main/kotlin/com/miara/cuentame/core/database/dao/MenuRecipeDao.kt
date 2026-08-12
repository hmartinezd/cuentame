package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuRecipeDao {
    @Query("SELECT * FROM menu_recipes WHERE restaurantId = :restaurantId AND (:includeArchived = 1 OR archivedAt IS NULL) ORDER BY normalizedName, id")
    fun observeRecipes(restaurantId: String, includeArchived: Boolean): Flow<List<MenuRecipeEntity>>

    @Query("SELECT * FROM menu_recipes WHERE id = :id") fun observeRecipe(id: String): Flow<MenuRecipeEntity?>
    @Query("SELECT * FROM menu_recipes WHERE id = :id") suspend fun getRecipe(id: String): MenuRecipeEntity?
    @Query("SELECT * FROM menu_recipe_components WHERE menuRecipeId = :id ORDER BY sortOrder, id") fun observeComponents(id: String): Flow<List<MenuRecipeComponentEntity>>
    @Query("SELECT mrc.* FROM menu_recipe_components mrc JOIN menu_recipes mr ON mr.id=mrc.menuRecipeId WHERE mr.restaurantId=:restaurantId ORDER BY mrc.menuRecipeId,mrc.sortOrder,mrc.id") fun observeAllComponents(restaurantId: String): Flow<List<MenuRecipeComponentEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecipe(entity: MenuRecipeEntity)
    @Update suspend fun updateRecipe(entity: MenuRecipeEntity)
    @Upsert suspend fun upsertComponent(entity: MenuRecipeComponentEntity)
    @Query("SELECT * FROM menu_recipe_components WHERE id=:id") suspend fun getComponent(id: String): MenuRecipeComponentEntity?
    @Query("DELETE FROM menu_recipe_components WHERE id=:componentId AND menuRecipeId=:recipeId") suspend fun deleteComponent(recipeId: String, componentId: String)
    @Query("UPDATE menu_recipes SET archivedAt=:archivedAt,updatedAt=:updatedAt WHERE id=:id") suspend fun setArchived(id: String, archivedAt: Long?, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM menu_recipes WHERE restaurantId=:restaurantId AND normalizedName=:normalizedName AND archivedAt IS NULL AND id!=:excludeId") suspend fun activeNameCount(restaurantId:String,normalizedName:String,excludeId:String):Int
}
