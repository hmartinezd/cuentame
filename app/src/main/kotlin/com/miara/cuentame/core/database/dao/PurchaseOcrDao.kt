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
        expectedAttachmentPath: String,
        expectedDocumentSha256: String,
        result: PurchaseInvoiceOcrResultEntity,
        pages: List<PurchaseInvoiceOcrPageEntity>,
        purchaseDao: PurchaseDao
    ) {
        val receipt = purchaseDao.getReceiptById(receiptId)
        if (receipt?.attachmentPath != expectedAttachmentPath) {
            throw IllegalStateException("Document changed or removed since OCR started")
        }

        val existingResult = getOcrResultForReceiptSync(receiptId)
        if (existingResult != null && existingResult.sourceDocumentSha256 != expectedDocumentSha256) {
            // Optional: stricter validation if we want to ensure we are only replacing 
            // the exact document we analyzed if a result already exists.
        }

        deleteOcrForReceipt(receiptId)
        insertOcrResult(result)
        insertOcrPages(pages)
    }
}
