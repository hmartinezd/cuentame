package com.venkoi.restaurantops.core.database.dao

import androidx.room.*
import com.venkoi.restaurantops.core.database.entity.PreparationRecipeComponentEntity
import com.venkoi.restaurantops.core.database.entity.PreparationRecipeEntity
import com.venkoi.restaurantops.core.model.ingredient.PreparationRecipeDependencyEdge
import kotlinx.coroutines.flow.Flow

@Dao
interface PreparationRecipeDao {

    @Query("""
        SELECT 
            pr.id,
            pr.outputIngredientId,
            i.name as outputIngredientName,
            pr.name as recipeName,
            pr.status,
            pr.standardYieldQuantity,
            iuo.displayName as yieldUnitLabel,
            (SELECT COUNT(*) FROM preparation_recipe_components WHERE recipeId = pr.id) as componentCount,
            pr.updatedAt
        FROM preparation_recipes pr
        JOIN ingredients i ON pr.outputIngredientId = i.id
        LEFT JOIN ingredient_unit_options iuo ON pr.yieldUnitOptionId = iuo.id
        WHERE pr.restaurantId = :restaurantId AND (:includeArchived = 1 OR pr.status != 'ARCHIVED')
        ORDER BY i.normalizedName ASC, pr.id ASC
    """)
    fun observeRecipeSummaries(restaurantId: String, includeArchived: Boolean): Flow<List<RecipeSummaryRow>>

    @Query("SELECT * FROM preparation_recipes WHERE id = :recipeId")
    fun observeById(recipeId: String): Flow<PreparationRecipeEntity?>

    @Query("SELECT * FROM preparation_recipes WHERE id = :recipeId")
    suspend fun getById(recipeId: String): PreparationRecipeEntity?

    @Query("SELECT * FROM preparation_recipes WHERE restaurantId = :restaurantId AND outputIngredientId = :outputIngredientId AND status != 'ARCHIVED'")
    suspend fun getActiveOrDraftByOutputIngredient(restaurantId: String, outputIngredientId: String): PreparationRecipeEntity?

    @Query("SELECT * FROM preparation_recipe_components WHERE id = :componentId")
    suspend fun getComponentById(componentId: String): PreparationRecipeComponentEntity?

    @Query("SELECT * FROM preparation_recipe_components WHERE recipeId = :recipeId ORDER BY sortOrder ASC, id ASC")
    suspend fun getComponentsForRecipe(recipeId: String): List<PreparationRecipeComponentEntity>

    @Query("SELECT * FROM preparation_recipe_components WHERE recipeId = :recipeId ORDER BY sortOrder ASC, id ASC")
    fun observeComponentsForRecipe(recipeId: String): Flow<List<PreparationRecipeComponentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PreparationRecipeEntity)

    @Update
    suspend fun update(entity: PreparationRecipeEntity)

    @Upsert
    suspend fun upsertComponent(entity: PreparationRecipeComponentEntity)

    @Query("DELETE FROM preparation_recipe_components WHERE recipeId = :recipeId AND id = :componentId")
    suspend fun deleteComponent(recipeId: String, componentId: String)

    @Query("UPDATE preparation_recipes SET status = :status, updatedAt = :updatedAt, archivedAt = :archivedAt WHERE id = :recipeId")
    suspend fun updateStatus(recipeId: String, status: String, updatedAt: Long, archivedAt: Long?)

    @Query("SELECT COUNT(*) FROM preparation_recipe_components WHERE recipeId = :recipeId")
    suspend fun countComponents(recipeId: String): Int

    @Query("""
        SELECT pr.outputIngredientId as fromId, prc.componentIngredientId as toId
        FROM preparation_recipes pr
        JOIN preparation_recipe_components prc ON pr.id = prc.recipeId
        WHERE pr.restaurantId = :restaurantId AND pr.status != 'ARCHIVED'
    """)
    suspend fun getNonArchivedDependencyGraph(restaurantId: String): List<PreparationRecipeDependencyEdge>

    @Transaction
    suspend fun reorderComponents(recipeId: String, orderedComponentIds: List<String>, updatedAt: Long) {
        orderedComponentIds.forEachIndexed { index, id ->
            updateComponentSortOrder(recipeId, id, index, updatedAt)
        }
    }

    @Query("UPDATE preparation_recipe_components SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE recipeId = :recipeId AND id = :componentId")
    suspend fun updateComponentSortOrder(recipeId: String, componentId: String, sortOrder: Int, updatedAt: Long)

    @Query("SELECT * FROM preparation_recipe_components WHERE recipeId = :recipeId AND componentIngredientId = :componentIngredientId")
    suspend fun getComponentByIngredient(recipeId: String, componentIngredientId: String): PreparationRecipeComponentEntity?

    @Query("SELECT * FROM preparation_recipes WHERE restaurantId = :restaurantId")
    suspend fun getAllRecipesForRestaurant(restaurantId: String): List<PreparationRecipeEntity>

    @Query("SELECT * FROM preparation_recipes WHERE restaurantId = :restaurantId")
    fun observeAllRecipesForRestaurant(restaurantId: String): Flow<List<PreparationRecipeEntity>>

    @Query("SELECT prc.* FROM preparation_recipe_components prc JOIN preparation_recipes pr ON pr.id = prc.recipeId WHERE pr.restaurantId = :restaurantId ORDER BY prc.recipeId, prc.sortOrder, prc.id")
    fun observeAllComponentsForRestaurant(restaurantId: String): Flow<List<PreparationRecipeComponentEntity>>

    @Query("""
        SELECT pr.* FROM preparation_recipes pr
        JOIN preparation_recipe_components prc ON pr.id = prc.recipeId
        WHERE prc.componentIngredientId = :ingredientId AND pr.status != 'ARCHIVED'
    """)
    suspend fun getRecipesUsingIngredient(ingredientId: String): List<PreparationRecipeEntity>

    @Query("SELECT COUNT(*) FROM preparation_recipes WHERE outputIngredientId = :ingredientId AND status != 'ARCHIVED'")
    suspend fun countActiveOrDraftRecipesForOutput(ingredientId: String): Int

    @Query("SELECT COUNT(*) FROM preparation_recipes WHERE yieldUnitOptionId = :optionId AND status != 'ARCHIVED'")
    suspend fun countActiveOrDraftRecipesUsingYieldOption(optionId: String): Int

    @Query("SELECT COUNT(*) FROM preparation_recipe_components prc JOIN preparation_recipes pr ON prc.recipeId = pr.id WHERE prc.unitOptionId = :optionId AND pr.status != 'ARCHIVED'")
    suspend fun countActiveOrDraftRecipeComponentsUsingOption(optionId: String): Int
}

data class RecipeSummaryRow(
    val id: String,
    val outputIngredientId: String,
    val outputIngredientName: String,
    val recipeName: String,
    val status: String,
    val standardYieldQuantity: java.math.BigDecimal?,
    val yieldUnitLabel: String?,
    val componentCount: Int,
    val updatedAt: Long
)
