package com.venkoi.restaurantops.core.domain.model.startup

import com.venkoi.restaurantops.core.domain.model.auth.AuthUser
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess

sealed interface SaaSStartupState {
    data object Loading : SaaSStartupState
    data object RequiresAuthentication : SaaSStartupState
    data class RequiresTenantSetup(val user: AuthUser) : SaaSStartupState
    data class RequiresLocalSetup(
        val user: AuthUser,
        val restaurantAccess: RestaurantAccess
    ) : SaaSStartupState
    data class ReadyOnline(
        val user: AuthUser,
        val restaurantAccess: RestaurantAccess
    ) : SaaSStartupState
    data object ReadyOffline : SaaSStartupState
    data object DeviceRevoked : SaaSStartupState
    data object NetworkRequired : SaaSStartupState
    data object TenantAccessMismatch : SaaSStartupState
    data object MultipleRestaurantsUnsupported : SaaSStartupState
    data object Error : SaaSStartupState
}
