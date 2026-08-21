package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.model.dashboard.DashboardDateRange
import com.venkoi.restaurantops.core.model.dashboard.DashboardSnapshot
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun observeDashboard(
        restaurantId: RestaurantId,
        range: DashboardDateRange
    ): Flow<DashboardSnapshot>
}
