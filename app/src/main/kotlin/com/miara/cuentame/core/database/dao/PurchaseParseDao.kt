package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.PurchaseInvoiceParseResultEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceParsedLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseParseDao {

    @Query("SELECT * FROM purchase_invoice_parse_results WHERE purchaseReceiptId = :receiptId")
    fun observeParseResultForReceipt(receiptId: String): Flow<PurchaseInvoiceParseResultEntity?>

    @Query("SELECT * FROM purchase_invoice_parse_results WHERE purchaseReceiptId = :receiptId")
    suspend fun getParseResultForReceipt(receiptId: String): PurchaseInvoiceParseResultEntity?

    @Query("SELECT * FROM purchase_invoice_parsed_lines WHERE parseResultId = :resultId ORDER BY lineIndex ASC")
    suspend fun getParsedLines(resultId: String): List<PurchaseInvoiceParsedLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParseResult(result: PurchaseInvoiceParseResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParsedLines(lines: List<PurchaseInvoiceParsedLineEntity>)

    @Query("DELETE FROM purchase_invoice_parse_results WHERE purchaseReceiptId = :receiptId")
    suspend fun deleteParseResultForReceipt(receiptId: String)

    @Query("UPDATE purchase_invoice_parsed_lines SET isIgnored = :isIgnored, correctionJson = :correctionJson WHERE parseResultId = :parseResultId AND lineIndex = :lineIndex")
    suspend fun updateParsedLine(parseResultId: String, lineIndex: Int, isIgnored: Boolean, correctionJson: String?)

    @Query("UPDATE purchase_invoice_parse_results SET correctionsJson = :correctionsJson, reviewedAt = :reviewedAt WHERE purchaseReceiptId = :receiptId")
    suspend fun updateParseResultCorrections(receiptId: String, correctionsJson: String?, reviewedAt: Long)

    @Transaction
    suspend fun replaceParseResult(
        receiptId: String,
        ocrResultId: String,
        result: PurchaseInvoiceParseResultEntity,
        lines: List<PurchaseInvoiceParsedLineEntity>
    ) {
        // Verify OCR result still exists and matches
        if (result.ocrResultId != ocrResultId) {
             throw IllegalArgumentException("OCR Result ID mismatch")
        }

        deleteParseResultForReceipt(receiptId)
        insertParseResult(result)
        insertParsedLines(lines)
    }
}
