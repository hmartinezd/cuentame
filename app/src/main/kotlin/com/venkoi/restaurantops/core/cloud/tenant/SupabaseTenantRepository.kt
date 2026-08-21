package com.venkoi.restaurantops.core.cloud.tenant

import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess
import com.venkoi.restaurantops.core.domain.model.tenant.TenantBootstrapResult
import com.venkoi.restaurantops.core.domain.repository.TenantOperationException
import com.venkoi.restaurantops.core.domain.repository.TenantRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SupabaseTenantRepository @Inject constructor(
    private val supabase: SupabaseClient
) : TenantRepository {

    override suspend fun getAccessibleRestaurants(): Result<List<RestaurantAccess>> =
        tenantOperation {
            val memberships = supabase.from("restaurant_memberships")
                .select()
                .decodeList<RestaurantMembershipDto>()

            if (memberships.isEmpty()) return@tenantOperation emptyList()

            val restaurantIds = memberships.map { it.restaurantId }.distinct()
            val restaurants = supabase.from("restaurants")
                .select {
                    filter {
                        isIn("id", restaurantIds)
                    }
                }
                .decodeList<RestaurantDto>()

            mapRestaurantAccess(memberships, restaurants)
        }

    override suspend fun createOrganizationWithRestaurant(
        organizationName: String,
        restaurantName: String,
        currencyCode: String,
        timezone: String,
        localeTag: String
    ): Result<TenantBootstrapResult> = tenantOperation {
        val parameters = buildJsonObject {
            put("organization_name", organizationName)
            put("restaurant_name", restaurantName)
            put("currency_code", currencyCode)
            put("timezone", timezone)
            put("locale_tag", localeTag)
        }

        supabase.postgrest
            .rpc(
                function = "create_organization_with_restaurant",
                parameters = parameters
            )
            .decodeSingle<TenantBootstrapRpcDto>()
            .toDomain()
    }
}

internal fun mapRestaurantAccess(
    memberships: List<RestaurantMembershipDto>,
    restaurants: List<RestaurantDto>
): List<RestaurantAccess> {
    val restaurantsById = restaurants.associateBy { it.id }
    require(restaurantsById.size == restaurants.size) { "Duplicate restaurant response" }

    return memberships.map { membership ->
        val restaurant = requireNotNull(restaurantsById[membership.restaurantId]) {
            "Membership restaurant was not returned"
        }
        RestaurantAccess(
            restaurant = restaurant.toDomain(),
            role = membership.role.toMembershipRole()
        )
    }
}

private suspend inline fun <T> tenantOperation(
    crossinline operation: suspend () -> T
): Result<T> = try {
    Result.success(operation())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    Result.failure(TenantOperationException())
}
