package com.venkoi.restaurantops.core.domain.usecase.locale

import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.locale.SupportedAppLocale
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
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
        } catch (e: Exception) {
            return LocaleUpdateResult.Error.RoomUpdateFailed(e)
        }

        // 2. Update DataStore preferences
        try {
            preferencesRepository.setAppLocaleTag(targetLocaleTag)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                try {
                    restaurantRepository.save(restaurant.copy(localeTag = previousLocaleTag))
                } catch (_: Exception) {}
            }
            throw e
        } catch (e: Exception) {
            // Attempt compensation: restore previous Room locale
            var compensationSucceeded = false
            try {
                withContext(NonCancellable) {
                    restaurantRepository.save(restaurant.copy(localeTag = previousLocaleTag))
                    compensationSucceeded = true
                }
            } catch (_: Exception) {
                compensationSucceeded = false
            }
            return LocaleUpdateResult.Error.PreferenceUpdateFailed(e, compensationSucceeded)
        }

        return LocaleUpdateResult.Success
    }
}
