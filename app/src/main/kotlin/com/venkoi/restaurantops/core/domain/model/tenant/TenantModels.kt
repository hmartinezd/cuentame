package com.venkoi.restaurantops.core.domain.model.tenant

data class OrganizationSummary(
    val id: String,
    val name: String
)

data class RestaurantSummary(
    val id: String,
    val organizationId: String,
    val name: String,
    val currencyCode: String,
    val timezone: String,
    val localeTag: String
)

enum class MembershipRole {
    OWNER,
    MANAGER,
    STAFF
}

data class RestaurantAccess(
    val restaurant: RestaurantSummary,
    val role: MembershipRole
)

data class TenantBootstrapResult(
    val organizationId: String,
    val restaurantId: String
)
