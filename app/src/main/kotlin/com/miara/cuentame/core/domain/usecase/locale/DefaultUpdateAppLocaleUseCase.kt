package com.miara.cuentame.core.domain.usecase.locale

import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.locale.SupportedAppLocale
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultUpdateAppLocaleUseCase @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val preferencesRepository: AppPreferencesRepository
) : UpdateAppLocaleUseCase {

    private val mutex = Mutex()

    override suspend fun invoke(locale: SupportedAppLocale): LocaleUpdateResult = mutex.withLock {
        val restaurant = restaurantRepository.getRestaurant()
            ?: return LocaleUpdateResult.Error.RestaurantNotFound

        val previousLocaleTag = restaurant.localeTag
        val targetLocaleTag = locale.languageTag

        // 1. Update authoritative Room restaurant record
        try {
            restaurantRepository.save(restaurant.copy(localeTag = targetLocaleTag))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return LocaleUpdateResult.Error.RoomUpdateFailed(e)
        }

        // 2. Update DataStore preferences
        try {
            preferencesRepository.setAppLocaleTag(targetLocaleTag)
        } catch (e: CancellationException) {
            // CRITICAL: Must re-evaluate if we need compensation on cancellation.
            // If the coroutine is cancelled here, Room was updated but DataStore was not.
            // We use NonCancellable to ensure Room is reverted to match DataStore state.
            withContext(NonCancellable) {
                try {
                    restaurantRepository.save(restaurant.copy(localeTag = previousLocaleTag))
                } catch (_: Throwable) {
                    // Log failure to revert if needed
                }
            }
            throw e
        } catch (e: Throwable) {
            // Attempt compensation: restore previous Room locale
            var compensationSucceeded = false
            try {
                // Ensure compensation is not interrupted if the parent scope is cancelled
                // during this catch block (unlikely but safe).
                withContext(NonCancellable) {
                    restaurantRepository.save(restaurant.copy(localeTag = previousLocaleTag))
                    compensationSucceeded = true
                }
            } catch (_: Throwable) {
                compensationSucceeded = false
            }
            return LocaleUpdateResult.Error.PreferenceUpdateFailed(e, compensationSucceeded)
        }

        return LocaleUpdateResult.Success
    }
}
