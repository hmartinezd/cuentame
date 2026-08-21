package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.database.dao.RestaurantDao
import com.venkoi.cuentame.core.database.mapper.toDomain
import com.venkoi.cuentame.core.database.mapper.toEntity
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.core.model.restaurant.Restaurant
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
