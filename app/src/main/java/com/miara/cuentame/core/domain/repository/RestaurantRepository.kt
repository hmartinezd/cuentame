package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.flow.Flow

interface RestaurantRepository {
    fun observeRestaurant(): Flow<Restaurant?>
    suspend fun getRestaurant(): Restaurant?
    suspend fun save(restaurant: Restaurant)
}
