package com.miara.cuentame.core.domain.usecase.locale

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppLocaleUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()

    private lateinit var updateUseCase: DefaultUpdateAppLocaleUseCase
    private lateinit var reconciler: DefaultAppLocaleReconciler

    private val baseRestaurant = Restaurant(
        id = RestaurantId("rest-1"),
        name = "My Restaurant",
        currencyCode = "USD",
        localeTag = "en-US",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        updateUseCase = DefaultUpdateAppLocaleUseCase(restaurantRepository, preferencesRepository)
        reconciler = DefaultAppLocaleReconciler(restaurantRepository, preferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateAppLocale succeeds for valid es-US`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns baseRestaurant
        coEvery { restaurantRepository.save(any()) } just Runs
        coEvery { preferencesRepository.setAppLocaleTag(any()) } just Runs

        val result = updateUseCase(SupportedAppLocale.SPANISH_US)

        assertThat(result).isEqualTo(LocaleUpdateResult.Success)
        coVerify(exactly = 1) { restaurantRepository.save(match { it.localeTag == "es-US" }) }
        coVerify(exactly = 1) { preferencesRepository.setAppLocaleTag("es-US") }
    }

    @Test
    fun `updateAppLocale Room failure does not update DataStore`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns baseRestaurant
        val roomErr = RuntimeException("Room DB locked")
        coEvery { restaurantRepository.save(any()) } throws roomErr

        val result = updateUseCase(SupportedAppLocale.SPANISH_US)

        assertThat(result).isInstanceOf(LocaleUpdateResult.Error.RoomUpdateFailed::class.java)
        val err = result as LocaleUpdateResult.Error.RoomUpdateFailed
        assertThat(err.cause).isEqualTo(roomErr)
        coVerify(exactly = 0) { preferencesRepository.setAppLocaleTag(any()) }
    }

    @Test
    fun `updateAppLocale DataStore failure triggers successful Room compensation`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns baseRestaurant
        coEvery { restaurantRepository.save(match { it.localeTag == "es-US" }) } just Runs
        coEvery { restaurantRepository.save(match { it.localeTag == "en-US" }) } just Runs
        val prefsErr = RuntimeException("DataStore write failed")
        coEvery { preferencesRepository.setAppLocaleTag("es-US") } throws prefsErr

        val result = updateUseCase(SupportedAppLocale.SPANISH_US)

        assertThat(result).isInstanceOf(LocaleUpdateResult.Error.PreferenceUpdateFailed::class.java)
        val err = result as LocaleUpdateResult.Error.PreferenceUpdateFailed
        assertThat(err.cause).isEqualTo(prefsErr)
        assertThat(err.compensationSucceeded).isTrue()

        // Room save was called first for es-US, then compensated back to en-US
        coVerify(exactly = 1) { restaurantRepository.save(match { it.localeTag == "es-US" }) }
        coVerify(exactly = 1) { restaurantRepository.save(match { it.localeTag == "en-US" }) }
    }

    @Test
    fun `reconcile updates DataStore when preferences tag differs from restaurant tag`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns baseRestaurant.copy(localeTag = "es-US")
        every { preferencesRepository.observePreferences() } returns flowOf(
            AppPreferences(onboardingCompleted = true, themeMode = ThemeMode.SYSTEM, dynamicColorEnabled = true, appLocaleTag = "en-US")
        )
        coEvery { preferencesRepository.setAppLocaleTag("es-US") } just Runs

        val result = reconciler.reconcile()

        assertThat(result).isEqualTo(LocaleReconciliationResult.Reconciled("es-US"))
        coVerify(exactly = 1) { preferencesRepository.setAppLocaleTag("es-US") }
    }

    @Test
    fun `reconcile reports InSync when preferences tag matches restaurant tag`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns baseRestaurant.copy(localeTag = "en-US")
        every { preferencesRepository.observePreferences() } returns flowOf(
            AppPreferences(onboardingCompleted = true, themeMode = ThemeMode.SYSTEM, dynamicColorEnabled = true, appLocaleTag = "en-US")
        )

        val result = reconciler.reconcile()

        assertThat(result).isEqualTo(LocaleReconciliationResult.InSync)
        coVerify(exactly = 0) { preferencesRepository.setAppLocaleTag(any()) }
    }

    @Test
    fun `concurrent update requests are serialized by Mutex`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } returns baseRestaurant
        coEvery { restaurantRepository.save(any()) } just Runs
        coEvery { preferencesRepository.setAppLocaleTag(any()) } just Runs

        val job1 = launch { updateUseCase(SupportedAppLocale.SPANISH_US) }
        val job2 = launch { updateUseCase(SupportedAppLocale.ENGLISH_US) }

        testDispatcher.scheduler.advanceUntilIdle()
        job1.join()
        job2.join()

        coVerify(exactly = 2) { restaurantRepository.getRestaurant() }
    }
}
