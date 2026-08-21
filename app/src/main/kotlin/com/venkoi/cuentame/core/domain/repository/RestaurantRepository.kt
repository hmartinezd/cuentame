package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.model.restaurant.Restaurant
import kotlinx.coroutines.flow.Flow

interface RestaurantRepository {
    fun observeRestaurant(): Flow<Restaurant?>
    suspend fun getRestaurant(): Restaurant?
    suspend fun save(restaurant: Restaurant)
}
