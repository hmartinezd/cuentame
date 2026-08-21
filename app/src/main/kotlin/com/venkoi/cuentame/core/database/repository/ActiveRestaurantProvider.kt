package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.database.dao.RestaurantDao
import com.venkoi.cuentame.core.database.entity.RestaurantEntity
import com.venkoi.cuentame.core.domain.validation.ValidationError
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

    suspend fun getRequiredActiveRestaurantId(): com.venkoi.cuentame.core.common.ids.RestaurantId {
        return com.venkoi.cuentame.core.common.ids.RestaurantId(getActiveRestaurant().id)
    }
}
