package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.model.dashboard.DashboardDateRange
import com.venkoi.cuentame.core.model.dashboard.DashboardSnapshot
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun observeDashboard(
        restaurantId: RestaurantId,
        range: DashboardDateRange
    ): Flow<DashboardSnapshot>
}
