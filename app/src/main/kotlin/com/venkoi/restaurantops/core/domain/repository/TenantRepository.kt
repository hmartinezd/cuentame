package com.venkoi.restaurantops.core.domain.repository

import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess
import com.venkoi.restaurantops.core.domain.model.tenant.TenantBootstrapResult

interface TenantRepository {
    suspend fun getAccessibleRestaurants(): Result<List<RestaurantAccess>>

    suspend fun createOrganizationWithRestaurant(
        organizationName: String,
        restaurantName: String,
        currencyCode: String,
        timezone: String,
        localeTag: String
    ): Result<TenantBootstrapResult>
}

class TenantOperationException : Exception("Tenant operation failed")
