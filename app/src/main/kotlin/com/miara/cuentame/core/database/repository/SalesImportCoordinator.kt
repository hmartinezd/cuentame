package com.miara.cuentame.core.database.repository

import androidx.room.withTransaction
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.dao.*
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.salesexport.*
import com.miara.cuentame.core.model.salesimport.*
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

const val MAX_SALES_EXPORT_BYTES:Int=16*1024*1024

@Singleton class SalesImportCoordinator @Inject constructor(private val database:RestaurantInventoryDatabase,private val restaurantDao:RestaurantDao,private val publicationDao:MenuPublicationDao,private val salesDao:SalesImportDao,private val timeProvider:TimeProvider) {
 suspend fun prepare(restaurantId:RestaurantId,rawBytes:ByteArray):SalesImportPreparationResult {
  if(rawBytes.size>MAX_SALES_EXPORT_BYTES)return failP(SalesImportFailureCode.FILE_TOO_LARGE)
  val hash=MessageDigest.getInstance("SHA-256").digest(rawBytes).joinToString(""){"%02x".format(it)}
  val text=try{StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(rawBytes)).toString()}catch(_:Exception){return failP(SalesImportFailureCode.INVALID_UTF8)}
  val export=when(val decoded=SalesExportJsonCodec.decodeAndValidate(text)){is SalesExportDecodeResult.Success->decoded.value;is SalesExportDecodeResult.InvalidJson->return failP(SalesImportFailureCode.INVALID_JSON);is SalesExportDecodeResult.InvalidExport->return failP(SalesImportFailureCode.INVALID_SALES_EXPORT,decoded.failure.code.name)}
  validateProvenance(restaurantId,export)?.let{return SalesImportPreparationResult.Failure(it)}
  val existing=salesDao.getImport(export.exportId)
  if(existing!=null)return if(existing.originalSha256==hash)SalesImportPreparationResult.Duplicate(existing.domain())else failP(SalesImportFailureCode.EXPORT_ID_CONFLICT)
  analyzeDurable(export)?.let{return SalesImportPreparationResult.Failure(it)}
  val completed=export.transactions.filter{it.status=="COMPLETED"}.flatMap{it.lines}
  return SalesImportPreparationResult.Ready(PreparedSalesImport(export,export.exportId,hash,export.terminalId,LocalDate.parse(export.businessDate),Instant.parse(export.generatedAt),export.menuPackageId,export.menuId,export.publicationRevision,export.currency,export.transactions.size,export.transactions.count{it.status=="COMPLETED"},export.transactions.count{it.status=="VOIDED"},export.transactions.sumOf{it.lines.size},completed.sumDecimal{it.gross},completed.sumDecimal{it.discount},completed.sumDecimal{it.net}))
 }

 suspend fun commit(prepared:PreparedSalesImport):SalesImportCommitResult=try { database.withTransaction {
  val export=prepared.export
  validateProvenance(RestaurantId(export.restaurantId),export)?.let{return@withTransaction SalesImportCommitResult.Failure(it)}
  salesDao.getImport(export.exportId)?.let{return@withTransaction if(it.originalSha256==prepared.originalSha256)SalesImportCommitResult.Duplicate(it.domain())else failC(SalesImportFailureCode.EXPORT_ID_CONFLICT)}
  analyzeDurable(export)?.let{return@withTransaction SalesImportCommitResult.Failure(it)}
  val importedAt=timeProvider.now().toEpochMilli();val generatedAt=Instant.parse(export.generatedAt).toEpochMilli()
  salesDao.insertImport(SalesImportEntity(export.exportId,prepared.originalSha256,export.restaurantId,export.terminalId,generatedAt,export.businessDate,export.menuPackageId,export.menuId,export.publicationRevision,export.currency,importedAt))
  for(t in export.transactions){
   val old=salesDao.getTransaction(export.terminalId,t.transactionId)
   if(old==null){salesDao.insertTransaction(t.entity(export,importedAt,generatedAt));salesDao.insertLines(t.lines.map{it.entity(export.terminalId,t.transactionId)})}
   else if(old.status=="COMPLETED"&&t.status=="VOIDED")salesDao.updateTransaction(old.copy(status="VOIDED",lastSeenExportId=export.exportId,lastSeenGeneratedAt=generatedAt))
   else if(generatedAt>=old.lastSeenGeneratedAt)salesDao.updateTransaction(old.copy(lastSeenExportId=export.exportId,lastSeenGeneratedAt=generatedAt))
  }
  salesDao.insertRefs(export.transactions.map{SalesImportTransactionRefEntity(export.exportId,export.terminalId,it.transactionId)})
  val imported=checkNotNull(salesDao.getImport(export.exportId))
  SalesImportCommitResult.Imported(SalesImportDetail(imported.domain(),salesDao.getTransactionsForImport(export.exportId).map{t->ImportedSaleTransactionDetail(t.domain(),salesDao.getLines(t.terminalId,t.transactionId).map(ImportedSaleLineEntity::domain))}))
 }}catch(e:CancellationException){throw e}catch(_:Exception){failC(SalesImportFailureCode.PERSISTENCE_FAILURE)}

 private suspend fun validateProvenance(target:RestaurantId,e:SalesExportV1):SalesImportFailure? {
  if(e.restaurantId!=target.value||restaurantDao.getById(target.value)==null)return SalesImportFailure(SalesImportFailureCode.WRONG_RESTAURANT)
  val p=publicationDao.getPublication(e.menuPackageId)?:return SalesImportFailure(SalesImportFailureCode.UNKNOWN_MENU_PACKAGE)
  if(p.restaurantId!=e.restaurantId)return SalesImportFailure(SalesImportFailureCode.PUBLICATION_RESTAURANT_MISMATCH)
  if(p.sourceMenuId!=e.menuId)return SalesImportFailure(SalesImportFailureCode.MENU_MISMATCH)
  if(p.publicationRevision!=e.publicationRevision)return SalesImportFailure(SalesImportFailureCode.PUBLICATION_REVISION_MISMATCH)
  if(p.currencyCodeSnapshot!=e.currency)return SalesImportFailure(SalesImportFailureCode.CURRENCY_MISMATCH)
  val items=publicationDao.getItems(p.id).associateBy{it.menuRecipeId}
  for(line in e.transactions.flatMap{it.lines}){val item=items[line.sellableItemId]?:return SalesImportFailure(SalesImportFailureCode.UNKNOWN_SELLABLE_ITEM,line.sellableItemId);if(item.commercialRevision!=line.commercialRevision)return SalesImportFailure(SalesImportFailureCode.COMMERCIAL_REVISION_MISMATCH,line.saleLineId);if(item.consumptionRevision!=line.consumptionRevision)return SalesImportFailure(SalesImportFailureCode.CONSUMPTION_REVISION_MISMATCH,line.saleLineId);if(item.displayNameSnapshot!=line.displayNameSnapshot)return SalesImportFailure(SalesImportFailureCode.ITEM_NAME_MISMATCH,line.saleLineId);if(item.sellingPriceSnapshot.compareTo(BigDecimal(line.unitPrice))!=0)return SalesImportFailure(SalesImportFailureCode.ITEM_PRICE_MISMATCH,line.saleLineId)}
  return null
 }
 private suspend fun analyzeDurable(e:SalesExportV1):SalesImportFailure? { val generated=Instant.parse(e.generatedAt).toEpochMilli();for(t in e.transactions){
  val old=salesDao.getTransaction(e.terminalId,t.transactionId);if(old!=null){if(!old.compatible(e,t))return SalesImportFailure(SalesImportFailureCode.TRANSACTION_CONFLICT,t.transactionId);val oldLines=salesDao.getLines(e.terminalId,t.transactionId);if(oldLines.size!=t.lines.size)return SalesImportFailure(SalesImportFailureCode.TRANSACTION_CONFLICT,t.transactionId);for(line in t.lines){val durable=oldLines.find{it.saleLineId==line.saleLineId}?:return SalesImportFailure(SalesImportFailureCode.TRANSACTION_CONFLICT,t.transactionId);if(!durable.same(line,t.transactionId))return SalesImportFailure(SalesImportFailureCode.TRANSACTION_CONFLICT,t.transactionId)};if(generated<old.lastSeenGeneratedAt&&old.status!=t.status)return SalesImportFailure(SalesImportFailureCode.STALE_TRANSACTION_STATE,t.transactionId);if(old.status=="VOIDED"&&t.status=="COMPLETED")return SalesImportFailure(SalesImportFailureCode.STALE_TRANSACTION_STATE,t.transactionId)}
  for(line in t.lines){val collision=salesDao.getLinesByIds(e.terminalId,listOf(line.saleLineId)).firstOrNull();if(collision!=null&&!collision.same(line,t.transactionId))return SalesImportFailure(SalesImportFailureCode.LINE_CONFLICT,line.saleLineId)}
 };return null }
}
private fun ImportedSaleTransactionEntity.compatible(e:SalesExportV1,t:SalesExportTransactionV1)=restaurantId==e.restaurantId&&menuPackageId==e.menuPackageId&&menuId==e.menuId&&publicationRevision==e.publicationRevision&&businessDate==e.businessDate&&currency==e.currency&&openedAt==Instant.parse(t.openedAt).toEpochMilli()&&closedAt==Instant.parse(t.closedAt).toEpochMilli()&&(status==t.status||(status=="COMPLETED"&&t.status=="VOIDED")||(status=="VOIDED"&&t.status=="COMPLETED"))
private fun ImportedSaleLineEntity.same(x:SalesExportLineV1,tx:String)=transactionId==tx&&sellableItemId==x.sellableItemId&&displayNameSnapshot==x.displayNameSnapshot&&quantity.compareTo(BigDecimal(x.quantity))==0&&unitPrice.compareTo(BigDecimal(x.unitPrice))==0&&gross.compareTo(BigDecimal(x.gross))==0&&discount.compareTo(BigDecimal(x.discount))==0&&net.compareTo(BigDecimal(x.net))==0&&commercialRevision==x.commercialRevision&&consumptionRevision==x.consumptionRevision
private fun SalesExportTransactionV1.entity(e:SalesExportV1,now:Long,generated:Long)=ImportedSaleTransactionEntity(e.terminalId,transactionId,e.restaurantId,e.menuPackageId,e.menuId,e.publicationRevision,e.businessDate,e.currency,Instant.parse(openedAt).toEpochMilli(),Instant.parse(closedAt).toEpochMilli(),status,e.exportId,now,e.exportId,generated)
private fun SalesExportLineV1.entity(terminal:String,tx:String)=ImportedSaleLineEntity(terminal,saleLineId,tx,sellableItemId,displayNameSnapshot,BigDecimal(quantity),BigDecimal(unitPrice),BigDecimal(gross),BigDecimal(discount),BigDecimal(net),commercialRevision,consumptionRevision)
private fun List<SalesExportLineV1>.sumDecimal(selector:(SalesExportLineV1)->String)=fold(BigDecimal.ZERO){a,v->a+BigDecimal(selector(v))}
private fun failP(c:SalesImportFailureCode,d:String?=null)=SalesImportPreparationResult.Failure(SalesImportFailure(c,d))
private fun failC(c:SalesImportFailureCode)=SalesImportCommitResult.Failure(SalesImportFailure(c))
