package com.venkoi.restaurantops.core.cloud.tenant

import com.venkoi.restaurantops.core.domain.model.tenant.MembershipRole
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantSummary
import com.venkoi.restaurantops.core.domain.model.tenant.TenantBootstrapResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RestaurantMembershipDto(
    @SerialName("restaurant_id")
    val restaurantId: String,
    val role: String
)

@Serializable
internal data class RestaurantDto(
    val id: String,
    @SerialName("organization_id")
    val organizationId: String,
    val name: String,
    @SerialName("currency_code")
    val currencyCode: String,
    val timezone: String,
    @SerialName("locale_tag")
    val localeTag: String
)

@Serializable
internal data class TenantBootstrapRpcDto(
    @SerialName("organization_id")
    val organizationId: String,
    @SerialName("restaurant_id")
    val restaurantId: String
)

internal fun String.toMembershipRole(): MembershipRole = when (this) {
    "OWNER" -> MembershipRole.OWNER
    "MANAGER" -> MembershipRole.MANAGER
    "STAFF" -> MembershipRole.STAFF
    else -> throw IllegalArgumentException("Unknown restaurant membership role")
}

internal fun RestaurantDto.toDomain(): RestaurantSummary = RestaurantSummary(
    id = id,
    organizationId = organizationId,
    name = name,
    currencyCode = currencyCode,
    timezone = timezone,
    localeTag = localeTag
)

internal fun TenantBootstrapRpcDto.toDomain(): TenantBootstrapResult = TenantBootstrapResult(
    organizationId = organizationId,
    restaurantId = restaurantId
)
