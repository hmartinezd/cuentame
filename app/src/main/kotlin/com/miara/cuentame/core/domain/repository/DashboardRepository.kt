package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.model.dashboard.DashboardDateRange
import com.miara.cuentame.core.model.dashboard.DashboardSnapshot
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun observeDashboard(
        restaurantId: RestaurantId,
        range: DashboardDateRange
    ): Flow<DashboardSnapshot>
}
