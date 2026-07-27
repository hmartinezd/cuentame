package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.service.ReportingPeriod
import com.miara.cuentame.core.model.dashboard.InventoryDetailReport
import com.miara.cuentame.core.model.dashboard.PurchaseDetailReport
import com.miara.cuentame.core.model.dashboard.WasteDetailReport
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
}
