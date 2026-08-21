package com.venkoi.cuentame.core.domain.repository
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.model.salesimport.*
import kotlinx.coroutines.flow.Flow
interface SalesImportRepository { fun observeImports(restaurantId:RestaurantId):Flow<List<SalesImport>>; suspend fun getImport(exportId:String):SalesImportDetail?; suspend fun getTransaction(terminalId:String,transactionId:String):ImportedSaleTransactionDetail? }
