package com.miara.cuentame.core.domain.usecase.locale

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AppLocaleUseCaseTest {

    private val restaurantRepository = mockk<RestaurantRepository>()
    private val preferencesRepository = mockk<AppPreferencesRepository>()
    private val timeProvider = mockk<TimeProvider>()
    
    private lateinit var reconciler: DefaultAppLocaleReconciler

    @Before
    fun setup() {
        reconciler = DefaultAppLocaleReconciler(restaurantRepository, preferencesRepository)
    }

    @Test
    fun `reconciler rethrows cancellation exception`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws CancellationException("User cancelled")
        
        try {
            reconciler.reconcile()
            assertThat(false).isTrue() // Should not reach here
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("User cancelled")
        }
    }

    @Test
    fun `reconciler handles room failure as ordinary failure`() = runTest {
        coEvery { restaurantRepository.getRestaurant() } throws RuntimeException("DB Error")
        
        val result = reconciler.reconcile()
        assertThat(result).isInstanceOf(LocaleReconciliationResult.Failure::class.java)
    }
}
