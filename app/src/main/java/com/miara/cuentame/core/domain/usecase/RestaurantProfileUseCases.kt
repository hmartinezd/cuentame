package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.domain.validation.ValidationError
import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.flow.Flow
import java.util.Currency
import javax.inject.Inject

class ObserveRestaurantProfileUseCase @Inject constructor(
    private val repository: RestaurantRepository
) {
    operator fun invoke(): Flow<Restaurant?> = repository.observeRestaurant()
}

class UpdateRestaurantProfileUseCase @Inject constructor(
    private val repository: RestaurantRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(restaurant: Restaurant) {
        if (restaurant.name.trim().isEmpty()) {
            throw ValidationError.InvalidName
        }
        
        try {
            Currency.getInstance(restaurant.currencyCode)
        } catch (e: Exception) {
            throw ValidationError.InvalidCurrencyCode
        }

        if (restaurant.localeTag !in listOf("en-US", "es-US")) {
            throw ValidationError.UnsupportedLocale
        }

        val existing = repository.getRestaurant() ?: throw ValidationError.MovementNotFound
        
        val updated = restaurant.copy(
            id = existing.id, // Ensure ID is preserved
            createdAt = existing.createdAt, // Preserve createdAt
            updatedAt = timeProvider.now()
        )
        repository.save(updated)
    }
}
