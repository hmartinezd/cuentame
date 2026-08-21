package com.venkoi.cuentame.core.database.dao

import androidx.room.*
import com.venkoi.cuentame.core.database.entity.ProductionBatchComponentEntity
import com.venkoi.cuentame.core.database.entity.ProductionBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductionBatchDao {

    @Query("""
        SELECT 
            pb.id,
            pb.recipeNameSnapshot as recipeName,
            i.name as outputIngredientName,
            pb.status,
            pb.expectedOutputQuantityEntered,
            pb.actualOutputQuantityEntered,
            iuo.displayName as outputUnitLabel,
            (SELECT COUNT(*) FROM production_batch_components WHERE productionBatchId = pb.id) as componentCount,
            pb.totalComponentCostSnapshot as totalComponentCost,
            pb.effectiveAt
        FROM production_batches pb
        JOIN ingredients i ON pb.outputIngredientId = i.id
        JOIN ingredient_unit_options iuo ON pb.outputUnitOptionId = iuo.id
        WHERE pb.restaurantId = :restaurantId AND (:status IS NULL OR pb.status = :status)
        ORDER BY pb.effectiveAt DESC, pb.createdAt DESC
    """)
    fun observeSummaries(restaurantId: String, status: String?): Flow<List<ProductionBatchSummaryRow>>

    @Query("SELECT * FROM production_batches WHERE id = :batchId")
    fun observeById(batchId: String): Flow<ProductionBatchEntity?>

    @Query("SELECT * FROM production_batches WHERE id = :batchId")
    suspend fun getById(batchId: String): ProductionBatchEntity?

    @Query("SELECT * FROM production_batch_components WHERE productionBatchId = :batchId ORDER BY sortOrder ASC, id ASC")
    fun observeComponents(batchId: String): Flow<List<ProductionBatchComponentEntity>>

    @Query("SELECT * FROM production_batch_components WHERE productionBatchId = :batchId ORDER BY sortOrder ASC, id ASC")
    suspend fun getComponents(batchId: String): List<ProductionBatchComponentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProductionBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComponents(entities: List<ProductionBatchComponentEntity>)

    @Update
    suspend fun update(entity: ProductionBatchEntity)

    @Update
    suspend fun updateComponent(entity: ProductionBatchComponentEntity)

    @Delete
    suspend fun delete(entity: ProductionBatchEntity)

    @Query("DELETE FROM production_batch_components WHERE productionBatchId = :batchId")
    suspend fun deleteComponentsForBatch(batchId: String)

    @Query("SELECT * FROM production_batch_components WHERE id = :componentId")
    suspend fun getComponentById(componentId: String): ProductionBatchComponentEntity?

    @Query("SELECT COUNT(*) FROM production_batches WHERE outputIngredientId = :ingredientId AND status = 'DRAFT'")
    suspend fun countDraftsUsingOutputIngredient(ingredientId: String): Int

    @Query("SELECT COUNT(*) FROM production_batch_components pbc JOIN production_batches pb ON pbc.productionBatchId = pb.id WHERE pbc.componentIngredientId = :ingredientId AND pb.status = 'DRAFT'")
    suspend fun countDraftsUsingComponentIngredient(ingredientId: String): Int

    @Query("SELECT COUNT(*) FROM production_batches WHERE outputUnitOptionId = :optionId AND status = 'DRAFT'")
    suspend fun countDraftsUsingOutputOption(optionId: String): Int

    @Query("SELECT COUNT(*) FROM production_batch_components pbc JOIN production_batches pb ON pbc.productionBatchId = pb.id WHERE pbc.unitOptionId = :optionId AND pb.status = 'DRAFT'")
    suspend fun countDraftsUsingComponentOption(optionId: String): Int

    @Query("SELECT COUNT(*) FROM production_batches WHERE outputAreaId = :areaId AND status = 'DRAFT'")
    suspend fun countDraftsUsingOutputArea(areaId: String): Int

    @Query("SELECT COUNT(*) FROM production_batch_components pbc JOIN production_batches pb ON pbc.productionBatchId = pb.id WHERE pbc.sourceAreaId = :areaId AND pb.status = 'DRAFT'")
    suspend fun countDraftsUsingComponentSourceArea(areaId: String): Int

    @Query("SELECT * FROM production_batches WHERE recipeId = :recipeId AND status = 'POSTED' AND voidedAt IS NULL ORDER BY effectiveAt DESC, createdAt DESC LIMIT 1")
    fun observeLatestPostedForRecipe(recipeId: String): Flow<ProductionBatchEntity?>
}

data class ProductionBatchSummaryRow(
    val id: String,
    val recipeName: String,
    val outputIngredientName: String,
    val status: String,
    val expectedOutputQuantityEntered: String,
    val actualOutputQuantityEntered: String,
    val outputUnitLabel: String,
    val componentCount: Int,
    val totalComponentCost: String?,
    val effectiveAt: Long
)
