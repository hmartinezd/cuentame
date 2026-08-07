package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.PurchaseInvoiceOcrPageEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceOcrResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseOcrDao {

    @Query("SELECT * FROM purchase_invoice_ocr_results WHERE purchaseReceiptId = :receiptId")
    fun getOcrResultForReceipt(receiptId: String): Flow<PurchaseInvoiceOcrResultEntity?>

    @Query("SELECT * FROM purchase_invoice_ocr_results WHERE purchaseReceiptId = :receiptId")
    suspend fun getOcrResultForReceiptSync(receiptId: String): PurchaseInvoiceOcrResultEntity?

    @Query("SELECT * FROM purchase_invoice_ocr_pages WHERE ocrResultId = :resultId ORDER BY pageIndex ASC")
    fun getOcrPages(resultId: String): Flow<List<PurchaseInvoiceOcrPageEntity>>

    @Query("SELECT * FROM purchase_invoice_ocr_pages WHERE ocrResultId = :resultId ORDER BY pageIndex ASC")
    suspend fun getOcrPagesSync(resultId: String): List<PurchaseInvoiceOcrPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResult(result: PurchaseInvoiceOcrResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrPages(pages: List<PurchaseInvoiceOcrPageEntity>)

    @Query("DELETE FROM purchase_invoice_ocr_results WHERE purchaseReceiptId = :receiptId")
    suspend fun deleteOcrForReceipt(receiptId: String)

    @Transaction
    suspend fun replaceOcrResult(
        receiptId: String,
        result: PurchaseInvoiceOcrResultEntity,
        pages: List<PurchaseInvoiceOcrPageEntity>
    ) {
        deleteOcrForReceipt(receiptId)
        insertOcrResult(result)
        insertOcrPages(pages)
    }
}
