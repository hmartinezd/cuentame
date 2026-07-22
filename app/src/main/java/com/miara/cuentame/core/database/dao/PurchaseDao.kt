package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: PurchaseReceiptEntity)

    @Upsert
    suspend fun upsertReceipt(receipt: PurchaseReceiptEntity)

    @Update
    suspend fun updateReceipt(receipt: PurchaseReceiptEntity)

    @Query("DELETE FROM purchase_receipts WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraftReceipt(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLine(line: PurchaseLineEntity)

    @Upsert
    suspend fun upsertLines(lines: List<PurchaseLineEntity>)

    @Update
    suspend fun updateLine(line: PurchaseLineEntity)

    @Query("DELETE FROM purchase_lines WHERE id = :id")
    suspend fun deleteLine(id: String)

    @Query("DELETE FROM purchase_lines WHERE purchaseReceiptId = :receiptId")
    suspend fun deleteLinesForReceipt(receiptId: String)

    @Query("""
        SELECT pr.* FROM purchase_receipts pr
        LEFT JOIN suppliers s ON pr.supplierId = s.id
        WHERE pr.restaurantId = :restaurantId
        AND (:status IS NULL OR pr.status = :status)
        AND (:supplierId IS NULL OR pr.supplierId = :supplierId)
        AND (:query IS NULL OR pr.invoiceNumber LIKE '%' || :query || '%' OR s.name LIKE '%' || :query || '%')
        ORDER BY pr.purchaseDate DESC, pr.createdAt DESC
    """)
    fun observeFilteredReceipts(
        restaurantId: String,
        status: String?,
        supplierId: String?,
        query: String?
    ): Flow<List<PurchaseReceiptEntity>>

    @Query("SELECT * FROM purchase_receipts WHERE id = :id")
    suspend fun getReceiptById(id: String): PurchaseReceiptEntity?

    @Query("SELECT * FROM purchase_receipts WHERE id = :id")
    fun observeReceiptById(id: String): Flow<PurchaseReceiptEntity?>

    @Query("SELECT * FROM purchase_lines WHERE purchaseReceiptId = :receiptId ORDER BY createdAt ASC")
    suspend fun getLinesForReceipt(receiptId: String): List<PurchaseLineEntity>

    @Query("SELECT * FROM purchase_lines WHERE purchaseReceiptId = :receiptId ORDER BY createdAt ASC")
    fun observeLinesForReceipt(receiptId: String): Flow<List<PurchaseLineEntity>>

    @Query("SELECT * FROM purchase_lines WHERE id = :id")
    suspend fun getLineById(id: String): PurchaseLineEntity?

    @Transaction
    suspend fun deleteDraftWithLines(receiptId: String) {
        deleteLinesForReceipt(receiptId)
        deleteDraftReceipt(receiptId)
    }
}
