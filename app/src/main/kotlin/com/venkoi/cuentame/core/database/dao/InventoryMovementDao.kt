package com.venkoi.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.venkoi.cuentame.core.database.entity.InventoryMovementEntity
import com.venkoi.cuentame.core.database.model.TopWasteRow
import com.venkoi.cuentame.core.database.model.WasteValueRow
import com.venkoi.cuentame.core.database.model.RecentWasteActivityRow
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryMovementDao {
    @Query("""
        SELECT 
            im.sourceDocumentId as wasteEventId,
            im.ingredientId,
            i.name as ingredientName,
            ia.name as areaName,
            we.reason,
            we.effectiveAt as timestamp,
            im.quantityBaseSigned as quantityBase,
            u.symbol as baseUnitSymbol,
            im.totalValueSnapshot,
            we.notes
        FROM inventory_movements im
        JOIN waste_events we ON im.sourceDocumentId = we.id AND we.restaurantId = im.restaurantId
        JOIN ingredients i ON im.ingredientId = i.id AND i.restaurantId = im.restaurantId
        JOIN units u ON i.baseUnitId = u.id
        JOIN inventory_areas ia ON im.areaId = ia.id AND ia.restaurantId = im.restaurantId
        WHERE im.restaurantId = :restaurantId
        AND im.sourceDocumentType = 'WASTE_EVENT'
        AND im.movementType = 'WASTE'
        AND we.status = 'POSTED'
        AND we.effectiveAt >= :startInclusive
        AND we.effectiveAt < :endExclusive
    """)
    fun observeWasteValueRows(
        restaurantId: String,
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<WasteValueRow>>

    @Query("""
        SELECT 
            im.ingredientId,
            i.name as ingredientName,
            u.symbol as baseUnitSymbol,
            im.quantityBaseSigned as totalQuantityBase,
            im.totalValueSnapshot as totalWasteValue,
            1 as eventCount
        FROM inventory_movements im
        JOIN waste_events we ON im.sourceDocumentId = we.id AND we.restaurantId = im.restaurantId
        JOIN ingredients i ON im.ingredientId = i.id AND i.restaurantId = im.restaurantId
        JOIN units u ON i.baseUnitId = u.id
        WHERE im.restaurantId = :restaurantId
        AND im.sourceDocumentType = 'WASTE_EVENT'
        AND im.movementType = 'WASTE'
        AND we.status = 'POSTED'
        AND we.effectiveAt >= :startInclusive
        AND we.effectiveAt < :endExclusive
    """)
    fun observeTopWasteRows(
        restaurantId: String,
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<TopWasteRow>>

    @Query("""
        SELECT 
            we.id,
            we.status,
            COALESCE(we.voidedAt, we.postedAt) as timestamp,
            i.name as ingredientName,
            im.totalValueSnapshot as totalValue
        FROM waste_events we
        JOIN inventory_movements im ON we.id = im.sourceDocumentId AND im.restaurantId = we.restaurantId
        JOIN ingredients i ON we.ingredientId = i.id AND i.restaurantId = we.restaurantId
        WHERE we.restaurantId = :restaurantId
        AND we.status IN ('POSTED', 'VOIDED')
        AND im.sourceDocumentType = 'WASTE_EVENT'
        AND im.movementType = 'WASTE'
        ORDER BY timestamp DESC, we.id ASC
        LIMIT :limit
    """)
    fun observeRecentWasteActivity(
        restaurantId: String,
        limit: Int
    ): Flow<List<RecentWasteActivityRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: InventoryMovementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(movements: List<InventoryMovementEntity>)

    @Query("DELETE FROM inventory_movements WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM inventory_movements")
    suspend fun deleteAll()

    @Query("SELECT * FROM inventory_movements")
    suspend fun getAll(): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE ingredientId = :ingredientId ORDER BY effectiveAt ASC, createdAt ASC, id ASC")
    fun observeByIngredient(ingredientId: String): Flow<List<InventoryMovementEntity>>

    @Query("SELECT * FROM inventory_movements WHERE ingredientId = :ingredientId ORDER BY effectiveAt ASC, createdAt ASC, id ASC")
    suspend fun getByIngredient(ingredientId: String): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE areaId = :areaId ORDER BY effectiveAt ASC, createdAt ASC, id ASC")
    suspend fun getByArea(areaId: String): List<InventoryMovementEntity>

    @Query("SELECT * FROM inventory_movements WHERE reversalOfMovementId = :originalMovementId LIMIT 1")
    suspend fun findReversalFor(originalMovementId: String): InventoryMovementEntity?

    @Query("SELECT * FROM inventory_movements WHERE sourceDocumentType = :type AND sourceDocumentId = :docId")
    suspend fun getBySourceDocument(type: String, docId: String): List<InventoryMovementEntity>

    @Query("""
        SELECT * FROM inventory_movements 
        WHERE restaurantId = :restaurantId 
        AND ingredientId = :ingredientId 
        AND effectiveAt <= :effectiveAt
        ORDER BY effectiveAt ASC, createdAt ASC, id ASC
    """)
    suspend fun getByRestaurantAndIngredientUpTo(
        restaurantId: String,
        ingredientId: String,
        effectiveAt: Long
    ): List<InventoryMovementEntity>

    @Query("""
        SELECT * FROM inventory_movements 
        WHERE restaurantId = :restaurantId 
        AND areaId = :areaId 
        AND effectiveAt <= :effectiveAt
        ORDER BY effectiveAt ASC, createdAt ASC, id ASC
    """)
    suspend fun getByRestaurantAndAreaUpTo(
        restaurantId: String,
        areaId: String,
        effectiveAt: Long
    ): List<InventoryMovementEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE sourceDocumentType = :type AND sourceDocumentId = :docId AND sourceOperationId = :opId LIMIT 1)")
    suspend fun existsBySourceOperation(type: String, docId: String, opId: String): Boolean

    @Query("""
        SELECT 
            im.*,
            i.name as ingredientName,
            ia.name as areaName,
            u.symbol as baseUnitSymbol,
            s.name as sourcePurchaseSupplierName,
            pr.invoiceNumber as sourcePurchaseInvoiceNumber,
            we.reason as sourceWasteReason,
            ia_we.name as sourceWasteAreaName,
            sc.name as sourceStockCountName,
            pb.recipeNameSnapshot as sourceProductionRecipeName,
            pb.status as sourceProductionStatus,
            pr.id AS sourcePurchaseResolvedId,
            we.id AS sourceWasteResolvedId,
            sc.id AS sourceStockCountResolvedId,
            pb.id AS sourceProductionResolvedId,
            im_rev.id as reversedByMovementId,
            im_rev.movementType as reversedByMovementType,
            im_rev.effectiveAt as reversedByMovementEffectiveAt,
            im_orig.movementType as reversalOfMovementType,
            im_orig.effectiveAt as reversalOfMovementEffectiveAt
        FROM inventory_movements im
        JOIN ingredients i ON im.ingredientId = i.id AND i.restaurantId = im.restaurantId
        JOIN units u ON i.baseUnitId = u.id
        JOIN inventory_areas ia ON im.areaId = ia.id AND ia.restaurantId = im.restaurantId
        LEFT JOIN purchase_receipts pr ON im.sourceDocumentType = 'PURCHASE_RECEIPT' AND im.sourceDocumentId = pr.id AND pr.restaurantId = im.restaurantId
        LEFT JOIN suppliers s ON pr.supplierId = s.id AND s.restaurantId = pr.restaurantId
        LEFT JOIN waste_events we ON im.sourceDocumentType = 'WASTE_EVENT' AND im.sourceDocumentId = we.id AND we.restaurantId = im.restaurantId
        LEFT JOIN inventory_areas ia_we ON we.areaId = ia_we.id AND ia_we.restaurantId = we.restaurantId
        LEFT JOIN stock_counts sc ON im.sourceDocumentType = 'STOCK_COUNT' AND im.sourceDocumentId = sc.id AND sc.restaurantId = im.restaurantId
        LEFT JOIN production_batches pb ON im.sourceDocumentType = 'PRODUCTION_BATCH' AND im.sourceDocumentId = pb.id AND pb.restaurantId = im.restaurantId
        LEFT JOIN inventory_movements im_rev ON im.id = im_rev.reversalOfMovementId AND im_rev.restaurantId = im.restaurantId
        LEFT JOIN inventory_movements im_orig ON im.reversalOfMovementId = im_orig.id AND im_orig.restaurantId = im.restaurantId
        WHERE im.restaurantId = :restaurantId
        AND im.effectiveAt >= :startInclusive
        AND im.effectiveAt < :endExclusive
        AND (:ingredientId IS NULL OR im.ingredientId = :ingredientId)
        AND (:areaId IS NULL OR im.areaId = :areaId)
        ORDER BY im.effectiveAt DESC, im.createdAt DESC, im.id DESC
    """)
    fun observeInventoryActivityRows(
        restaurantId: String,
        startInclusive: Long,
        endExclusive: Long,
        ingredientId: String? = null,
        areaId: String? = null
    ): Flow<List<com.venkoi.cuentame.core.database.model.InventoryActivityRow>>

    @Query("""
        SELECT 
            im.*,
            i.name as ingredientName,
            ia.name as areaName,
            u.symbol as baseUnitSymbol,
            s.name as sourcePurchaseSupplierName,
            pr.invoiceNumber as sourcePurchaseInvoiceNumber,
            we.reason as sourceWasteReason,
            ia_we.name as sourceWasteAreaName,
            sc.name as sourceStockCountName,
            pb.recipeNameSnapshot as sourceProductionRecipeName,
            pb.status as sourceProductionStatus,
            pr.id AS sourcePurchaseResolvedId,
            we.id AS sourceWasteResolvedId,
            sc.id AS sourceStockCountResolvedId,
            pb.id AS sourceProductionResolvedId,
            im_rev.id as reversedByMovementId,
            im_rev.movementType as reversedByMovementType,
            im_rev.effectiveAt as reversedByMovementEffectiveAt,
            im_orig.movementType as reversalOfMovementType,
            im_orig.effectiveAt as reversalOfMovementEffectiveAt
        FROM inventory_movements im
        JOIN ingredients i ON im.ingredientId = i.id AND i.restaurantId = im.restaurantId
        JOIN units u ON i.baseUnitId = u.id
        JOIN inventory_areas ia ON im.areaId = ia.id AND ia.restaurantId = im.restaurantId
        LEFT JOIN purchase_receipts pr ON im.sourceDocumentType = 'PURCHASE_RECEIPT' AND im.sourceDocumentId = pr.id AND pr.restaurantId = im.restaurantId
        LEFT JOIN suppliers s ON pr.supplierId = s.id AND s.restaurantId = pr.restaurantId
        LEFT JOIN waste_events we ON im.sourceDocumentType = 'WASTE_EVENT' AND im.sourceDocumentId = we.id AND we.restaurantId = im.restaurantId
        LEFT JOIN inventory_areas ia_we ON we.areaId = ia_we.id AND ia_we.restaurantId = we.restaurantId
        LEFT JOIN stock_counts sc ON im.sourceDocumentType = 'STOCK_COUNT' AND im.sourceDocumentId = sc.id AND sc.restaurantId = im.restaurantId
        LEFT JOIN production_batches pb ON im.sourceDocumentType = 'PRODUCTION_BATCH' AND im.sourceDocumentId = pb.id AND pb.restaurantId = im.restaurantId
        LEFT JOIN inventory_movements im_rev ON im.id = im_rev.reversalOfMovementId AND im_rev.restaurantId = im.restaurantId
        LEFT JOIN inventory_movements im_orig ON im.reversalOfMovementId = im_orig.id AND im_orig.restaurantId = im.restaurantId
        WHERE im.restaurantId = :restaurantId
        AND im.id = :movementId
    """)
    suspend fun getInventoryActivityRow(restaurantId: String, movementId: String): com.venkoi.cuentame.core.database.model.InventoryActivityRow?
}
