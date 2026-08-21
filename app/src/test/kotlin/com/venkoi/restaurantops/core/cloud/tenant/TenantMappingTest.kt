package com.venkoi.restaurantops.core.cloud.tenant

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.domain.model.tenant.MembershipRole
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantSummary
import com.venkoi.restaurantops.core.domain.model.tenant.TenantBootstrapResult
import org.junit.Assert.assertThrows
import org.junit.Test

class TenantMappingTest {

    @Test
    fun `owner role maps exactly`() {
        assertThat("OWNER".toMembershipRole()).isEqualTo(MembershipRole.OWNER)
    }

    @Test
    fun `manager role maps exactly`() {
        assertThat("MANAGER".toMembershipRole()).isEqualTo(MembershipRole.MANAGER)
    }

    @Test
    fun `staff role maps exactly`() {
        assertThat("STAFF".toMembershipRole()).isEqualTo(MembershipRole.STAFF)
    }

    @Test
    fun `unknown role fails safely`() {
        assertThrows(IllegalArgumentException::class.java) {
            "UNKNOWN".toMembershipRole()
        }
    }

    @Test
    fun `restaurant dto maps to domain summary`() {
        val dto = restaurantDto()

        assertThat(dto.toDomain()).isEqualTo(
            RestaurantSummary(
                id = "restaurant-1",
                organizationId = "organization-1",
                name = "Test Restaurant",
                currencyCode = "USD",
                timezone = "America/New_York",
                localeTag = "en-US"
            )
        )
    }

    @Test
    fun `bootstrap dto maps both returned identifiers`() {
        val dto = TenantBootstrapRpcDto(
            organizationId = "organization-1",
            restaurantId = "restaurant-1"
        )

        assertThat(dto.toDomain()).isEqualTo(
            TenantBootstrapResult(
                organizationId = "organization-1",
                restaurantId = "restaurant-1"
            )
        )
    }

    @Test
    fun `access mapping fails when membership role is unknown`() {
        val membership = RestaurantMembershipDto(
            restaurantId = "restaurant-1",
            role = "SUPERUSER"
        )

        assertThrows(IllegalArgumentException::class.java) {
            mapRestaurantAccess(listOf(membership), listOf(restaurantDto()))
        }
    }

    private fun restaurantDto() = RestaurantDto(
        id = "restaurant-1",
        organizationId = "organization-1",
        name = "Test Restaurant",
        currencyCode = "USD",
        timezone = "America/New_York",
        localeTag = "en-US"
    )
}
