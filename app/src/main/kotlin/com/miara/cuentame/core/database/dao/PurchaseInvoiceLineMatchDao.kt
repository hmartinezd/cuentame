package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.PurchaseInvoiceLineMatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseInvoiceLineMatchDao {

    @Query("SELECT * FROM purchase_invoice_line_matches WHERE parseResultId = :parseResultId ORDER BY lineIndex ASC")
    fun observeMatchesForParseResult(parseResultId: String): Flow<List<PurchaseInvoiceLineMatchEntity>>

    @Query("SELECT * FROM purchase_invoice_line_matches WHERE parseResultId = :parseResultId ORDER BY lineIndex ASC")
    suspend fun getMatchesForParseResult(parseResultId: String): List<PurchaseInvoiceLineMatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<PurchaseInvoiceLineMatchEntity>)

    @Query("DELETE FROM purchase_invoice_line_matches WHERE parseResultId = :parseResultId")
    suspend fun deleteMatchesForParseResult(parseResultId: String)
    
    @Query("DELETE FROM purchase_invoice_line_matches WHERE parseResultId = :parseResultId AND lineIndex = :lineIndex")
    suspend fun deleteMatch(parseResultId: String, lineIndex: Int)
}
