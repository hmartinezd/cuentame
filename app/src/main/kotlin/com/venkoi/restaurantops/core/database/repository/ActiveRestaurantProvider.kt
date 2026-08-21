package com.venkoi.restaurantops.core.database.repository

import com.venkoi.restaurantops.core.database.dao.RestaurantDao
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.domain.validation.ValidationError
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveRestaurantProvider @Inject constructor(
    private val restaurantDao: RestaurantDao
) {
    fun observeActiveRestaurant(): Flow<RestaurantEntity?> {
        return restaurantDao.observeRestaurant()
    }
    suspend fun getActiveRestaurant(): RestaurantEntity {
        return restaurantDao.getRestaurant() ?: throw ValidationError.RecordNotFound
    }

    suspend fun getRequiredActiveRestaurantId(): com.venkoi.restaurantops.core.common.ids.RestaurantId {
        return com.venkoi.restaurantops.core.common.ids.RestaurantId(getActiveRestaurant().id)
    }
}
