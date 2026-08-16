package com.miara.cuentame.core.model.salesimport

import com.miara.cuentame.core.model.salesexport.SalesExportV1
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class ImportedSaleStatus { COMPLETED, VOIDED }

data class SalesImport(
    val exportId:String,val originalSha256:String,val restaurantId:String,val terminalId:String,
    val generatedAt:Instant,val businessDate:LocalDate,val menuPackageId:String,val menuId:String,
    val publicationRevision:Long,val currency:String,val importedAt:Instant
)
data class ImportedSaleLine(val terminalId:String,val saleLineId:String,val transactionId:String,val sellableItemId:String,val displayNameSnapshot:String,val quantity:BigDecimal,val unitPrice:BigDecimal,val gross:BigDecimal,val discount:BigDecimal,val net:BigDecimal,val commercialRevision:Long,val consumptionRevision:Long)
data class ImportedSaleTransaction(val terminalId:String,val transactionId:String,val restaurantId:String,val menuPackageId:String,val menuId:String,val publicationRevision:Long,val businessDate:LocalDate,val currency:String,val openedAt:Instant,val closedAt:Instant,val status:ImportedSaleStatus,val firstSeenExportId:String,val firstImportedAt:Instant,val lastSeenExportId:String,val lastSeenGeneratedAt:Instant)
data class ImportedSaleTransactionDetail(val transaction:ImportedSaleTransaction,val lines:List<ImportedSaleLine>)
data class SalesImportDetail(val salesImport:SalesImport,val transactions:List<ImportedSaleTransactionDetail>)

data class PreparedSalesImport internal constructor(
    internal val export:SalesExportV1,
    val exportId:String,val originalSha256:String,val terminalId:String,val businessDate:LocalDate,
    val generatedAt:Instant,val menuPackageId:String,val menuId:String,val publicationRevision:Long,val currency:String,
    val transactionCount:Int,val completedCount:Int,val voidedCount:Int,val lineCount:Int,
    val completedGross:BigDecimal,val completedDiscount:BigDecimal,val completedNet:BigDecimal
)

enum class SalesImportFailureCode { FILE_TOO_LARGE, INVALID_UTF8, INVALID_JSON, INVALID_SALES_EXPORT, WRONG_RESTAURANT, UNKNOWN_MENU_PACKAGE, PUBLICATION_RESTAURANT_MISMATCH, MENU_MISMATCH, PUBLICATION_REVISION_MISMATCH, CURRENCY_MISMATCH, UNKNOWN_SELLABLE_ITEM, COMMERCIAL_REVISION_MISMATCH, CONSUMPTION_REVISION_MISMATCH, ITEM_NAME_MISMATCH, ITEM_PRICE_MISMATCH, EXPORT_ID_CONFLICT, TRANSACTION_CONFLICT, LINE_CONFLICT, STALE_TRANSACTION_STATE, PERSISTENCE_FAILURE }
data class SalesImportFailure(val code:SalesImportFailureCode,val detail:String?=null)
sealed interface SalesImportPreparationResult { data class Ready(val prepared:PreparedSalesImport):SalesImportPreparationResult; data class Duplicate(val existing:SalesImport):SalesImportPreparationResult; data class Failure(val failure:SalesImportFailure):SalesImportPreparationResult }
sealed interface SalesImportCommitResult { data class Imported(val detail:SalesImportDetail):SalesImportCommitResult; data class Duplicate(val existing:SalesImport):SalesImportCommitResult; data class Failure(val failure:SalesImportFailure):SalesImportCommitResult }
