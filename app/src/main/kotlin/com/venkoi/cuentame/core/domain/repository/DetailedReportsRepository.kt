package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.domain.service.ReportingPeriod
import com.venkoi.cuentame.core.model.dashboard.InventoryDetailReport
import com.venkoi.cuentame.core.model.dashboard.PurchaseDetailReport
import com.venkoi.cuentame.core.model.dashboard.WasteDetailReport
import kotlinx.coroutines.flow.Flow

interface DetailedReportsRepository {
    fun observeInventoryDetails(
        restaurantId: RestaurantId
    ): Flow<InventoryDetailReport>

    fun observePurchaseDetails(
        restaurantId: RestaurantId,
        period: ReportingPeriod
    ): Flow<PurchaseDetailReport>

    fun observeWasteDetails(
        restaurantId: RestaurantId,
        period: ReportingPeriod
    ): Flow<WasteDetailReport>

    fun observePurchaseExportRows(
        restaurantId: RestaurantId,
        period: ReportingPeriod
    ): Flow<List<PurchaseExportRow>>
}

data class PurchaseExportRow(
    val purchaseDate: Long,
    val supplierName: String?,
    val invoiceNumber: String?,
    val ingredientName: String,
    val quantityEntered: String,
    val purchaseUnitLabel: String?,
    val quantityBase: String,
    val baseUnitSymbol: String,
    val unitCostBase: String,
    val lineTotal: String,
    val status: String
)
