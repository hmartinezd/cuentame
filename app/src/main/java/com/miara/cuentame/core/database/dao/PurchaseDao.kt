package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: PurchaseReceiptEntity)

    @Update
    suspend fun updateReceipt(receipt: PurchaseReceiptEntity)

    @Query("DELETE FROM purchase_receipts WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraftReceipt(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<PurchaseLineEntity>)

    @Query("DELETE FROM purchase_lines WHERE purchaseReceiptId = :receiptId")
    suspend fun deleteLinesForReceipt(receiptId: String)

    @Query("SELECT * FROM purchase_receipts WHERE status = :status ORDER BY purchaseDate DESC")
    fun observeReceiptsByStatus(status: String): Flow<List<PurchaseReceiptEntity>>

    @Query("SELECT * FROM purchase_receipts WHERE id = :id")
    suspend fun getReceiptById(id: String): PurchaseReceiptEntity?

    @Query("SELECT * FROM purchase_lines WHERE purchaseReceiptId = :receiptId")
    suspend fun getLinesForReceipt(receiptId: String): List<PurchaseLineEntity>

    @Query("SELECT * FROM purchase_lines WHERE purchaseReceiptId = :receiptId")
    fun observeLinesForReceipt(receiptId: String): Flow<List<PurchaseLineEntity>>
}
