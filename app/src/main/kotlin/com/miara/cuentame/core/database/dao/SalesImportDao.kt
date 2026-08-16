package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao interface SalesImportDao {
 @Query("SELECT * FROM sales_imports WHERE exportId=:id") suspend fun getImport(id:String):SalesImportEntity?
 @Query("SELECT * FROM sales_imports WHERE restaurantId=:restaurantId ORDER BY importedAt DESC, exportId") fun observeImports(restaurantId:String):Flow<List<SalesImportEntity>>
 @Query("SELECT * FROM imported_sale_transactions WHERE terminalId=:terminalId AND transactionId=:transactionId") suspend fun getTransaction(terminalId:String,transactionId:String):ImportedSaleTransactionEntity?
 @Query("SELECT * FROM imported_sale_lines WHERE terminalId=:terminalId AND transactionId=:transactionId ORDER BY saleLineId") suspend fun getLines(terminalId:String,transactionId:String):List<ImportedSaleLineEntity>
 @Query("SELECT t.* FROM imported_sale_transactions t JOIN sales_import_transaction_refs r ON r.terminalId=t.terminalId AND r.transactionId=t.transactionId WHERE r.exportId=:exportId ORDER BY t.openedAt,t.transactionId") suspend fun getTransactionsForImport(exportId:String):List<ImportedSaleTransactionEntity>
 @Insert suspend fun insertImport(value:SalesImportEntity)
 @Insert suspend fun insertTransaction(value:ImportedSaleTransactionEntity)
 @Update suspend fun updateTransaction(value:ImportedSaleTransactionEntity)
 @Insert suspend fun insertLines(values:List<ImportedSaleLineEntity>)
 @Insert suspend fun insertRefs(values:List<SalesImportTransactionRefEntity>)
 @Query("SELECT * FROM imported_sale_lines WHERE terminalId=:terminalId AND saleLineId IN (:ids)") suspend fun getLinesByIds(terminalId:String,ids:List<String>):List<ImportedSaleLineEntity>
}
