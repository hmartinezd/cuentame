package com.miara.cuentame.core.domain.usecase.locale

import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppLocaleReconciler @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val preferencesRepository: AppPreferencesRepository
) : AppLocaleReconciler {

    override suspend fun reconcile(): LocaleReconciliationResult {
        return try {
            val restaurant = restaurantRepository.getRestaurant()
                ?: return LocaleReconciliationResult.RestaurantNotFound

            val prefs = preferencesRepository.observePreferences().first()
            if (prefs.appLocaleTag != restaurant.localeTag) {
                preferencesRepository.setAppLocaleTag(restaurant.localeTag)
                LocaleReconciliationResult.Reconciled(restaurant.localeTag)
            } else {
                LocaleReconciliationResult.InSync
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocaleReconciliationResult.Failure(e)
        }
    }
}
