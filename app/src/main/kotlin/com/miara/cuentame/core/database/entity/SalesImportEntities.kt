package com.miara.cuentame.core.database.entity

import androidx.room.*
import java.math.BigDecimal

@Entity(tableName="sales_imports",foreignKeys=[ForeignKey(entity=RestaurantEntity::class,parentColumns=["id"],childColumns=["restaurantId"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=MenuPublicationEntity::class,parentColumns=["id"],childColumns=["menuPackageId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("restaurantId"),Index("menuPackageId")])
data class SalesImportEntity(@PrimaryKey val exportId:String,val originalSha256:String,val restaurantId:String,val terminalId:String,val generatedAt:Long,val businessDate:String,val menuPackageId:String,val menuId:String,val publicationRevision:Long,val currency:String,val importedAt:Long)

@Entity(tableName="imported_sale_transactions",primaryKeys=["terminalId","transactionId"],foreignKeys=[ForeignKey(entity=RestaurantEntity::class,parentColumns=["id"],childColumns=["restaurantId"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=MenuPublicationEntity::class,parentColumns=["id"],childColumns=["menuPackageId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("restaurantId"),Index("menuPackageId"),Index("firstSeenExportId"),Index("lastSeenExportId")])
data class ImportedSaleTransactionEntity(val terminalId:String,val transactionId:String,val restaurantId:String,val menuPackageId:String,val menuId:String,val publicationRevision:Long,val businessDate:String,val currency:String,val openedAt:Long,val closedAt:Long,val status:String,val firstSeenExportId:String,val firstImportedAt:Long,val lastSeenExportId:String,val lastSeenGeneratedAt:Long)

@Entity(tableName="imported_sale_lines",primaryKeys=["terminalId","saleLineId"],foreignKeys=[ForeignKey(entity=ImportedSaleTransactionEntity::class,parentColumns=["terminalId","transactionId"],childColumns=["terminalId","transactionId"],onDelete=ForeignKey.CASCADE)],indices=[Index(value=["terminalId","transactionId"])])
data class ImportedSaleLineEntity(val terminalId:String,val saleLineId:String,val transactionId:String,val sellableItemId:String,val displayNameSnapshot:String,val quantity:BigDecimal,val unitPrice:BigDecimal,val gross:BigDecimal,val discount:BigDecimal,val net:BigDecimal,val commercialRevision:Long,val consumptionRevision:Long)

@Entity(tableName="sales_import_transaction_refs",primaryKeys=["exportId","terminalId","transactionId"],foreignKeys=[ForeignKey(entity=SalesImportEntity::class,parentColumns=["exportId"],childColumns=["exportId"],onDelete=ForeignKey.CASCADE),ForeignKey(entity=ImportedSaleTransactionEntity::class,parentColumns=["terminalId","transactionId"],childColumns=["terminalId","transactionId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["terminalId","transactionId"])])
data class SalesImportTransactionRefEntity(val exportId:String,val terminalId:String,val transactionId:String)
