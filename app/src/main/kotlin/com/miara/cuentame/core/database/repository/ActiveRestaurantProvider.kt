package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.domain.validation.ValidationError
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

    suspend fun getRequiredActiveRestaurantId(): com.miara.cuentame.core.common.ids.RestaurantId {
        return com.miara.cuentame.core.common.ids.RestaurantId(getActiveRestaurant().id)
    }
}
