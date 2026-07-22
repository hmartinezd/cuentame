package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Upsert
    suspend fun upsertReceipt(receipt: PurchaseReceiptEntity)

    @Update
    suspend fun updateReceipt(receipt: PurchaseReceiptEntity)

    @Query("DELETE FROM purchase_receipts WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraftReceipt(id: String)

    @Upsert
    suspend fun upsertLines(lines: List<PurchaseLineEntity>)

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
