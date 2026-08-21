package com.venkoi.cuentame.core.database.dao

import androidx.room.*
import com.venkoi.cuentame.core.database.entity.PurchaseInvoiceParseResultEntity
import com.venkoi.cuentame.core.database.entity.PurchaseInvoiceParsedLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseParseDao {

    @Query("SELECT * FROM purchase_invoice_parse_results WHERE purchaseReceiptId = :receiptId")
    fun observeParseResultForReceipt(receiptId: String): Flow<PurchaseInvoiceParseResultEntity?>

    @Query("SELECT * FROM purchase_invoice_parse_results WHERE purchaseReceiptId = :receiptId")
    suspend fun getParseResultForReceipt(receiptId: String): PurchaseInvoiceParseResultEntity?

    @Query("SELECT id FROM purchase_invoice_parse_results WHERE purchaseReceiptId = :receiptId")
    suspend fun getParseResultIdForReceipt(receiptId: String): String?

    @Query("SELECT * FROM purchase_invoice_parsed_lines WHERE parseResultId = :resultId ORDER BY lineIndex ASC")
    suspend fun getParsedLines(resultId: String): List<PurchaseInvoiceParsedLineEntity>

    @Query("SELECT * FROM purchase_invoice_parsed_lines WHERE parseResultId = :resultId ORDER BY lineIndex ASC")
    fun observeParsedLines(resultId: String): Flow<List<PurchaseInvoiceParsedLineEntity>>

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
        expectedOcrResultId: String,
        expectedSourceDocumentSha256: String,
        result: PurchaseInvoiceParseResultEntity,
        lines: List<PurchaseInvoiceParsedLineEntity>,
        ocrDao: PurchaseOcrDao,
        lineMatchDao: PurchaseInvoiceLineMatchDao,
        materializationDao: PurchaseInvoiceMaterializationDao
    ) {
        val currentOcr = ocrDao.getOcrResultForReceiptSync(receiptId)
            ?: throw IllegalStateException("OCR Result missing for receipt $receiptId")

        if (currentOcr.id != expectedOcrResultId) {
            throw IllegalStateException("OCR Result ID changed: expected $expectedOcrResultId, found ${currentOcr.id}")
        }

        if (currentOcr.sourceDocumentSha256 != expectedSourceDocumentSha256) {
            throw IllegalStateException("OCR Source Document SHA-256 changed")
        }

        if (currentOcr.purchaseReceiptId != receiptId) {
            throw IllegalStateException("OCR result ownership mismatch")
        }

        // Lifecycle safety: block re-parse if materialized
        val existingApp = materializationDao.getApplicationForReceipt(receiptId)
        if (existingApp != null) {
            throw IllegalStateException("Cannot replace parse result: invoice already materialized to Purchase Draft")
        }

        val existingParse = getParseResultForReceipt(receiptId)
        if (existingParse != null) {
            lineMatchDao.deleteMatchesForParseResult(existingParse.id)
            deleteParseResultForReceipt(receiptId)
        }
        
        insertParseResult(result)
        insertParsedLines(lines)
    }
}
