package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.domain.repository.CompleteLocalSetupCommand
import com.venkoi.restaurantops.core.domain.repository.LocalSetupRepository
import com.venkoi.restaurantops.core.domain.repository.LocalSetupResult
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
import com.venkoi.restaurantops.core.domain.service.StarterCatalogSeeder
import javax.inject.Inject


class CompleteOnboardingUseCase @Inject constructor(
    private val setupRepository: LocalSetupRepository,
    private val restaurantRepository: RestaurantRepository,
    private val preferencesRepository: AppPreferencesRepository
) {
    @Deprecated("Starter catalogs are now an explicit inventory action")
    constructor(
        setupRepository: LocalSetupRepository,
        restaurantRepository: RestaurantRepository,
        preferencesRepository: AppPreferencesRepository,
        @Suppress("UNUSED_PARAMETER") starterCatalogSeeder: StarterCatalogSeeder
    ) : this(setupRepository, restaurantRepository, preferencesRepository)

    suspend operator fun invoke(command: CompleteLocalSetupCommand): LocalSetupResult {
        val result = setupRepository.completeSetup(command)
        
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
