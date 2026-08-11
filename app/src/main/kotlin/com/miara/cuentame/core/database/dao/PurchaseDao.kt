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
import com.miara.cuentame.core.database.model.PurchaseSpendRow
import com.miara.cuentame.core.database.model.RecentPurchaseActivityRow
import com.miara.cuentame.core.database.model.VendorPriceObservationRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("""
        SELECT pr.id AS purchaseReceiptId, pl.id AS purchaseLineId,
            pr.restaurantId, r.currencyCode, pl.ingredientId, i.name AS ingredientName,
            u.symbol AS baseUnitSymbol, pr.supplierId, s.name AS supplierName,
            pr.purchaseDate, pr.postedAt, pl.ingredientUnitOptionId,
            iuo.displayName AS purchaseUnitLabel, pl.quantityEntered,
            pl.quantityBase, pl.lineTotal, pl.unitCostBase,
            pil.evidenceJson AS parsedLineEvidenceJson,
            pil.correctionJson AS parsedLineCorrectionJson
        FROM purchase_receipts pr
        JOIN purchase_lines pl ON pl.purchaseReceiptId = pr.id
        JOIN restaurants r ON r.id = pr.restaurantId
        JOIN ingredients i ON i.id = pl.ingredientId AND i.restaurantId = pr.restaurantId
        JOIN units u ON u.id = i.baseUnitId
        LEFT JOIN suppliers s ON s.id = pr.supplierId AND s.restaurantId = pr.restaurantId
        LEFT JOIN ingredient_unit_options iuo ON iuo.id = pl.ingredientUnitOptionId
        LEFT JOIN purchase_invoice_line_origins origin ON origin.purchaseLineId = pl.id
        LEFT JOIN purchase_invoice_draft_applications app ON app.id = origin.applicationId
        LEFT JOIN purchase_invoice_parsed_lines pil
          ON pil.parseResultId = app.parseResultId AND pil.lineIndex = origin.sourceLineIndex
        WHERE pr.restaurantId = :restaurantId AND pr.status = 'POSTED'
          AND (:ingredientId IS NULL OR pl.ingredientId = :ingredientId)
        ORDER BY pr.purchaseDate DESC, pr.postedAt DESC,
          pr.id ASC, pl.id ASC
    """)
    fun observeVendorPriceRows(
        restaurantId: String,
        ingredientId: String?
    ): Flow<List<VendorPriceObservationRow>>

    @Query("""
        SELECT 
            pr.id as receiptId,
            pr.purchaseDate,
            pr.postedAt,
            s.name as supplierName,
            pl.lineTotal
        FROM purchase_receipts pr
        JOIN purchase_lines pl ON pr.id = pl.purchaseReceiptId
        LEFT JOIN suppliers s ON pr.supplierId = s.id AND s.restaurantId = pr.restaurantId
        WHERE pr.restaurantId = :restaurantId 
        AND pr.status = 'POSTED'
        AND pr.purchaseDate >= :startInclusive
        AND pr.purchaseDate < :endExclusive
    """)
    fun observeSpendRows(
        restaurantId: String,
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<PurchaseSpendRow>>

    @Query("""
        SELECT 
            pr.id,
            pr.status,
            pr.postedAt,
            s.name as supplierName,
            pl.lineTotal
        FROM purchase_receipts pr
        JOIN purchase_lines pl ON pr.id = pl.purchaseReceiptId
        LEFT JOIN suppliers s ON pr.supplierId = s.id
        WHERE pr.id IN (
            SELECT id FROM purchase_receipts 
            WHERE restaurantId = :restaurantId 
            AND status = 'POSTED' 
            ORDER BY postedAt DESC, id ASC
            LIMIT :limit
        )
        ORDER BY pr.postedAt DESC, pr.id ASC
    """)
    fun observeRecentPurchaseActivity(
        restaurantId: String,
        limit: Int
    ): Flow<List<RecentPurchaseActivityRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: PurchaseReceiptEntity)

    @Upsert
    suspend fun upsertReceipt(receipt: PurchaseReceiptEntity)

    @Update
    suspend fun updateReceipt(receipt: PurchaseReceiptEntity): Int

    @Query("DELETE FROM purchase_receipts WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraftReceipt(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLine(line: PurchaseLineEntity)

    @Update
    suspend fun updateLine(line: PurchaseLineEntity): Int

    @Query("DELETE FROM purchase_lines WHERE id = :id")
    suspend fun deleteLine(id: String): Int

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

    @Query("SELECT * FROM purchase_receipts WHERE restaurantId = :restaurantId AND invoiceNumber = :invoiceNumber")
    suspend fun findByInvoiceNumber(restaurantId: String, invoiceNumber: String): List<PurchaseReceiptEntity>

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
