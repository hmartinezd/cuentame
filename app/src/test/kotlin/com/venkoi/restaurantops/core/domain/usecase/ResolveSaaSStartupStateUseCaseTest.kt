package com.venkoi.restaurantops.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.auth.AuthSessionState
import com.venkoi.restaurantops.core.domain.model.auth.AuthUser
import com.venkoi.restaurantops.core.domain.model.device.DeviceInstallation
import com.venkoi.restaurantops.core.domain.model.startup.SaaSStartupState
import com.venkoi.restaurantops.core.domain.model.tenant.MembershipRole
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantSummary
import com.venkoi.restaurantops.core.domain.model.tenant.TenantBootstrapResult
import com.venkoi.restaurantops.core.domain.repository.AuthRepository
import com.venkoi.restaurantops.core.domain.repository.CompleteLocalSetupCommand
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRepository
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRevokedException
import com.venkoi.restaurantops.core.domain.repository.LocalSetupRepository
import com.venkoi.restaurantops.core.domain.repository.LocalSetupResult
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.domain.repository.TenantRepository
import com.venkoi.restaurantops.core.domain.startup.SaaSStartupRefresh
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

class ResolveSaaSStartupStateUseCaseTest {

    @Test
    fun `initializing is loading`() = runTest {
        assertThat(fixture(auth = AuthSessionState.Initializing).resolve())
            .isEqualTo(SaaSStartupState.Loading)
    }

    @Test
    fun `signed out requires authentication`() = runTest {
        assertThat(fixture(auth = AuthSessionState.SignedOut).resolve())
            .isEqualTo(SaaSStartupState.RequiresAuthentication)
    }

    @Test
    fun `refresh failure with completed local restaurant is ready offline`() = runTest {
        assertThat(fixture(AuthSessionState.RefreshFailed, localRestaurant(), true).resolve())
            .isEqualTo(SaaSStartupState.ReadyOffline)
    }

    @Test
    fun `refresh failure without local setup requires network`() = runTest {
        assertThat(fixture(AuthSessionState.RefreshFailed).resolve())
            .isEqualTo(SaaSStartupState.NetworkRequired)
    }

    @Test
    fun `signed in with no cloud restaurant and no local restaurant requires tenant setup`() = runTest {
        val fixture = fixture(signedIn())
        fixture.tenant.lookupResult = Result.success(emptyList())

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.RequiresTenantSetup(USER))
        assertThat(fixture.tenant.createCalls).isEqualTo(0)
    }

    @Test
    fun `one cloud restaurant and no local setup requires exact local setup access`() = runTest {
        val access = access("cloud-restaurant")
        val fixture = fixture(signedIn())
        fixture.tenant.lookupResult = Result.success(listOf(access))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.RequiresLocalSetup(USER, access))
        assertThat(fixture.device.requestedRestaurantIds).isEmpty()
    }

    @Test
    fun `matching completed restaurant and successful device ensure is ready online`() = runTest {
        val access = access(LOCAL_ID)
        val fixture = fixture(signedIn(), localRestaurant(), true)
        fixture.tenant.lookupResult = Result.success(listOf(access))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.ReadyOnline(USER, access))
        assertThat(fixture.device.requestedRestaurantIds).containsExactly(RestaurantId(LOCAL_ID))
    }

    @Test
    fun `matching completed restaurant and failed device ensure is ready offline`() = runTest {
        val fixture = fixture(signedIn(), localRestaurant(), true)
        fixture.tenant.lookupResult = Result.success(listOf(access(LOCAL_ID)))
        fixture.device.result = Result.failure(IllegalStateException("offline"))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.ReadyOffline)
    }

    @Test
    fun `matching completed restaurant and confirmed device revocation is device revoked`() = runTest {
        val fixture = fixture(signedIn(), localRestaurant(), true)
        fixture.tenant.lookupResult = Result.success(listOf(access(LOCAL_ID)))
        fixture.device.result = Result.failure(DeviceInstallationRevokedException())

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.DeviceRevoked)
    }

    @Test
    fun `cloud and completed local restaurant mismatch fails safely`() = runTest {
        val fixture = fixture(signedIn(), localRestaurant(), true)
        fixture.tenant.lookupResult = Result.success(listOf(access("different-restaurant")))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.TenantAccessMismatch)
        assertThat(fixture.device.requestedRestaurantIds).isEmpty()
    }

    @Test
    fun `tenant lookup failure with completed local restaurant is ready offline`() = runTest {
        val fixture = fixture(signedIn(), localRestaurant(), true)
        fixture.tenant.lookupResult = Result.failure(IllegalStateException("offline"))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.ReadyOffline)
    }

    @Test
    fun `tenant lookup failure without local restaurant requires network`() = runTest {
        val fixture = fixture(signedIn())
        fixture.tenant.lookupResult = Result.failure(IllegalStateException("offline"))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.NetworkRequired)
    }

    @Test
    fun `multiple cloud restaurants without completed local restaurant is unsupported`() = runTest {
        val fixture = fixture(signedIn())
        fixture.tenant.lookupResult = Result.success(listOf(access("one"), access("two")))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.MultipleRestaurantsUnsupported)
        assertThat(fixture.device.requestedRestaurantIds).isEmpty()
    }

    @Test
    fun `multiple cloud restaurants resolve exact completed local match`() = runTest {
        val matching = access(LOCAL_ID, MembershipRole.MANAGER)
        val fixture = fixture(signedIn(), localRestaurant(), true)
        fixture.tenant.lookupResult = Result.success(listOf(access("other"), matching))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.ReadyOnline(USER, matching))
        assertThat(fixture.device.requestedRestaurantIds).containsExactly(RestaurantId(LOCAL_ID))
    }

    @Test
    fun `multiple cloud restaurants without local match is access mismatch`() = runTest {
        val fixture = fixture(signedIn(), localRestaurant(), true)
        fixture.tenant.lookupResult = Result.success(listOf(access("one"), access("two")))

        assertThat(fixture.resolve()).isEqualTo(SaaSStartupState.TenantAccessMismatch)
        assertThat(fixture.device.requestedRestaurantIds).isEmpty()
    }

    @Test
    fun `auth change cancels stale tenant resolution`() = runTest {
        val fixture = fixture(signedIn())
        val lookupStarted = CompletableDeferred<Unit>()
        fixture.tenant.lookupBlock = {
            lookupStarted.complete(Unit)
            awaitCancellation()
        }
        val resolved = async { fixture.resolve() }
        lookupStarted.await()

        fixture.setAuth(AuthSessionState.SignedOut)

        assertThat(resolved.await()).isEqualTo(SaaSStartupState.RequiresAuthentication)
    }

    @Test
    fun `startup refresh reruns tenant lookup without auth or local changes`() = runTest {
        val fixture = fixture(signedIn())
        fixture.tenant.lookupResult = Result.success(emptyList())
        val states = async { fixture.states(2) }
        runCurrent()

        fixture.tenant.lookupResult = Result.success(listOf(access("cloud-restaurant")))
        fixture.requestRefresh()

        assertThat(states.await()).containsExactly(
            SaaSStartupState.RequiresTenantSetup(USER),
            SaaSStartupState.RequiresLocalSetup(USER, access("cloud-restaurant"))
        ).inOrder()
        assertThat(fixture.tenant.lookupCalls).isEqualTo(2)
    }

    @Test
    fun `startup refresh cancels stale tenant lookup`() = runTest {
        val fixture = fixture(signedIn())
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        fixture.tenant.lookupBlock = {
            if (fixture.tenant.lookupCalls == 1) {
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    firstCancelled.complete(Unit)
                }
            } else {
                Result.success(emptyList())
            }
        }
        val resolved = async { fixture.resolve() }
        firstStarted.await()

        fixture.requestRefresh()

        assertThat(resolved.await()).isEqualTo(SaaSStartupState.RequiresTenantSetup(USER))
        firstCancelled.await()
    }

    private fun fixture(
        auth: AuthSessionState,
        restaurant: Restaurant? = null,
        setupComplete: Boolean = false
    ) = Fixture(auth, restaurant, setupComplete)

    private fun signedIn() = AuthSessionState.SignedIn(USER)

    private fun localRestaurant() = Restaurant(
        id = RestaurantId(LOCAL_ID),
        name = "Local Restaurant",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun access(
        id: String,
        role: MembershipRole = MembershipRole.OWNER
    ) = RestaurantAccess(
        restaurant = RestaurantSummary(
            id = id,
            organizationId = "organization-$id",
            name = "Restaurant $id",
            currencyCode = "USD",
            timezone = "America/New_York",
            localeTag = "en-US"
        ),
        role = role
    )

    private class Fixture(
        auth: AuthSessionState,
        restaurant: Restaurant?,
        setupComplete: Boolean
    ) {
        val tenant = FakeTenantRepository()
        val device = FakeDeviceInstallationRepository()
        private val authRepository = FakeAuthRepository(auth)
        private val localSetup = FakeLocalSetupRepository(setupComplete)
        private val restaurants = FakeRestaurantRepository(restaurant)
        private val startupRefresh = SaaSStartupRefresh()
        private val useCase = ResolveSaaSStartupStateUseCase(
            authRepository = authRepository,
            tenantRepository = tenant,
            deviceInstallationRepository = device,
            localSetupRepository = localSetup,
            restaurantRepository = restaurants,
            startupRefresh = startupRefresh
        )

        suspend fun resolve(): SaaSStartupState = useCase().first()
        suspend fun states(count: Int): List<SaaSStartupState> = useCase().take(count).toList()
        fun requestRefresh() = startupRefresh.requestRefresh()

        fun setAuth(state: AuthSessionState) {
            authRepository.sessionState.value = state
        }
    }

    private class FakeAuthRepository(initial: AuthSessionState) : AuthRepository {
        override val sessionState = MutableStateFlow(initial)
        override suspend fun signUp(email: String, password: String) = Result.success(Unit)
        override suspend fun signIn(email: String, password: String) = Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
    }

    private class FakeTenantRepository : TenantRepository {
        var lookupResult: Result<List<RestaurantAccess>> = Result.success(emptyList())
        var lookupBlock: suspend () -> Result<List<RestaurantAccess>> = { lookupResult }
        var createCalls = 0
        var lookupCalls = 0

        override suspend fun getAccessibleRestaurants(): Result<List<RestaurantAccess>> {
            lookupCalls += 1
            return lookupBlock()
        }

        override suspend fun createOrganizationWithRestaurant(
            organizationName: String,
            restaurantName: String,
            currencyCode: String,
            timezone: String,
            localeTag: String
        ): Result<TenantBootstrapResult> {
            createCalls += 1
            return Result.failure(AssertionError("Tenant creation must not be invoked"))
        }
    }

    private class FakeDeviceInstallationRepository : DeviceInstallationRepository {
        val requestedRestaurantIds = mutableListOf<RestaurantId>()
        var result: Result<DeviceInstallation> = Result.success(
            DeviceInstallation("device", RestaurantId(LOCAL_ID), "installation")
        )

        override suspend fun ensureCurrentDeviceRegistered(
            restaurantId: RestaurantId
        ): Result<DeviceInstallation> {
            requestedRestaurantIds += restaurantId
            return result
        }
    }

    private class FakeLocalSetupRepository(initial: Boolean) : LocalSetupRepository {
        private val complete = MutableStateFlow(initial)
        override suspend fun isSetupComplete() = complete.value
        override fun observeIsSetupComplete(): Flow<Boolean> = complete
        override suspend fun completeSetup(command: CompleteLocalSetupCommand): LocalSetupResult =
            throw AssertionError("Local setup must not be invoked")
    }

    private class FakeRestaurantRepository(initial: Restaurant?) : RestaurantRepository {
        private val restaurant = MutableStateFlow(initial)
        override fun observeRestaurant(): Flow<Restaurant?> = restaurant
        override suspend fun getRestaurant(): Restaurant? = restaurant.value
        override suspend fun save(restaurant: Restaurant) {
            throw AssertionError("Restaurant mutation must not be invoked")
        }
    }

    private companion object {
        const val LOCAL_ID = "local-restaurant"
        val USER = AuthUser("user-1", "owner@example.com")
    }
}
