package com.miara.cuentame.core.model.salesexport

import kotlinx.serialization.Serializable

@Serializable
data class SalesExportV1(
    val format: String,
    val formatVersion: Int,
    val exportId: String,
    val terminalId: String,
    val restaurantId: String,
    val generatedAt: String,
    val businessDate: String,
    val menuPackageId: String,
    val menuId: String,
    val publicationRevision: Long,
    val currency: String,
    val transactions: List<SalesExportTransactionV1>
)

@Serializable
data class SalesExportTransactionV1(
    val transactionId: String,
    val openedAt: String,
    val closedAt: String,
    val status: String,
    val lines: List<SalesExportLineV1>
)

@Serializable
data class SalesExportLineV1(
    val saleLineId: String,
    val sellableItemId: String,
    val displayNameSnapshot: String,
    val quantity: String,
    val unitPrice: String,
    val gross: String,
    val discount: String,
    val net: String,
    val commercialRevision: Long,
    val consumptionRevision: Long
)

const val SALES_EXPORT_FORMAT = "cuentame-sales-export"
const val SALES_EXPORT_FORMAT_VERSION = 1

