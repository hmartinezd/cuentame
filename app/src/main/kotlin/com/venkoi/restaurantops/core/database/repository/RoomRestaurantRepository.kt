package com.venkoi.restaurantops.core.database.repository

import com.venkoi.restaurantops.core.database.dao.RestaurantDao
import com.venkoi.restaurantops.core.database.mapper.toDomain
import com.venkoi.restaurantops.core.database.mapper.toEntity
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomRestaurantRepository @Inject constructor(
    private val restaurantDao: RestaurantDao
) : RestaurantRepository {
    override fun observeRestaurant(): Flow<Restaurant?> {
        return restaurantDao.observeRestaurant().map { it?.toDomain() }
    }

    override suspend fun getRestaurant(): Restaurant? {
        return restaurantDao.getRestaurant()?.toDomain()
    }

    override suspend fun save(restaurant: Restaurant) {
        val existing = restaurantDao.getRestaurant()
        if (existing == null) {
            restaurantDao.insert(restaurant.toEntity())
        } else {
            restaurantDao.update(restaurant.toEntity())
        }
    }
}
