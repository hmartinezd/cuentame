package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.flow.Flow
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
        val updated = restaurant.copy(updatedAt = timeProvider.now())
        repository.save(updated)
    }
}
