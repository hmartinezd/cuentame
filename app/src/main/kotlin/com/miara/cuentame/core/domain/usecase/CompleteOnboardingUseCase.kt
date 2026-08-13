package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.domain.repository.LocalSetupRepository
import com.miara.cuentame.core.domain.repository.LocalSetupResult
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.service.StarterCatalogSeeder
import com.miara.cuentame.core.model.catalog.CubanFoodiesStarterCatalog
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import javax.inject.Inject


class CompleteOnboardingUseCase @Inject constructor(
    private val setupRepository: LocalSetupRepository,
    private val restaurantRepository: RestaurantRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val starterCatalogSeeder: StarterCatalogSeeder
) {
    suspend operator fun invoke(command: CompleteLocalSetupCommand): LocalSetupResult {
        val result = setupRepository.completeSetup(command)
        
        if (result is LocalSetupResult.Success) {
            val restaurant = restaurantRepository.getRestaurant()
            if (restaurant != null) {
                // Seed the Cuban Foodies starter catalog for new restaurants
                val seedResult = starterCatalogSeeder.seedNewRestaurant(
                    restaurantId = restaurant.id.value,
                    catalog = CubanFoodiesStarterCatalog.definition
                )
                if (seedResult is com.miara.cuentame.core.domain.service.StarterCatalogSeedResult.Failure) {
                    throw (seedResult.reason as com.miara.cuentame.core.domain.service.StarterCatalogSeedFailure.DatabaseError).cause
                }
            }
        }

        if (result is LocalSetupResult.Success || result is LocalSetupResult.AlreadyCompleted) {
            val restaurant = restaurantRepository.getRestaurant()
            val localeTag = if (result is LocalSetupResult.AlreadyCompleted && restaurant != null) {
                restaurant.localeTag
            } else {
                command.localeTag
            }
            
            preferencesRepository.setAppLocaleTag(localeTag)
            preferencesRepository.setOnboardingCompleted(true)
            preferencesRepository.clearOnboardingDraft()
        }
        return result
    }
}

