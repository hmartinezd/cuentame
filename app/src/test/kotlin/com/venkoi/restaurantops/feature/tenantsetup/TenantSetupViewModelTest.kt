package com.venkoi.restaurantops.feature.tenantsetup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess
import com.venkoi.restaurantops.core.domain.model.tenant.TenantBootstrapResult
import com.venkoi.restaurantops.core.domain.repository.TenantRepository
import com.venkoi.restaurantops.core.domain.startup.SaaSStartupRefresh
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TenantSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeTenantRepository
    private lateinit var refresh: SaaSStartupRefresh
    private lateinit var viewModel: TenantSetupViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeTenantRepository()
        refresh = SaaSStartupRefresh()
        viewModel = TenantSetupViewModel(repository, refresh, FakeDefaults())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `device defaults initialize timezone locale and USD`() {
        assertThat(viewModel.state.value.currencyCode).isEqualTo("USD")
        assertThat(viewModel.state.value.timezone).isEqualTo("Europe/Madrid")
        assertThat(viewModel.state.value.localeTag).isEqualTo("es-ES")
    }

    @Test fun `blank organization is rejected locally`() = runTest {
        populate(organization = "   ")
        viewModel.submit()
        runCurrent()
        assertThat(viewModel.state.value.validationErrors).contains(TenantSetupField.ORGANIZATION)
        assertThat(repository.calls).isEmpty()
    }

    @Test fun `blank restaurant is rejected locally`() = runTest {
        populate(restaurant = "")
        viewModel.submit()
        runCurrent()
        assertThat(viewModel.state.value.validationErrors).contains(TenantSetupField.RESTAURANT)
        assertThat(repository.calls).isEmpty()
    }

    @Test fun `valid submit normalizes values calls RPC once and refreshes once`() = runTest {
        populate(currency = " usd ")
        viewModel.submit()
        runCurrent()

        assertThat(repository.calls).containsExactly(
            CreateCall("My Company", "My Restaurant", "USD", "America/New_York", "en-US")
        )
        assertThat(refresh.revision.value).isEqualTo(1L)
        assertThat(TenantSetupUiState::class.java.declaredFields.map { it.name }).doesNotContain("navigation")
    }

    @Test fun `double submit while request is active calls RPC once`() = runTest {
        val release = CompletableDeferred<Result<TenantBootstrapResult>>()
        repository.block = { release.await() }
        populate()

        viewModel.submit()
        runCurrent()
        viewModel.submit()
        runCurrent()

        assertThat(repository.calls).hasSize(1)
        release.complete(Result.success(TenantBootstrapResult("org", "restaurant")))
        runCurrent()
        assertThat(refresh.revision.value).isEqualTo(1L)
    }

    @Test fun `failure clears submitting shows safe error and does not refresh`() = runTest {
        repository.block = { Result.failure(IllegalStateException("database detail")) }
        populate()

        viewModel.submit()
        runCurrent()

        assertThat(viewModel.state.value.submitting).isFalse()
        assertThat(viewModel.state.value.operationFailed).isTrue()
        assertThat(refresh.revision.value).isEqualTo(0L)
    }

    private fun populate(
        organization: String = " My Company ",
        restaurant: String = " My Restaurant ",
        currency: String = "USD"
    ) {
        viewModel.updateOrganizationName(organization)
        viewModel.updateRestaurantName(restaurant)
        viewModel.updateCurrencyCode(currency)
        viewModel.updateTimezone("America/New_York")
        viewModel.updateLocaleTag("en-US")
    }

    private class FakeDefaults : TenantSetupDefaultsProvider {
        override fun timezone() = "Europe/Madrid"
        override fun localeTag() = "es-ES"
    }

    private data class CreateCall(
        val organizationName: String,
        val restaurantName: String,
        val currencyCode: String,
        val timezone: String,
        val localeTag: String
    )

    private class FakeTenantRepository : TenantRepository {
        val calls = mutableListOf<CreateCall>()
        var block: suspend () -> Result<TenantBootstrapResult> = {
            Result.success(TenantBootstrapResult("organization", "restaurant"))
        }

        override suspend fun getAccessibleRestaurants(): Result<List<RestaurantAccess>> =
            error("Not used")

        override suspend fun createOrganizationWithRestaurant(
            organizationName: String,
            restaurantName: String,
            currencyCode: String,
            timezone: String,
            localeTag: String
        ): Result<TenantBootstrapResult> {
            calls += CreateCall(organizationName, restaurantName, currencyCode, timezone, localeTag)
            return block()
        }
    }
}
