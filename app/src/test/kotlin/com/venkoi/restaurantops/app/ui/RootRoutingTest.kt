package com.venkoi.restaurantops.app.ui

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.RestoreStartupState
import com.venkoi.restaurantops.core.domain.model.auth.AuthUser
import com.venkoi.restaurantops.core.domain.model.startup.SaaSStartupState
import com.venkoi.restaurantops.core.domain.model.tenant.MembershipRole
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantSummary
import com.venkoi.restaurantops.core.domain.usecase.AppStartState
import org.junit.Test

class RootRoutingTest {
    @Test fun `requires authentication routes auth content`() { assertRoute(SaaSStartupState.RequiresAuthentication, AppStartState.RequiresOnboarding, RootDestination.AUTH) }
    @Test fun `ready online and local ready routes main`() { assertRoute(SaaSStartupState.ReadyOnline(USER, ACCESS), AppStartState.Ready, RootDestination.MAIN) }
    @Test fun `ready offline and local ready routes main`() { assertRoute(SaaSStartupState.ReadyOffline, AppStartState.Ready, RootDestination.MAIN) }
    @Test fun `recovery required overrides authentication`() {
        assertThat(resolveRootDestination(RestoreStartupState.RecoveryRequired, SaaSStartupState.RequiresAuthentication, AppStartState.Ready)).isEqualTo(RootDestination.RECOVERY_REQUIRED)
    }
    @Test fun `tenant setup routes tenant setup UI`() { assertRoute(SaaSStartupState.RequiresTenantSetup(USER), AppStartState.RequiresOnboarding, RootDestination.TENANT_SETUP) }
    @Test fun `local setup routes cloud backed onboarding`() { assertRoute(SaaSStartupState.RequiresLocalSetup(USER, ACCESS), AppStartState.RequiresOnboarding, RootDestination.CLOUD_LOCAL_SETUP) }
    @Test fun `device revoked blocks main`() { assertRoute(SaaSStartupState.DeviceRevoked, AppStartState.Ready, RootDestination.DEVICE_REVOKED) }

    private fun assertRoute(saas: SaaSStartupState, local: AppStartState, expected: RootDestination) {
        assertThat(resolveRootDestination(RestoreStartupState.Ready, saas, local)).isEqualTo(expected)
    }
    private companion object {
        val USER = AuthUser("user", "owner@example.com")
        val ACCESS = RestaurantAccess(RestaurantSummary("restaurant", "organization", "Restaurant", "USD", "America/New_York", "en-US"), MembershipRole.OWNER)
    }
}
