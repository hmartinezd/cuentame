package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.dao.SalesImportDao
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.domain.repository.SalesImportRepository
import com.miara.cuentame.core.model.salesimport.*
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class RoomSalesImportRepository @Inject constructor(private val dao:SalesImportDao):SalesImportRepository {
 override fun observeImports(restaurantId:RestaurantId)=dao.observeImports(restaurantId.value).map{it.map(SalesImportEntity::domain)}
 override suspend fun getImport(exportId:String):SalesImportDetail? { val i=dao.getImport(exportId)?:return null; return SalesImportDetail(i.domain(),dao.getTransactionsForImport(exportId).map{t->ImportedSaleTransactionDetail(t.domain(),dao.getLines(t.terminalId,t.transactionId).map(ImportedSaleLineEntity::domain))}) }
 override suspend fun getTransaction(terminalId:String,transactionId:String):ImportedSaleTransactionDetail? { val t=dao.getTransaction(terminalId,transactionId)?:return null;return ImportedSaleTransactionDetail(t.domain(),dao.getLines(terminalId,transactionId).map(ImportedSaleLineEntity::domain)) }
}
internal fun SalesImportEntity.domain()=SalesImport(exportId,originalSha256,restaurantId,terminalId,Instant.ofEpochMilli(generatedAt),LocalDate.parse(businessDate),menuPackageId,menuId,publicationRevision,currency,Instant.ofEpochMilli(importedAt))
internal fun ImportedSaleTransactionEntity.domain()=ImportedSaleTransaction(terminalId,transactionId,restaurantId,menuPackageId,menuId,publicationRevision,LocalDate.parse(businessDate),currency,Instant.ofEpochMilli(openedAt),Instant.ofEpochMilli(closedAt),ImportedSaleStatus.valueOf(status),firstSeenExportId,Instant.ofEpochMilli(firstImportedAt),lastSeenExportId,Instant.ofEpochMilli(lastSeenGeneratedAt))
internal fun ImportedSaleLineEntity.domain()=ImportedSaleLine(terminalId,saleLineId,transactionId,sellableItemId,displayNameSnapshot,quantity,unitPrice,gross,discount,net,commercialRevision,consumptionRevision)
